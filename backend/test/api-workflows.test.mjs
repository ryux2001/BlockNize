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
  const text = await response.text();
  return { status: response.status, headers: response.headers, body: text ? JSON.parse(text) : null };
}

function dateAfter(days) {
  const date = new Date();
  date.setUTCHours(0, 0, 0, 0);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

test('API workflows: activation, agenda, one-off lifecycle, completion, exceptions and history', async (context) => {
  const tokenA = process.env.BLOCKNIZE_ACCEPTANCE_TOKEN_A;
  const tokenB = process.env.BLOCKNIZE_ACCEPTANCE_TOKEN_B;
  if (!tokenA || !tokenB) {
    context.skip('Run scripts/neon-auth-acceptance.mjs or set both temporary acceptance tokens.');
    return;
  }
  if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required to clean the API fixtures.');

  const db = new Pool({ connectionString: process.env.DATABASE_URL, max: 1 });
  const suffix = randomUUID();
  const keys = [];
  const key = (label) => {
    const value = `accept-${label}-${suffix}`;
    keys.push(value);
    return value;
  };
  const occurrenceKeys = new Set();
  const concurrentTemplateIds = new Set();
  let ownerId;
  let templateId;
  let oneOffId;

  try {
    const [profileA, profileB] = await Promise.all([request(tokenA, '/me'), request(tokenB, '/me')]);
    assert.equal(profileA.status, 200);
    assert.equal(profileB.status, 200);
    assert.notEqual(profileA.body.id, profileB.body.id);
    ownerId = profileA.body.id;

    const name = `Workflow acceptance ${suffix}`;
    const created = await request(tokenA, '/templates', {
      method: 'POST', headers: { 'Idempotency-Key': key('template') }, body: { name },
    });
    assert.equal(created.status, 201);
    templateId = created.body.id;
    assert.ok(templateId);

    const concurrentCreateKey = key('concurrent-template');
    const concurrentBody = { name: `${name} concurrent` };
    const concurrentCreates = await Promise.all([1, 2].map(() => request(tokenA, '/templates', {
      method: 'POST', headers: { 'Idempotency-Key': concurrentCreateKey }, body: concurrentBody,
    })));
    assert.deepEqual(concurrentCreates.map((response) => response.status), [201, 201]);
    assert.equal(concurrentCreates[0].body.id, concurrentCreates[1].body.id, 'concurrent retries must resolve to one resource');
    concurrentTemplateIds.add(concurrentCreates[0].body.id);

    const blockResult = await request(tokenA, `/templates/${templateId}/blocks`, {
      method: 'POST', headers: { 'If-Match': created.headers.get('etag'), 'Idempotency-Key': key('block') },
      body: { title: 'Acceptance weekly block', description: 'Temporary fixture', daysMask: 127, startMinute: 600, endMinute: 660 },
    });
    assert.equal(blockResult.status, 201);
    const blockId = blockResult.body.blocks?.[0]?.id;
    assert.ok(blockId, 'creating a block should return its stable ID');

    const activated = await request(tokenA, '/active-selection', {
      method: 'PUT', headers: { 'If-Match': '"0"', 'Idempotency-Key': key('activate') }, body: { templateId },
    });
    assert.equal(activated.status, 200);
    assert.equal(activated.body.templateId, templateId);
    const activeBlockEdit = await request(tokenA, `/templates/${templateId}/blocks/${blockId}`, {
      method: 'PATCH', headers: { 'If-Match': blockResult.headers.get('etag'), 'Idempotency-Key': key('active-block-edit') },
      body: { id: blockId, title: 'Acceptance weekly block updated', description: 'Active revision fixture', daysMask: 127, startMinute: 600, endMinute: 660 },
    });
    assert.equal(activeBlockEdit.status, 200);
    const revisions = await request(tokenA, `/templates/${templateId}/revisions`);
    assert.equal(revisions.status, 200);
    assert.equal(revisions.body.length, 2, 'editing an active template should preserve the prior revision');
    const revisionId = revisions.body[0].id;
    const foreignRevision = await request(tokenB, `/templates/${templateId}/revisions/${revisionId}`);
    assert.equal(foreignRevision.status, 404);
    assert.equal((await request(tokenB, '/active-selection')).body, null);

    const day = dateAfter(1);
    const replacementTarget = dateAfter(3);
    const dayAgenda = await request(tokenA, `/agenda?date=${day}`);
    assert.equal(dayAgenda.status, 200);
    let weekly = dayAgenda.body.find((item) => item.sourceId === blockId);
    assert.ok(weekly, 'active weekly block should be projected into the requested date');
    assert.equal(weekly.title, 'Acceptance weekly block updated');
    occurrenceKeys.add(weekly.occurrenceKey);
    const foreignAgenda = await request(tokenB, `/agenda?date=${day}`);
    assert.equal(foreignAgenda.status, 200);
    assert.equal(foreignAgenda.body.length, 0, 'another account must not see the weekly block');

    const completed = await request(tokenA, '/completions', {
      method: 'POST', headers: { 'Idempotency-Key': key('complete') },
      body: { date: day, occurrenceKey: weekly.occurrenceKey, completed: true },
    });
    assert.equal(completed.status, 200);
    assert.equal((await request(tokenA, `/agenda?date=${day}`)).body.find((item) => item.occurrenceKey === weekly.occurrenceKey)?.completed, true);
    const undone = await request(tokenA, '/completions', {
      method: 'POST', headers: { 'Idempotency-Key': key('undo') },
      body: { date: day, occurrenceKey: weekly.occurrenceKey, completed: false },
    });
    assert.equal(undone.status, 200);
    assert.equal((await request(tokenA, `/agenda?date=${day}`)).body.find((item) => item.occurrenceKey === weekly.occurrenceKey)?.completed, false);

    const oneOffBody = { date: day, title: `Acceptance one-off ${suffix}`, description: 'Temporary fixture', startMinute: 780, endMinute: 810 };
    const oneOff = await request(tokenA, '/one-off-tasks', {
      method: 'POST', headers: { 'Idempotency-Key': key('oneoff-create') }, body: oneOffBody,
    });
    assert.equal(oneOff.status, 201);
    oneOffId = oneOff.body.id;
    assert.ok(oneOffId);
    const oneOffOccurrence = (await request(tokenA, `/agenda?date=${day}`)).body.find((item) => item.sourceId === oneOffId);
    assert.ok(oneOffOccurrence, 'created one-off task should appear in the agenda');
    occurrenceKeys.add(oneOffOccurrence.occurrenceKey);
    assert.equal((await request(tokenB, `/one-off-tasks?from=${day}&to=${day}`)).body.length, 0);
    const foreignOneOffWrite = await request(tokenB, `/one-off-tasks/${oneOffId}`, {
      method: 'PATCH', headers: { 'If-Match': oneOff.headers.get('etag'), 'Idempotency-Key': key('foreign-oneoff') }, body: oneOffBody,
    });
    assert.equal(foreignOneOffWrite.status, 404);

    const updatedBody = { ...oneOffBody, title: `${oneOffBody.title} updated`, startMinute: 810, endMinute: 840 };
    const updatedOneOff = await request(tokenA, `/one-off-tasks/${oneOffId}`, {
      method: 'PATCH', headers: { 'If-Match': oneOff.headers.get('etag'), 'Idempotency-Key': key('oneoff-update') }, body: updatedBody,
    });
    assert.equal(updatedOneOff.status, 200);
    assert.equal((await request(tokenA, `/one-off-tasks?from=${day}&to=${day}`)).body.find((item) => item.id === oneOffId)?.title, updatedBody.title);
    const deletedOneOff = await request(tokenA, `/one-off-tasks/${oneOffId}`, {
      method: 'DELETE', headers: { 'If-Match': updatedOneOff.headers.get('etag'), 'Idempotency-Key': key('oneoff-delete') },
    });
    assert.equal(deletedOneOff.status, 200);
    assert.equal((await request(tokenA, `/one-off-tasks?from=${day}&to=${day}`)).body.some((item) => item.id === oneOffId), false);

    const skip = await request(tokenA, '/occurrence-exceptions', {
      method: 'POST', headers: { 'If-Match': '"0"', 'Idempotency-Key': key('skip') },
      body: { date: day, occurrenceKey: weekly.occurrenceKey, action: 'SKIP' },
    });
    assert.equal(skip.status, 200);
    assert.equal(skip.body.action, 'SKIP');
    const skippedAgenda = await request(tokenA, `/agenda?date=${day}`);
    const skippedOrigin = skippedAgenda.body.find((item) => item.exceptionId === skip.body.id && item.originalDate === day);
    assert.ok(skippedOrigin, 'SKIP should be represented in the agenda as an exception-origin entry');
    occurrenceKeys.add(skippedOrigin.occurrenceKey);
    const restoredSkip = await request(tokenA, '/occurrence-exceptions', {
      method: 'POST', headers: { 'If-Match': '"1"', 'Idempotency-Key': key('restore-skip') },
      body: { date: day, occurrenceKey: skippedOrigin.occurrenceKey, action: 'RESTORE' },
    });
    assert.equal(restoredSkip.status, 200);
    assert.equal(restoredSkip.body.action, 'RESTORE');

    const replaceDay = dateAfter(2);
    const replaceAgenda = await request(tokenA, `/agenda?date=${replaceDay}`);
    const replaceOrigin = replaceAgenda.body.find((item) => item.sourceId === blockId);
    assert.ok(replaceOrigin);
    occurrenceKeys.add(replaceOrigin.occurrenceKey);
    const replaced = await request(tokenA, '/occurrence-exceptions', {
      method: 'POST', headers: { 'If-Match': '"0"', 'Idempotency-Key': key('replace') },
      body: { date: replaceDay, occurrenceKey: replaceOrigin.occurrenceKey, action: 'REPLACE', targetDate: replacementTarget,
        title: 'Acceptance moved block', description: 'Temporary replacement', startMinute: 900, endMinute: 960 },
    });
    assert.equal(replaced.status, 200);
    assert.equal(replaced.body.action, 'REPLACE');
    const targetAgenda = await request(tokenA, `/agenda?date=${replacementTarget}`);
    assert.ok(targetAgenda.body.some((item) => item.exceptionId === replaced.body.id && item.title === 'Acceptance moved block'));
    const replaceOriginAgenda = await request(tokenA, `/agenda?date=${replaceDay}`);
    const replaceMarker = replaceOriginAgenda.body.find((item) => item.exceptionId === replaced.body.id && item.originalDate === replaceDay);
    assert.ok(replaceMarker);
    occurrenceKeys.add(replaceMarker.occurrenceKey);
    const restoredReplace = await request(tokenA, '/occurrence-exceptions', {
      method: 'POST', headers: { 'If-Match': '"1"', 'Idempotency-Key': key('restore-replace') },
      body: { date: replaceDay, occurrenceKey: replaceMarker.occurrenceKey, action: 'RESTORE' },
    });
    assert.equal(restoredReplace.status, 200);
    assert.equal(restoredReplace.body.action, 'RESTORE');

    const history = await request(tokenA, `/history?from=${day}&to=${replacementTarget}&limit=7`);
    assert.equal(history.status, 200);
    assert.equal(history.body.entries.length, 3);
    assert.equal(history.body.nextDate, null);
    const foreignHistory = await request(tokenB, `/history?from=${day}&to=${replacementTarget}&limit=7`);
    assert.equal(foreignHistory.status, 200);
    assert.ok(foreignHistory.body.entries.every((entry) => entry.occurrences.length === 0));
  } finally {
    if (ownerId && process.env.DATABASE_URL) {
      const client = await db.connect();
      try {
        await client.query('BEGIN');
        await client.query('DELETE FROM idempotency_requests WHERE owner_id = $1 AND key = ANY($2::text[])', [ownerId, keys]);
        if (templateId) {
          await client.query('DELETE FROM completion_records WHERE owner_id = $1 AND (source_template_id = $2 OR occurrence_key = ANY($3::text[]))',
            [ownerId, templateId, [...occurrenceKeys]]);
          await client.query(`DELETE FROM occurrence_exception_events WHERE owner_id = $1 AND exception_id IN
            (SELECT id FROM occurrence_exceptions WHERE owner_id = $1 AND template_id = $2)`, [ownerId, templateId]);
          await client.query('DELETE FROM occurrence_exceptions WHERE owner_id = $1 AND template_id = $2', [ownerId, templateId]);
          if (oneOffId) {
            await client.query('DELETE FROM one_off_schedule_events WHERE owner_id = $1 AND task_id = $2', [ownerId, oneOffId]);
            await client.query('DELETE FROM one_off_tasks WHERE owner_id = $1 AND id = $2', [ownerId, oneOffId]);
          }
          await client.query('DELETE FROM schedule_change_events WHERE owner_id = $1 AND template_id = $2', [ownerId, templateId]);
          await client.query('DELETE FROM active_selections WHERE owner_id = $1 AND template_id = $2', [ownerId, templateId]);
          await client.query('DELETE FROM template_revisions WHERE owner_id = $1 AND template_id = $2', [ownerId, templateId]);
          await client.query('DELETE FROM schedule_blocks WHERE owner_id = $1 AND template_id = $2', [ownerId, templateId]);
          await client.query('DELETE FROM task_definitions WHERE owner_id = $1 AND template_id = $2', [ownerId, templateId]);
          await client.query('DELETE FROM templates WHERE owner_id = $1 AND id = $2', [ownerId, templateId]);
        }
        if (concurrentTemplateIds.size) {
          const ids = [...concurrentTemplateIds];
          await client.query('DELETE FROM templates WHERE owner_id = $1 AND id = ANY($2::uuid[])', [ownerId, ids]);
        }
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
