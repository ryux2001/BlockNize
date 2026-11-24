import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { config as loadEnv } from 'dotenv';
import { Pool } from 'pg';

loadEnv();

const baseUrl = (process.env.BLOCKNIZE_API_URL ?? 'http://localhost:3001/api/v1').replace(/\/$/, '');

async function request(token, path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...headers, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  return { status: response.status, headers: response.headers, body: await response.json() };
}

test('real Neon tokens authenticate separate owners and isolate templates', async (context) => {
  const tokenA = process.env.BLOCKNIZE_ACCEPTANCE_TOKEN_A;
  const tokenB = process.env.BLOCKNIZE_ACCEPTANCE_TOKEN_B;
  if (!tokenA || !tokenB) {
    context.skip('Set BLOCKNIZE_ACCEPTANCE_TOKEN_A and BLOCKNIZE_ACCEPTANCE_TOKEN_B in the local, git-ignored backend/.env.');
    return;
  }
  if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required to clean the temporary template after the API check.');

  const db = new Pool({ connectionString: process.env.DATABASE_URL, max: 1 });
  const suffix = randomUUID();
  const createKey = `accept-create-${suffix}`;
  const renameKey = `accept-rename-${suffix}`;
  const staleKey = `accept-stale-${suffix}`;
  let ownerId;
  let templateId;

  try {
    const [profileA, profileB] = await Promise.all([request(tokenA, '/me'), request(tokenB, '/me')]);
    assert.equal(profileA.status, 200, 'account A token should be accepted by Neon JWKS verification');
    assert.equal(profileB.status, 200, 'account B token should be accepted by Neon JWKS verification');
    assert.ok(profileA.body.id && profileB.body.id, 'both accounts should have local profiles');
    assert.notEqual(profileA.body.id, profileB.body.id, 'the two tokens should identify distinct accounts');
    ownerId = profileA.body.id;

    const name = `API acceptance ${suffix}`;
    const created = await request(tokenA, '/templates', {
      method: 'POST', headers: { 'Idempotency-Key': createKey }, body: { name },
    });
    assert.equal(created.status, 201);
    templateId = created.body.id;
    const etag = created.headers.get('etag');
    assert.ok(templateId && etag, 'template creation should return an ID and ETag');

    const replay = await request(tokenA, '/templates', {
      method: 'POST', headers: { 'Idempotency-Key': createKey }, body: { name },
    });
    assert.equal(replay.status, 201);
    assert.equal(replay.body.id, templateId, 'an identical retry should return its original resource');

    const conflictingReplay = await request(tokenA, '/templates', {
      method: 'POST', headers: { 'Idempotency-Key': createKey }, body: { name: `${name} changed` },
    });
    assert.equal(conflictingReplay.status, 409, 'reusing an idempotency key with another body should conflict');

    const ownerRead = await request(tokenA, `/templates/${templateId}`);
    assert.equal(ownerRead.status, 200);
    const foreignRead = await request(tokenB, `/templates/${templateId}`);
    assert.equal(foreignRead.status, 404, 'another account must not read the template');
    const foreignList = await request(tokenB, '/templates');
    assert.equal(foreignList.status, 200);
    assert.ok(!foreignList.body.some((item) => item.id === templateId), 'another account must not list the template');
    const foreignRevisionRead = await request(tokenB, `/templates/${templateId}/revisions`);
    assert.equal(foreignRevisionRead.status, 404, 'nested revision routes must also be owner-scoped');

    const foreignWrite = await request(tokenB, `/templates/${templateId}`, {
      method: 'PATCH', headers: { 'If-Match': etag, 'Idempotency-Key': `accept-foreign-${suffix}` },
      body: { name: 'foreign update' },
    });
    assert.equal(foreignWrite.status, 404, 'another account must not modify the template');

    const updated = await request(tokenA, `/templates/${templateId}`, {
      method: 'PATCH', headers: { 'If-Match': etag, 'Idempotency-Key': renameKey }, body: { name: `${name} updated` },
    });
    assert.equal(updated.status, 200);
    const staleWrite = await request(tokenA, `/templates/${templateId}`, {
      method: 'PATCH', headers: { 'If-Match': etag, 'Idempotency-Key': staleKey }, body: { name: `${name} stale` },
    });
    assert.equal(staleWrite.status, 412, 'an outdated ETag must not overwrite a newer value');
  } finally {
    if (ownerId && templateId) {
      const client = await db.connect();
      try {
        await client.query('BEGIN');
        await client.query('DELETE FROM idempotency_requests WHERE owner_id = $1 AND key = ANY($2::text[])',
          [ownerId, [createKey, renameKey, staleKey]]);
        await client.query('DELETE FROM templates WHERE owner_id = $1 AND id = $2', [ownerId, templateId]);
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK');
        throw error;
      } finally {
        client.release();
      }
    }
    await db.end();
  }
});
