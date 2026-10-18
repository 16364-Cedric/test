import { createServer } from 'node:http';
import { createHandler } from './app.js';
import { Supervisor } from './supervisor.js';
import { FileStore } from './store.js';
import { Notifier } from './notifier.js';

const env = process.env;
const minutes = (value, fallback) => (Number(value) > 0 ? Number(value) : fallback) * 60_000;

const retentionDays = Number(env.RETENTION_DAYS) > 0 ? Number(env.RETENTION_DAYS) : 30;
const port = Number(env.PORT) || 8080;
const publicUrl = (env.PUBLIC_URL || `http://localhost:${port}`).replace(/\/+$/, '');

const supervisor = new Supervisor({
  store: new FileStore(env.DATA_FILE || './data/dati.json'),
  notifier: new Notifier({
    ntfyUrl: env.NTFY_URL,
    escalationNtfyUrl: env.ESCALATION_NTFY_URL,
    webhookUrl: env.WEBHOOK_URL,
  }),
  heartbeatTimeoutMs: minutes(env.HEARTBEAT_TIMEOUT_MIN, 15),
  escalateAfterMs: minutes(env.ESCALATE_AFTER_MIN, 5),
  retentionMs: retentionDays * 24 * 3600_000,
});

const server = createServer(createHandler({ supervisor, adminPassword: env.ADMIN_PASSWORD, publicUrl }));
setInterval(() => supervisor.tick(), 30_000).unref();

server.listen(port, () => {
  console.log(`Supervision DATI sur le port ${port} (adresse publique : ${publicUrl})`);
  if (!env.NTFY_URL && !env.WEBHOOK_URL) {
    console.warn('Aucun canal de notification (NTFY_URL / WEBHOOK_URL) : les alertes ne seront visibles que dans la console.');
  }
});
