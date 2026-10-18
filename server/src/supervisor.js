import { randomBytes, createHash } from 'node:crypto';

/** Types d'alerte visibles dans la console. */
export const AlertType = Object.freeze({
  ALARM: 'alarm',
  LOST_CONTACT: 'lost_contact',
  LOW_BATTERY: 'low_battery',
  SHUTDOWN: 'shutdown',
});

const URGENT = new Set([AlertType.ALARM, AlertType.LOST_CONTACT]);

const REASON_LABELS = {
  FALL: 'Chute détectée',
  IMMOBILITY: 'Absence de mouvement',
  MISSED_CHECK_IN: 'Confirmation de présence manquée',
  SOS: 'Appel SOS',
  TEST: 'Essai',
};

export function hashToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

function newId(bytes = 6) {
  return randomBytes(bytes).toString('hex');
}

function mapsLink(location) {
  return location ? `https://maps.google.com/?q=${location.lat},${location.lon}` : null;
}

/**
 * Logique de supervision, indépendante du HTTP et de l'horloge (injectée) pour être testable.
 *
 * Confidentialité : la dernière position d'un appareil est conservée pour pouvoir la joindre à
 * une alerte, mais elle n'est exposée à la console qu'au travers des alertes.
 */
export class Supervisor {
  constructor({
    store,
    notifier,
    now = () => Date.now(),
    heartbeatTimeoutMs = 15 * 60_000,
    escalateAfterMs = 5 * 60_000,
    retentionMs = 30 * 24 * 3600_000,
  }) {
    this.store = store;
    this.notifier = notifier;
    this.now = now;
    this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    this.escalateAfterMs = escalateAfterMs;
    this.retentionMs = retentionMs;
  }

  get data() {
    return this.store.data;
  }

  // --- Administration -------------------------------------------------------

  createDevice(name) {
    const trimmed = String(name ?? '').trim();
    if (!trimmed) throw new Error('Le nom est obligatoire');
    const token = randomBytes(24).toString('hex');
    const device = {
      id: newId(),
      name: trimmed,
      tokenHash: hashToken(token),
      createdAt: this.now(),
      lastSeen: null,
      monitoring: false,
      state: 'idle',
      reason: null,
      battery: null,
      charging: null,
      lastLocation: null,
    };
    this.data.devices.push(device);
    this.store.save();
    return { device, token };
  }

  deleteDevice(id) {
    const before = this.data.devices.length;
    this.data.devices = this.data.devices.filter((d) => d.id !== id);
    this.store.save();
    return this.data.devices.length !== before;
  }

  findDeviceByToken(token) {
    if (!token) return null;
    const hash = hashToken(token);
    return this.data.devices.find((d) => d.tokenHash === hash) ?? null;
  }

  acknowledge(alertId, by) {
    const alert = this.data.alerts.find((a) => a.id === alertId);
    if (!alert || alert.resolvedAt) return null;
    alert.ackedBy = String(by ?? '').trim() || 'Superviseur';
    alert.ackedAt = this.now();
    this.store.save();
    const device = this.device(alert.deviceId);
    this.notifier.send({
      title: `Pris en charge : ${device?.name ?? 'appareil supprimé'}`,
      message: `${alert.ackedBy} prend en charge l'alerte « ${this.describe(alert)} ».`,
      urgent: false,
    });
    return alert;
  }

  resolve(alertId, note = 'Clôturée depuis la console') {
    const alert = this.data.alerts.find((a) => a.id === alertId);
    if (!alert || alert.resolvedAt) return null;
    alert.resolvedAt = this.now();
    alert.resolution = note;
    this.store.save();
    return alert;
  }

  /** Vue pour la console : appareils sans secret ni position, alertes avec position. */
  status() {
    const devices = this.data.devices.map(({ tokenHash, lastLocation, ...rest }) => ({
      ...rest,
      online: rest.monitoring && rest.lastSeen !== null && this.now() - rest.lastSeen <= this.heartbeatTimeoutMs,
    }));
    const alerts = [...this.data.alerts]
      .sort((a, b) => b.createdAt - a.createdAt)
      .map((a) => ({
        ...a,
        deviceName: this.device(a.deviceId)?.name ?? 'Appareil supprimé',
        label: this.describe(a),
        mapsUrl: mapsLink(a.location),
      }));
    return { devices, alerts, serverTime: this.now() };
  }

  // --- Appareils ------------------------------------------------------------

  /** Battement de cœur périodique envoyé par l'application. */
  heartbeat(device, payload = {}) {
    this.applyPayload(device, payload);
    const lost = this.openAlert(device.id, AlertType.LOST_CONTACT);
    if (lost) {
      lost.resolvedAt = this.now();
      lost.resolution = 'Contact rétabli';
      this.notifier.send({
        title: `Contact rétabli : ${device.name}`,
        message: `${device.name} est de nouveau joignable.`,
        urgent: false,
      });
    }
    this.store.save();
    return this.reply(device);
  }

  /** Événement ponctuel : start, stop, prealarm, alarm, ok, low_battery, shutdown. */
  event(device, payload = {}) {
    this.applyPayload(device, payload);
    const location = payload.location ?? device.lastLocation;
    switch (payload.type) {
      case 'start':
        device.monitoring = true;
        break;
      case 'stop':
        device.monitoring = false;
        this.resolveDeviceAlerts(device.id, [AlertType.LOST_CONTACT], 'Surveillance arrêtée');
        break;
      case 'alarm': {
        const test = payload.reason === 'TEST';
        const alert = this.createAlert(device, AlertType.ALARM, { reason: payload.reason ?? null, location, test });
        this.notifier.send({
          title: `${test ? '[ESSAI] ' : ''}ALARME DATI : ${device.name}`,
          message: [this.describe(alert), mapsLink(location)].filter(Boolean).join('\n'),
          urgent: !test,
          click: mapsLink(location),
        });
        break;
      }
      case 'ok': {
        const resolved = this.resolveDeviceAlerts(device.id, [AlertType.ALARM], 'Annulée par le salarié');
        if (resolved.length > 0) {
          this.notifier.send({
            title: `Fin d'alarme : ${device.name}`,
            message: `${device.name} a arrêté l'alarme et indique aller bien.`,
            urgent: false,
          });
        }
        break;
      }
      case 'low_battery':
      case 'shutdown': {
        const type = payload.type === 'shutdown' ? AlertType.SHUTDOWN : AlertType.LOW_BATTERY;
        const alert = this.createAlert(device, type, { location });
        this.notifier.send({ title: `${this.describe(alert)} : ${device.name}`, message: this.describe(alert), urgent: false });
        break;
      }
      case 'prealarm':
        break;
      default:
        throw new Error(`Type d'événement inconnu : ${payload.type}`);
    }
    this.store.save();
    return this.reply(device);
  }

  // --- Tâche périodique -----------------------------------------------------

  /** Détecte les pertes de contact, escalade les alertes non prises en charge, purge l'historique. */
  tick() {
    const now = this.now();
    for (const device of this.data.devices) {
      const silent = device.monitoring && device.lastSeen !== null && now - device.lastSeen > this.heartbeatTimeoutMs;
      if (silent && !this.openAlert(device.id, AlertType.LOST_CONTACT)) {
        const alert = this.createAlert(device, AlertType.LOST_CONTACT, { location: device.lastLocation });
        this.notifier.send({
          title: `PERTE DE CONTACT : ${device.name}`,
          message: [
            `Aucune nouvelle de ${device.name} depuis ${Math.round((now - device.lastSeen) / 60_000)} min.`,
            mapsLink(alert.location) && `Dernière position : ${mapsLink(alert.location)}`,
          ].filter(Boolean).join('\n'),
          urgent: true,
          click: mapsLink(alert.location),
        });
      }
    }

    for (const alert of this.data.alerts) {
      const pending = URGENT.has(alert.type) && !alert.test && !alert.ackedAt && !alert.resolvedAt && !alert.escalatedAt;
      if (pending && now - alert.createdAt >= this.escalateAfterMs) {
        alert.escalatedAt = now;
        const device = this.device(alert.deviceId);
        this.notifier.send({
          title: `ESCALADE – alerte non prise en charge : ${device?.name ?? '?'}`,
          message: [
            `${this.describe(alert)} depuis ${Math.round((now - alert.createdAt) / 60_000)} min, personne n'a pris en charge.`,
            mapsLink(alert.location),
          ].filter(Boolean).join('\n'),
          urgent: true,
          escalation: true,
          click: mapsLink(alert.location),
        });
      }
    }

    const keepAfter = now - this.retentionMs;
    this.data.alerts = this.data.alerts.filter((a) => !a.resolvedAt || a.resolvedAt >= keepAfter);
    this.store.save();
  }

  // --- Interne --------------------------------------------------------------

  device(id) {
    return this.data.devices.find((d) => d.id === id);
  }

  describe(alert) {
    switch (alert.type) {
      case AlertType.ALARM:
        return `${alert.test ? 'Essai – ' : ''}${REASON_LABELS[alert.reason] ?? 'Alarme'}`;
      case AlertType.LOST_CONTACT:
        return 'Perte de contact avec le téléphone';
      case AlertType.LOW_BATTERY:
        return 'Batterie faible';
      case AlertType.SHUTDOWN:
        return 'Téléphone en cours d\'extinction';
      default:
        return alert.type;
    }
  }

  applyPayload(device, payload) {
    device.lastSeen = this.now();
    if (typeof payload.state === 'string') device.state = payload.state;
    if (payload.state && payload.state !== 'idle') device.monitoring = true;
    if ('reason' in payload) device.reason = payload.reason ?? null;
    if (Number.isFinite(payload.battery)) device.battery = payload.battery;
    if (typeof payload.charging === 'boolean') device.charging = payload.charging;
    const loc = payload.location;
    if (loc && Number.isFinite(loc.lat) && Number.isFinite(loc.lon)) {
      device.lastLocation = { lat: loc.lat, lon: loc.lon, accuracy: loc.accuracy ?? null, time: loc.time ?? this.now() };
    }
  }

  createAlert(device, type, { reason = null, location = null, test = false } = {}) {
    const alert = {
      id: newId(),
      deviceId: device.id,
      type,
      reason,
      test,
      location: location ? { ...location } : null,
      createdAt: this.now(),
      ackedBy: null,
      ackedAt: null,
      escalatedAt: null,
      resolvedAt: null,
      resolution: null,
    };
    this.data.alerts.push(alert);
    return alert;
  }

  openAlert(deviceId, type) {
    return this.data.alerts.find((a) => a.deviceId === deviceId && a.type === type && !a.resolvedAt) ?? null;
  }

  resolveDeviceAlerts(deviceId, types, note) {
    const resolved = this.data.alerts.filter((a) => a.deviceId === deviceId && types.includes(a.type) && !a.resolvedAt);
    for (const alert of resolved) {
      alert.resolvedAt = this.now();
      alert.resolution = note;
    }
    return resolved;
  }

  /** Réponse renvoyée au téléphone : indique si son alarme a été prise en charge. */
  reply(device) {
    const alarm = this.openAlert(device.id, AlertType.ALARM);
    return {
      serverTime: this.now(),
      ack: alarm?.ackedAt ? { by: alarm.ackedBy, at: alarm.ackedAt } : null,
    };
  }
}
