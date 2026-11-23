import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { config as loadEnv } from 'dotenv';
import { Pool } from 'pg';

loadEnv();

async function expectConstraint(client, name, statement, values, code) {
  await client.query(`SAVEPOINT ${name}`);
  await assert.rejects(client.query(statement, values), (error) => error.code === code);
  await client.query(`ROLLBACK TO SAVEPOINT ${name}`);
}

test('PostgreSQL enforces owner relations and civil-time constraints', async (context) => {
  if (!process.env.DATABASE_URL) {
    context.skip('DATABASE_URL is required; copy backend/.env.example to backend/.env first.');
    return;
  }

  const pool = new Pool({ connectionString: process.env.DATABASE_URL, max: 1 });
  const client = await pool.connect();
  const suffix = randomUUID();

  try {
    await client.query('BEGIN');
    const ownerA = randomUUID();
    const ownerB = randomUUID();
    const templateA = randomUUID();
    const taskA = randomUUID();

    await client.query(
      'INSERT INTO users (id, auth_issuer, auth_subject, time_zone) VALUES ($1, $2, $3, $4), ($5, $2, $6, $4)',
      [ownerA, `https://blocknize.test/${suffix}`, `owner-a-${suffix}`, 'UTC', ownerB, `owner-b-${suffix}`],
    );
    await client.query('INSERT INTO templates (id, owner_id, name) VALUES ($1, $2, $3)', [templateA, ownerA, 'rollback fixture']);
    await client.query('INSERT INTO task_definitions (id, owner_id, template_id, title) VALUES ($1, $2, $3, $4)', [taskA, ownerA, templateA, 'rollback fixture']);

    await expectConstraint(client, 'cross_owner_template',
      'INSERT INTO task_definitions (owner_id, template_id, title) VALUES ($1, $2, $3)',
      [ownerB, templateA, 'must be rejected'], '23503');

    const tagB = randomUUID();
    await client.query('INSERT INTO tags (id, owner_id, name, normalized_name) VALUES ($1, $2, $3, $4)', [tagB, ownerB, 'Other owner', 'other-owner']);
    await expectConstraint(client, 'cross_owner_tag',
      'INSERT INTO task_definitions (owner_id, template_id, title, tag_id) VALUES ($1, $2, $3, $4)',
      [ownerA, templateA, 'must be rejected', tagB], '23503');

    await client.query(
      'INSERT INTO schedule_blocks (owner_id, template_id, task_id, days_mask, start_minute, end_minute) VALUES ($1, $2, $3, 127, 1380, 1440)',
      [ownerA, templateA, taskA],
    );
    await expectConstraint(client, 'invalid_interval',
      'INSERT INTO schedule_blocks (owner_id, template_id, task_id, days_mask, start_minute, end_minute) VALUES ($1, $2, $3, 1, 600, 600)',
      [ownerA, templateA, taskA], '23514');
    await expectConstraint(client, 'invalid_day_mask',
      'INSERT INTO schedule_blocks (owner_id, template_id, task_id, days_mask, start_minute, end_minute) VALUES ($1, $2, $3, 0, 600, 660)',
      [ownerA, templateA, taskA], '23514');
  } finally {
    await client.query('ROLLBACK');
    client.release();
    await pool.end();
  }
});
