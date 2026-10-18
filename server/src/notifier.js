/**
 * Envoie les alertes aux superviseurs :
 * - ntfy (https://ntfy.sh ou instance auto-hébergée) : notification push sur téléphone ;
 * - webhook générique recevant {"text": "..."} (Teams, Slack, Mattermost, Google Chat…).
 * Les escalades partent vers un canal ntfy distinct s'il est configuré.
 */
export class Notifier {
  constructor({ ntfyUrl, escalationNtfyUrl, webhookUrl, fetchImpl = globalThis.fetch, log = console } = {}) {
    this.ntfyUrl = ntfyUrl;
    this.escalationNtfyUrl = escalationNtfyUrl;
    this.webhookUrl = webhookUrl;
    this.fetch = fetchImpl;
    this.log = log;
  }

  send({ title, message, urgent = false, escalation = false, click = null }) {
    this.log.info?.(`[alerte] ${title} – ${message.replace(/\n/g, ' | ')}`);
    const targets = [];
    const ntfy = escalation && this.escalationNtfyUrl ? this.escalationNtfyUrl : this.ntfyUrl;
    if (ntfy) {
      const headers = {
        // Les en-têtes HTTP doivent rester en ASCII : ntfy accepte le titre encodé en RFC 2047.
        Title: `=?UTF-8?B?${Buffer.from(title).toString('base64')}?=`,
        Priority: urgent ? '5' : '3',
        Tags: urgent ? 'rotating_light' : 'information_source',
      };
      if (click) headers.Click = click;
      targets.push(this.post(ntfy, message, headers));
    }
    if (this.webhookUrl) {
      targets.push(this.post(this.webhookUrl, JSON.stringify({ text: `**${title}**\n${message}` }), {
        'Content-Type': 'application/json',
      }));
    }
    return Promise.all(targets);
  }

  async post(url, body, headers) {
    try {
      const res = await this.fetch(url, { method: 'POST', body, headers });
      if (!res.ok) this.log.error?.(`Notification refusée par ${new URL(url).host} : HTTP ${res.status}`);
    } catch (e) {
      this.log.error?.(`Échec de la notification vers ${new URL(url).host} : ${e.message}`);
    }
  }
}
