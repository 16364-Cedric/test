import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Supervisor, AlertType } from '../src/supervisor.js';
import { MemoryStore } from '../src/store.js';

function setup() {
  const clock = { t: 1_000_000 };
  const sent = [];
  const supervisor = new Supervisor({
    store: new MemoryStore(),
    notifier: { send: (n) => sent.push(n) },
    now: () => clock.t,
    heartbeatTimeoutMs: 15 * 60_000,
    escalateAfterMs: 5 * 60_000,
    retentionMs: 24 * 3600_000,
  });
  const { device, token } = supervisor.createDevice('Alice');
  return { clock, sent, supervisor, device, token };
}

const minutes = (n) => n * 60_000;

test('device is found by its token, never by a wrong one', () => {
  const { supervisor, device, token } = setup();
  assert.equal(supervisor.findDeviceByToken(token), device);
  assert.equal(supervisor.findDeviceByToken('nope'), null);
  assert.ok(!JSON.stringify(supervisor.data).includes(token), 'le jeton ne doit pas être stocké en clair');
});

test('an alarm event creates an urgent alert with the position', () => {
  const { supervisor, device, sent } = setup();
  supervisor.event(device, { type: 'alarm', reason: 'FALL', state: 'alarm', location: { lat: 45.1, lon: 5.7, accuracy: 8 } });
  const [alert] = supervisor.status().alerts;
  assert.equal(alert.type, AlertType.ALARM);
  assert.equal(alert.label, 'Chute détectée');
  assert.equal(alert.mapsUrl, 'https://maps.google.com/?q=45.1,5.7');
  assert.equal(sent.at(-1).urgent, true);
});

test('acknowledgement is reported back to the phone', () => {
  const { supervisor, device } = setup();
  supervisor.event(device, { type: 'alarm', reason: 'SOS' });
  const alertId = supervisor.data.alerts[0].id;
  assert.equal(supervisor.heartbeat(device, { state: 'alarm' }).ack, null);
  supervisor.acknowledge(alertId, 'Bruno');
  assert.equal(supervisor.heartbeat(device, { state: 'alarm' }).ack.by, 'Bruno');
});

test('the worker cancelling the alarm resolves it', () => {
  const { supervisor, device } = setup();
  supervisor.event(device, { type: 'alarm', reason: 'IMMOBILITY' });
  supervisor.event(device, { type: 'ok', state: 'monitoring' });
  assert.equal(supervisor.data.alerts[0].resolution, 'Annulée par le salarié');
});

test('silence while monitoring raises a lost-contact alert, then recovers', () => {
  const { supervisor, device, clock, sent } = setup();
  supervisor.event(device, { type: 'start', state: 'monitoring', location: { lat: 1, lon: 2 } });
  clock.t += minutes(14);
  supervisor.tick();
  assert.equal(supervisor.data.alerts.length, 0);

  clock.t += minutes(2);
  supervisor.tick();
  supervisor.tick();
  const lost = supervisor.data.alerts.filter((a) => a.type === AlertType.LOST_CONTACT);
  assert.equal(lost.length, 1, 'une seule alerte par perte de contact');
  assert.deepEqual([lost[0].location.lat, lost[0].location.lon], [1, 2]);
  assert.match(sent.at(-1).title, /PERTE DE CONTACT/);

  supervisor.heartbeat(device, { state: 'monitoring' });
  assert.equal(lost[0].resolution, 'Contact rétabli');
});

test('no lost-contact alert once monitoring is stopped', () => {
  const { supervisor, device, clock } = setup();
  supervisor.event(device, { type: 'start', state: 'monitoring' });
  supervisor.event(device, { type: 'stop', state: 'idle' });
  clock.t += minutes(60);
  supervisor.tick();
  assert.equal(supervisor.data.alerts.length, 0);
});

test('an unacknowledged alarm is escalated once', () => {
  const { supervisor, device, clock, sent } = setup();
  supervisor.event(device, { type: 'alarm', reason: 'FALL' });
  clock.t += minutes(5);
  supervisor.tick();
  supervisor.tick();
  const escalations = sent.filter((n) => n.escalation);
  assert.equal(escalations.length, 1);
});

test('acknowledged and test alarms are not escalated', () => {
  const { supervisor, device, clock, sent } = setup();
  supervisor.event(device, { type: 'alarm', reason: 'TEST' });
  supervisor.event(device, { type: 'alarm', reason: 'FALL' });
  supervisor.acknowledge(supervisor.data.alerts[1].id, 'Chloé');
  clock.t += minutes(10);
  supervisor.tick();
  assert.equal(sent.filter((n) => n.escalation).length, 0);
  assert.equal(sent.find((n) => n.title.includes('ESSAI')).urgent, false);
});

test('status never exposes device secrets or routine positions', () => {
  const { supervisor, device } = setup();
  supervisor.heartbeat(device, { state: 'monitoring', location: { lat: 3, lon: 4 } });
  const [shown] = supervisor.status().devices;
  assert.equal(shown.tokenHash, undefined);
  assert.equal(shown.lastLocation, undefined);
});

test('resolved alerts are purged after the retention period', () => {
  const { supervisor, device, clock } = setup();
  supervisor.event(device, { type: 'alarm', reason: 'SOS' });
  supervisor.resolve(supervisor.data.alerts[0].id);
  clock.t += 25 * 3600_000;
  supervisor.tick();
  assert.equal(supervisor.data.alerts.length, 0);
});

test('unknown event types are rejected', () => {
  const { supervisor, device } = setup();
  assert.throws(() => supervisor.event(device, { type: 'party' }));
});

test('a network outage reported on reconnection is kept in the history', () => {
  const { supervisor, device, sent } = setup();
  supervisor.event(device, { type: 'network_restored', durationMs: minutes(7) });
  const [alert] = supervisor.status().alerts;
  assert.equal(alert.type, AlertType.NETWORK_LOSS);
  assert.equal(alert.resolution, 'Réseau retrouvé après 7 min');
  assert.equal(sent.at(-1).urgent, false);
});

test('loss of verticality has a readable label', () => {
  const { supervisor, device } = setup();
  supervisor.event(device, { type: 'alarm', reason: 'LOSS_OF_VERTICALITY' });
  assert.equal(supervisor.status().alerts[0].label, 'Perte de verticalité');
});
