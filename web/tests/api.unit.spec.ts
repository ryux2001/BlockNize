import { expect, test } from '@playwright/test';
import { createMutationIntent, requestJson, sendMutation } from '../src/lib/api/client';

const originalFetch = globalThis.fetch;

test.afterEach(() => {
  globalThis.fetch = originalFetch;
});

test('mutation retries reuse the same idempotency key, body and version', async () => {
  const requests: Array<{ headers: Headers; body: string | undefined }> = [];
  globalThis.fetch = async (_input, init) => {
    requests.push({ headers: new Headers(init?.headers), body: init?.body?.toString() });
    return new Response(JSON.stringify({ version: 8 }), { status: 200, headers: { 'content-type': 'application/json' } });
  };

  const intent = createMutationIntent({
    method: 'PATCH',
    path: '/templates/5cce9e1b-df84-4cf0-907a-af763eb12345',
    version: 7,
    timeZone: 'Europe/Madrid',
    body: { name: 'Semana de prueba' },
  });
  await sendMutation(intent);
  await sendMutation(intent);

  expect(requests).toHaveLength(2);
  expect(requests[1].headers.get('idempotency-key')).toBe(requests[0].headers.get('idempotency-key'));
  expect(requests[1].headers.get('if-match')).toBe('7');
  expect(requests[1].body).toBe(requests[0].body);
  expect(JSON.parse(requests[0].body ?? '{}')).toMatchObject({
    name: 'Semana de prueba',
    clientTimeZone: 'Europe/Madrid',
  });
});

test('API conflicts preserve server version and request ID', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: 'STALE_VERSION',
    message: 'La semana cambió.',
    requestId: 'web-request-17',
    currentVersion: 9,
  }), {
    status: 412,
    headers: { 'content-type': 'application/json', 'x-request-id': 'web-request-17' },
  });

  await expect(requestJson('/templates/5cce9e1b-df84-4cf0-907a-af763eb12345'))
    .rejects.toMatchObject({ status: 412, code: 'STALE_VERSION', requestId: 'web-request-17', currentVersion: 9 });
});

test('offline fetch failures surface a retryable network error', async () => {
  globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };

  await expect(requestJson('/agenda?date=2026-11-19'))
    .rejects.toMatchObject({ status: 0, code: 'NETWORK_UNAVAILABLE' });
});
