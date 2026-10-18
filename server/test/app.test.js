import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createHandler, buildConfigUrl } from '../src/app.js';
import { Supervisor } from '../src/supervisor.js';
import { MemoryStore } from '../src/store.js';

let server;
let base;
const admin = { Authorization: `Basic ${Buffer.from('admin:secret').toString('base64')}` };

before(async () => {
  const supervisor = new Supervisor({ store: new MemoryStore(), notifier: { send() {} } });
  server = createServer(createHandler({ supervisor, adminPassword: 'secret', publicUrl: 'https://dati.example', consoleHtml: '<h1>console</h1>' }));
  await new Promise((resolve) => server.listen(0, resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

after(() => server.close());

const json = (body) => ({ method: 'POST', body: JSON.stringify(body), headers: { 'Content-Type': 'application/json' } });

test('console requires the admin password', async () => {
  const anonymous = await fetch(`${base}/`);
  assert.equal(anonymous.status, 401);
  assert.match(anonymous.headers.get('www-authenticate'), /Basic/);
  const wrong = await fetch(`${base}/`, { headers: { Authorization: `Basic ${Buffer.from('admin:nope').toString('base64')}` } });
  assert.equal(wrong.status, 401);
  assert.equal((await fetch(`${base}/`, { headers: admin })).status, 200);
});

test('full flow: enrol a phone, raise an alarm, acknowledge it', async () => {
  const created = await fetch(`${base}/api/v1/admin/devices`, {
    ...json({ name: 'Alice', settings: { contacts: '+33600000000', checkin: 30 } }),
    headers: { ...admin, 'Content-Type': 'application/json' },
  });
  assert.equal(created.status, 201);
  const { configUrl } = await created.json();
  const params = new URL(configUrl.replace('dati://', 'http://')).searchParams;
  assert.equal(params.get('server'), 'https://dati.example');
  assert.equal(params.get('contacts'), '+33600000000');
  const token = params.get('token');

  const device = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  const alarm = await fetch(`${base}/api/v1/events`, { ...json({ type: 'alarm', reason: 'FALL', state: 'alarm' }), headers: device });
  assert.equal(alarm.status, 200);

  const status = await (await fetch(`${base}/api/v1/admin/status`, { headers: admin })).json();
  const alertId = status.alerts[0].id;
  await fetch(`${base}/api/v1/admin/alerts/${alertId}/ack`, { ...json({ by: 'Bruno' }), headers: { ...admin, 'Content-Type': 'application/json' } });

  const beat = await (await fetch(`${base}/api/v1/heartbeat`, { ...json({ state: 'alarm' }), headers: device })).json();
  assert.equal(beat.ack.by, 'Bruno');
});

test('device endpoints reject unknown tokens', async () => {
  const res = await fetch(`${base}/api/v1/heartbeat`, { ...json({}), headers: { Authorization: 'Bearer nope' } });
  assert.equal(res.status, 401);
  assert.equal(res.headers.get('www-authenticate'), null);
});

test('invalid JSON is a 400', async () => {
  const res = await fetch(`${base}/api/v1/admin/devices`, { method: 'POST', body: '{', headers: admin });
  assert.equal(res.status, 400);
});

test('config url only carries known settings', () => {
  const url = buildConfigUrl({ publicUrl: 'https://x', token: 't', name: 'A B', settings: { checkin: 0, evil: 'x', fall: '' } });
  assert.equal(url, 'dati://config?server=https%3A%2F%2Fx&token=t&name=A+B&checkin=0');
});
