import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createApp } from '../src/app.js';
import { ProviderError } from '../src/errors.js';
import { normalizeAvailability, normalizeBetween, normalizeFare, normalizeSchedule, normalizeStations } from '../src/provider/normalize.js';
import { DEFAULT_BASE_URL, RailRadarClient } from '../src/provider/railRadarClient.js';

async function withServer(app, run) {
  const server = createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const address = server.address();
    await run(`http://127.0.0.1:${address.port}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

function fakeClient(get = async () => ({})) { return { get }; }

test('health returns a safe process status without provider configuration', async () => {
  await withServer(createApp({ client: fakeClient(), rateLimitMax: 100 }), async (base) => {
    const response = await fetch(`${base}/health`);
    assert.equal(response.status, 200);
    assert.deepEqual((await response.json()).data, { status: 'ok' });
  });
});

test('valid station request normalizes provider result and preserves empty results', async () => {
  let observed;
  const app = createApp({ client: fakeClient(async (path, query) => {
    observed = { path, query };
    return [{ code: 'NDLS', name: 'New Delhi', city: 'Delhi' }];
  }), rateLimitMax: 100 });
  await withServer(app, async (base) => {
    const response = await fetch(`${base}/api/stations/search?q=Delhi`);
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.deepEqual(observed, { path: 'lookup/search/stations', query: { q: 'Delhi', limit: 10 } });
    assert.deepEqual(body.data.stations, [{ code: 'NDLS', name: 'New Delhi', city: 'Delhi' }]);
  });
  assert.deepEqual(normalizeStations([]), []);
  assert.throws(() => normalizeStations(undefined), (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
});

test('station search returns the documented success envelope for an empty provider result', async () => {
  await withServer(createApp({ client: fakeClient(async () => []), rateLimitMax: 100 }), async (base) => {
    const response = await fetch(`${base}/api/stations/search?q=NoMatches`);
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.success, true);
    assert.deepEqual(body.data.stations, []);
    assert.equal(typeof body.meta.requestId, 'string');
  });
});

test('validates malformed input before provider call', async () => {
  let calls = 0;
  await withServer(createApp({ client: fakeClient(async () => { calls += 1; }), rateLimitMax: 100 }), async (base) => {
    const response = await fetch(`${base}/api/trains/between/`);
    assert.equal(response.status, 400);
    const invalidDate = await fetch(`${base}/api/trains/between?from=NDLS&to=HWH&date=2026-02-30`);
    assert.equal(invalidDate.status, 400);
    assert.equal((await invalidDate.json()).error.code, 'INVALID_INPUT');
  });
  assert.equal(calls, 0);
});

test('between normalizer retains actual day offsets and missing fields', () => {
  const normalized = normalizeBetween({ from: { code: 'NDLS', name: 'New Delhi' }, to: { code: 'HWH', name: 'Howrah' },
    count: 1, trains: [{ train: { number: '12345', name: 'Test', runDays: ['MON'] }, from: { departure: '20:00', day: 0, sequence: 1 }, to: { arrival: '08:00', day: 1, sequence: 8 }, duration: 720 }] });
  assert.equal(normalized.trains[0].to.day, 1);
  assert.equal(normalized.trains[0].to.arrival, '08:00');
  assert.equal(normalized.trains[0].totalHaltsBetween, null);
});

test('schedule and fare normalization preserve provider details and unknown values', () => {
  const schedule = normalizeSchedule({ train: { number: '12345', name: 'Example train' }, route: [{ sequence: 2, station: { code: 'HWH', name: 'Howrah' }, arrivalDay: 1 }] });
  assert.equal(schedule.stops[0].sequence, 2);
  assert.equal(schedule.stops[0].arrivalDay, 1);
  assert.equal(schedule.train.category, null);
  const fare = normalizeFare({ trainNumber: '12345', sourceStation: 'NDLS', destinationStation: 'HWH', totalFare: 875, breakdown: { baseFare: 700, goodsServiceTax: 20 } });
  assert.equal(fare.fareKnown, true);
  assert.equal(fare.totalFare, 875);
  assert.equal(fare.breakdown.baseFare, 700);
  assert.equal(fare.breakdown.reservationCharge, null);
  assert.deepEqual(fare.source, { code: 'NDLS', name: null });
  assert.equal(normalizeFare({}).fareKnown, false);
});

test('malformed provider train and schedule payloads are not reported as empty successful results', () => {
  assert.throws(() => normalizeBetween({ from: { code: 'NDLS', name: 'New Delhi' }, to: { code: 'HWH', name: 'Howrah' }, count: 0 }),
    (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
  assert.throws(() => normalizeSchedule({ train: { number: '12345', name: 'Example' } }),
    (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
});

test('availability selects requested date only and preserves RAC, waitlist, and unknown values', () => {
  const payload = { trainNumber: '12345', avlDayList: [
    { availablityDate: '2026-10-20', availablityStatus: 'AVAILABLE-0042' },
    { availablityDate: '2026-10-21', availablityStatus: 'RAC 12' },
  ] };
  assert.equal(normalizeAvailability(payload, '2026-10-21').availabilityStatus, 'RAC 12');
  assert.equal(normalizeAvailability(payload, '2026-10-21').statusType, 'RAC');
  assert.equal(normalizeAvailability(payload, '2026-10-20').statusType, 'AVAILABLE');
  assert.equal(normalizeAvailability({ avlDayList: [{ availablityDate: '2026-10-21', availablityStatus: 'GNWL24/WL11' }] }, '2026-10-21').statusType, 'WAITLIST');
  const missing = normalizeAvailability(payload, '2026-10-22');
  assert.equal(missing.availabilityStatus, null);
  assert.equal(missing.statusType, 'UNKNOWN');
});

test('provider request uses documented HTTPS URL and bearer auth without exposing key', async () => {
  const key = 'private-test-key';
  let request;
  const client = new RailRadarClient({ apiKey: key, fetchImpl: async (url, options) => {
    request = { url: String(url), options };
    return { ok: true, json: async () => ({ success: true, data: [] }) };
  } });
  assert.equal(DEFAULT_BASE_URL, 'https://api.railradar.in/v1');
  await client.get('lookup/search/stations', { q: 'Delhi' });
  assert.equal(new URL(request.url).protocol, 'https:');
  assert.equal(request.options.headers.Authorization, `Bearer ${key}`);
});

test('missing key and timeout have distinct safe errors', async () => {
  const missing = new RailRadarClient({ apiKey: '' });
  await assert.rejects(missing.get('x'), (error) => error.code === 'PROVIDER_NOT_CONFIGURED');
  const timed = new RailRadarClient({ apiKey: 'secret', fetchImpl: async () => { const error = new Error('timed out'); error.name = 'TimeoutError'; throw error; } });
  await assert.rejects(timed.get('x'), (error) => error.code === 'PROVIDER_TIMEOUT');
});

test('provider status mapping distinguishes auth, not found, throttling and outage; secrets are not returned', async () => {
  for (const [status, code] of [[401, 'PROVIDER_AUTH_ERROR'], [404, 'PROVIDER_NOT_FOUND'], [429, 'PROVIDER_RATE_LIMITED'], [503, 'PROVIDER_UNAVAILABLE']]) {
    const secret = 'never-return-this-key';
    const client = new RailRadarClient({ apiKey: secret, fetchImpl: async () => ({ ok: false, status, json: async () => ({ message: secret }) }) });
    await withServer(createApp({ client, rateLimitMax: 100 }), async (base) => {
      const response = await fetch(`${base}/api/stations/search?q=test`);
      const body = await response.text();
      assert.equal(response.status, status === 404 ? 404 : status === 429 ? 503 : 502);
      assert.equal(JSON.parse(body).error.code, code);
      assert.equal(body.includes(secret), false);
    });
  }
});

test('malformed provider JSON and missing API key are sanitized into error envelopes', async () => {
  const malformed = new RailRadarClient({ apiKey: 'secret', fetchImpl: async () => ({ ok: true, json: async () => { throw new Error('secret parse details'); } }) });
  const noKey = new RailRadarClient({ apiKey: '' });
  for (const [client, code] of [[malformed, 'INVALID_PROVIDER_RESPONSE'], [noKey, 'PROVIDER_NOT_CONFIGURED']]) {
    await withServer(createApp({ client, rateLimitMax: 100 }), async (base) => {
      const response = await fetch(`${base}/api/stations/search?q=test`);
      const body = await response.text();
      assert.equal(response.status, code === 'PROVIDER_NOT_CONFIGURED' ? 503 : 502);
      assert.equal(JSON.parse(body).error.code, code);
      assert.equal(body.includes('secret'), false);
      assert.equal(body.includes('stack'), false);
    });
  }
});

test('rate limit has the same JSON error envelope', async () => {
  await withServer(createApp({ client: fakeClient(), rateLimitMax: 1 }), async (base) => {
    await fetch(`${base}/api/stations/search?q=test`);
    const response = await fetch(`${base}/api/stations/search?q=test`);
    assert.equal(response.status, 429);
    assert.equal((await response.json()).error.code, 'RATE_LIMITED');
  });
});

test('unknown endpoint is a not-found error', async () => {
  await withServer(createApp({ client: fakeClient() }), async (base) => {
    const response = await fetch(`${base}/no-such-route`);
    assert.equal(response.status, 404);
    assert.equal((await response.json()).error.code, 'NOT_FOUND');
  });
});

test('provider error objects do not embed upstream body or credentials', async () => {
  const client = new RailRadarClient({ apiKey: 'secret', fetchImpl: async () => ({ ok: false, status: 401, text: async () => 'secret' }) });
  await assert.rejects(client.get('x'), (error) => error instanceof ProviderError && !error.message.includes('secret'));
});
