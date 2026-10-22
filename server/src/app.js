import { readFileSync } from 'node:fs';
import { timingSafeEqual, createHash } from 'node:crypto';

const MAX_BODY_BYTES = 64 * 1024;

/** Paramètres de l'application qu'un lien de configuration peut transmettre (voir DeploymentConfig côté Android). */
const CONFIG_KEYS = [
  'contacts', 'checkin', 'prealarm', 'immobility', 'verticality', 'battery', 'fall', 'sensitivity', 'call', 'powersos',
];

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function sameSecret(a, b) {
  const ha = createHash('sha256').update(a).digest();
  const hb = createHash('sha256').update(b).digest();
  return timingSafeEqual(ha, hb);
}

async function readJson(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new HttpError(413, 'Requête trop volumineuse');
    chunks.push(chunk);
  }
  if (chunks.length === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'JSON invalide');
  }
}

function send(res, status, body, headers = {}) {
  const isJson = typeof body !== 'string';
  res.writeHead(status, {
    'Content-Type': isJson ? 'application/json; charset=utf-8' : 'text/html; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    ...headers,
  });
  res.end(isJson ? JSON.stringify(body) : body);
}

function configParams({ publicUrl, token, name, settings = {} }) {
  const params = new URLSearchParams({ server: publicUrl, token, name });
  for (const key of CONFIG_KEYS) {
    const value = settings[key];
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  }
  return params;
}

/** Lien dati://config, ouvert directement par l'application. */
export function buildConfigUrl(options) {
  return `dati://config?${configParams(options)}`;
}

/**
 * Lien https encodé dans le QR code : tous les appareils photo savent l'ouvrir. La page renvoie
 * vers l'application ; les paramètres sont dans le fragment (#), jamais transmis au serveur.
 */
export function buildEnrollUrl(options) {
  return `${options.publicUrl}/c#${configParams(options)}`;
}

/** Bibliothèques servies telles quelles (liste fermée : aucun chemin fourni par le client n'est lu). */
const VENDOR_FILES = {
  'qrcode.js': 'text/javascript',
  'leaflet/leaflet.js': 'text/javascript',
  'leaflet/leaflet.css': 'text/css',
};

const DEFAULT_MAP = {
  tileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution: '&copy; contributeurs OpenStreetMap',
};

export function createHandler({ supervisor, adminPassword, publicUrl, consoleHtml, map = DEFAULT_MAP }) {
  if (!adminPassword) throw new Error('ADMIN_PASSWORD est obligatoire');
  const html = consoleHtml ?? readFileSync(new URL('../public/index.html', import.meta.url), 'utf8');
  const enrollHtml = readFileSync(new URL('../public/enroll.html', import.meta.url), 'utf8');
  const vendor = Object.fromEntries(Object.entries(VENDOR_FILES).map(([file, type]) => [
    `/vendor/${file}`,
    { type, body: readFileSync(new URL(`../public/vendor/${file}`, import.meta.url), 'utf8') },
  ]));

  function requireAdmin(req) {
    const header = req.headers.authorization ?? '';
    const [scheme, encoded] = header.split(' ');
    const password = scheme === 'Basic' && encoded
      ? Buffer.from(encoded, 'base64').toString('utf8').split(':').slice(1).join(':')
      : '';
    if (!password || !sameSecret(password, adminPassword)) {
      throw new HttpError(401, 'Authentification requise');
    }
  }

  function requireDevice(req) {
    const header = req.headers.authorization ?? '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    const device = supervisor.findDeviceByToken(token);
    if (!device) throw new HttpError(401, 'Jeton d\'appareil invalide');
    return device;
  }

  async function route(req, res) {
    const url = new URL(req.url, 'http://localhost');
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const method = req.method;

    if (method === 'GET' && path === '/healthz') return send(res, 200, { ok: true });
    if (method === 'GET' && path === '/c') return send(res, 200, enrollHtml);
    if (method === 'GET' && vendor[path]) {
      const { type, body } = vendor[path];
      return send(res, 200, body, { 'Content-Type': `${type}; charset=utf-8`, 'Cache-Control': 'max-age=86400' });
    }

    // API des téléphones
    if (method === 'POST' && path === '/api/v1/heartbeat') {
      const device = requireDevice(req);
      return send(res, 200, supervisor.heartbeat(device, await readJson(req)));
    }
    if (method === 'POST' && path === '/api/v1/events') {
      const device = requireDevice(req);
      const body = await readJson(req);
      try {
        return send(res, 200, supervisor.event(device, body));
      } catch (e) {
        throw new HttpError(400, e.message);
      }
    }

    // Console de supervision
    if (method === 'GET' && path === '/') {
      requireAdmin(req);
      return send(res, 200, html);
    }
    if (path.startsWith('/api/v1/admin/')) {
      requireAdmin(req);
      const rest = path.slice('/api/v1/admin/'.length).split('/');
      if (method === 'GET' && rest[0] === 'status' && rest.length === 1) {
        return send(res, 200, { ...supervisor.status(), map });
      }
      if (method === 'POST' && rest[0] === 'zones' && rest.length === 1) {
        try {
          return send(res, 201, supervisor.createZone(await readJson(req)));
        } catch (e) {
          if (e instanceof HttpError) throw e;
          throw new HttpError(400, e.message);
        }
      }
      if (method === 'DELETE' && rest[0] === 'zones' && rest.length === 2) {
        return supervisor.deleteZone(rest[1]) ? send(res, 204, '') : send(res, 404, { error: 'Zone introuvable' });
      }
      if (method === 'POST' && rest[0] === 'devices' && rest.length === 1) {
        const body = await readJson(req);
        let created;
        try {
          created = supervisor.createDevice(body.name);
        } catch (e) {
          throw new HttpError(400, e.message);
        }
        const { tokenHash, lastLocation, ...device } = created.device;
        const options = { publicUrl, token: created.token, name: device.name, settings: body.settings };
        return send(res, 201, { device, configUrl: buildConfigUrl(options), enrollUrl: buildEnrollUrl(options) });
      }
      if (method === 'DELETE' && rest[0] === 'devices' && rest.length === 2) {
        return supervisor.deleteDevice(rest[1]) ? send(res, 204, '') : send(res, 404, { error: 'Appareil introuvable' });
      }
      if (method === 'POST' && rest[0] === 'alerts' && rest.length === 3 && ['ack', 'resolve'].includes(rest[2])) {
        const body = await readJson(req);
        const alert = rest[2] === 'ack' ? supervisor.acknowledge(rest[1], body.by) : supervisor.resolve(rest[1]);
        return alert ? send(res, 200, alert) : send(res, 404, { error: 'Alerte introuvable ou déjà clôturée' });
      }
    }

    throw new HttpError(404, 'Introuvable');
  }

  return async (req, res) => {
    try {
      await route(req, res);
    } catch (e) {
      const status = e instanceof HttpError ? e.status : 500;
      if (status === 500) console.error(e);
      const headers = status === 401 && !req.url.startsWith('/api/v1/heartbeat') && !req.url.startsWith('/api/v1/events')
        ? { 'WWW-Authenticate': 'Basic realm="Supervision DATI", charset="UTF-8"' }
        : {};
      send(res, status, { error: status === 500 ? 'Erreur interne' : e.message }, headers);
    }
  };
}
