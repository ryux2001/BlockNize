import { ConflictException, Injectable } from '@nestjs/common';
import { createHash, randomUUID } from 'node:crypto';
import { and, eq, sql } from 'drizzle-orm';
import { InjectDatabase, type Database } from '../database/database.types';
import { idempotencyRequests, syncCommits, syncHeads, users } from '../database/schema';

type Tx = Parameters<Parameters<Database['transaction']>[0]>[0];
type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };
const DURABLE_SYNC_KEY = /^bn-sync:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}):([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?::[A-Za-z0-9._:-]{1,40})?$/i;

function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => `${JSON.stringify(key)}:${canonical(item)}`).join(',')}}`;
  return JSON.stringify(value) ?? 'null';
}

@Injectable()
export class IdempotencyService {
  constructor(@InjectDatabase() private readonly db: Database) {}

  async run<T>(ownerId: string, operation: string, key: string, request: unknown, work: (tx: Tx) => Promise<T>, responseStatus = 200): Promise<T> {
    if (!/^[A-Za-z0-9._:-]{8,128}$/.test(key)) throw new ConflictException('Idempotency-Key debe tener entre 8 y 128 caracteres seguros.');
    const durableMatch = DURABLE_SYNC_KEY.exec(key);
    const requestHash = createHash('sha256').update(canonical(request)).digest('hex');
    return this.db.transaction(async (tx) => {
      await tx.select({ id: users.id }).from(users).where(eq(users.id, ownerId)).for('update');
      const now = new Date();
      const [prior] = await tx.select().from(idempotencyRequests).where(and(eq(idempotencyRequests.ownerId, ownerId), eq(idempotencyRequests.key, key), eq(idempotencyRequests.operation, operation))).limit(1);
      if (prior && (prior.expiresAt === null || prior.expiresAt > now)) {
        if (prior.requestHash !== requestHash) throw new ConflictException('Esta Idempotency-Key ya se usó con otro contenido.');
        return prior.responseBody as T;
      }
      if (prior) await tx.delete(idempotencyRequests).where(and(eq(idempotencyRequests.ownerId, ownerId), eq(idempotencyRequests.key, key), eq(idempotencyRequests.operation, operation)));
      const result = await work(tx);
      const responseBody = JSON.parse(JSON.stringify(result)) as JsonValue;
      await tx.insert(idempotencyRequests).values({ ownerId, key, operation, requestHash, responseStatus,
        responseBody, deviceId: durableMatch?.[1] ?? null, createdAt: now,
        expiresAt: durableMatch ? null : new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000) });
      await tx.insert(syncHeads).values({ ownerId, revision: 0 }).onConflictDoNothing();
      const [head] = await tx.update(syncHeads).set({ revision: sql`${syncHeads.revision} + 1`, updatedAt: now })
        .where(eq(syncHeads.ownerId, ownerId)).returning({ revision: syncHeads.revision });
      await tx.insert(syncCommits).values({ ownerId, revision: head.revision,
        operationId: durableMatch ? key : randomUUID(), operation, requestHash, committedAt: now });
      return result;
    });
  }
}
