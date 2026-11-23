import { BadRequestException, ConflictException, Injectable, NotFoundException, PreconditionFailedException } from '@nestjs/common';
import { and, asc, eq, gte, isNull, lte } from 'drizzle-orm';
import { randomUUID } from 'node:crypto';
import { IdempotencyService } from '../common/idempotency.service';
import { InjectDatabase, type Database } from '../database/database.types';
import { oneOffScheduleEvents, oneOffTasks, tags, users } from '../database/schema';
import { civilDateAt, civilInstant } from '../agenda/civil-time';
import { clientActionZone, clientEffectiveAt, type ClientActionMetadata } from '../common/client-action';

type Tx = Parameters<Parameters<Database['transaction']>[0]>[0];

@Injectable()
export class OneOffService {
  constructor(@InjectDatabase() private readonly db: Database, private readonly idempotency: IdempotencyService) {}

  async list(ownerId: string, from: string, to: string) {
    if (from > to || (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000 > 31) {
      throw new BadRequestException('El rango debe ser ascendente y no superar 32 días.');
    }
    return this.db.select().from(oneOffTasks).where(and(eq(oneOffTasks.ownerId, ownerId), gte(oneOffTasks.date, from), lte(oneOffTasks.date, to), isNull(oneOffTasks.deletedAt))).orderBy(asc(oneOffTasks.date), asc(oneOffTasks.startMinute));
  }

  async get(ownerId: string, id: string) {
    const [task] = await this.db.select().from(oneOffTasks).where(and(eq(oneOffTasks.id, id), eq(oneOffTasks.ownerId, ownerId), isNull(oneOffTasks.deletedAt))).limit(1);
    if (!task) throw new NotFoundException('No se encontró la tarea temporal.');
    return task;
  }

  async create(ownerId: string, input: { id?: string; eventId?: string; date: string; title: string; description?: string; tagName?: string; startMinute: number; endMinute: number } & ClientActionMetadata, key: string) {
    return this.idempotency.run(ownerId, `one-off:create:${input.id ?? 'new'}`, key, input, async (tx) => {
      const zone = await this.timeZone(tx, ownerId);
      const effectiveAt = clientEffectiveAt(input);
      const actionZone = clientActionZone(input, zone);
      this.validate(input.date, input.title, input.startMinute, input.endMinute, zone, effectiveAt, actionZone);
      const tagId = await this.resolveTag(tx, ownerId, input.tagName?.trim() ?? '');
      const [task] = await tx.insert(oneOffTasks).values({ ...(input.id ? { id: input.id } : {}), ownerId, date: input.date, title: input.title.trim(),
        description: input.description?.trim() ?? '', tagId, startMinute: input.startMinute, endMinute: input.endMinute }).returning();
      await this.recordEvent(tx, ownerId, task, input.tagName?.trim() || null, zone, 'UPSERT', input.eventId, effectiveAt);
      return task;
    }, 201);
  }

  async update(ownerId: string, id: string, version: number, input: { eventId?: string; date: string; title: string; description?: string; tagName?: string; startMinute: number; endMinute: number } & ClientActionMetadata, key: string) {
    return this.idempotency.run(ownerId, `one-off:update:${id}`, key, { id, version, input }, async (tx) => {
      const zone = await this.timeZone(tx, ownerId);
      const effectiveAt = clientEffectiveAt(input);
      const actionZone = clientActionZone(input, zone);
      this.validate(input.date, input.title, input.startMinute, input.endMinute, zone, effectiveAt, actionZone);
      const [current] = await tx.select().from(oneOffTasks).where(and(eq(oneOffTasks.id, id), eq(oneOffTasks.ownerId, ownerId), isNull(oneOffTasks.deletedAt))).limit(1);
      if (!current) throw new NotFoundException('No se encontró la tarea temporal.');
      if (current.version !== version) throw new PreconditionFailedException({ code: 'STALE_VERSION', currentVersion: current.version, message: 'La tarea cambió; vuelve a cargarla.' });
      const tagId = await this.resolveTag(tx, ownerId, input.tagName?.trim() ?? '');
      if (current.date === input.date && current.title === input.title.trim() && current.description === (input.description?.trim() ?? '') &&
        current.tagId === tagId && current.startMinute === input.startMinute && current.endMinute === input.endMinute) return current;
      const [updated] = await tx.update(oneOffTasks).set({ date: input.date, title: input.title.trim(), description: input.description?.trim() ?? '',
        tagId, startMinute: input.startMinute, endMinute: input.endMinute, version: version + 1, updatedAt: effectiveAt })
        .where(and(eq(oneOffTasks.id, id), eq(oneOffTasks.ownerId, ownerId), eq(oneOffTasks.version, version))).returning();
      if (!updated) throw new ConflictException('La tarea cambió; vuelve a cargarla.');
      await this.recordEvent(tx, ownerId, updated, input.tagName?.trim() || null, zone, 'UPSERT', input.eventId, effectiveAt);
      return updated;
    });
  }

  async delete(ownerId: string, id: string, version: number, key: string, eventId?: string, clientActionAt?: string, clientTimeZone?: string) {
    const metadata = { clientActionAt, clientTimeZone };
    const effectiveAt = clientEffectiveAt(metadata);
    return this.idempotency.run(ownerId, `one-off:delete:${id}`, key, { id, version, eventId: eventId ?? null, metadata }, async (tx) => {
      const [current] = await tx.select().from(oneOffTasks).where(and(eq(oneOffTasks.id, id), eq(oneOffTasks.ownerId, ownerId), isNull(oneOffTasks.deletedAt))).limit(1);
      if (!current) throw new NotFoundException('No se encontró la tarea temporal.');
      if (current.version !== version) throw new PreconditionFailedException({ code: 'STALE_VERSION', currentVersion: current.version, message: 'La tarea cambió; vuelve a cargarla.' });
      const zone = await this.timeZone(tx, ownerId);
      clientActionZone(metadata, zone);
      const [updated] = await tx.update(oneOffTasks).set({ deletedAt: effectiveAt, version: version + 1, updatedAt: effectiveAt })
        .where(and(eq(oneOffTasks.id, id), eq(oneOffTasks.ownerId, ownerId), eq(oneOffTasks.version, version))).returning();
      if (!updated) throw new ConflictException('La tarea cambió; vuelve a cargarla.');
      const [tag] = current.tagId ? await tx.select({ name: tags.name }).from(tags).where(and(eq(tags.id, current.tagId), eq(tags.ownerId, ownerId))).limit(1) : [];
      await this.recordEvent(tx, ownerId, current, tag?.name ?? null, zone, 'DELETE', eventId, effectiveAt);
      return { id, deleted: true, version: version + 1 };
    });
  }

  private async timeZone(tx: Tx, ownerId: string) {
    const [profile] = await tx.select({ timeZone: users.timeZone }).from(users).where(eq(users.id, ownerId)).limit(1);
    return profile?.timeZone ?? 'UTC';
  }

  private validate(date: string, title: string, start: number, end: number, zone: string, actionAt?: Date, actionZone?: string) {
    if (!title.trim()) throw new BadRequestException('El título es obligatorio.');
    if (start < 0 || start > 1439 || end < 1 || end > 1440 || start >= end) throw new BadRequestException('Horario inválido; no se admiten bloques que crucen medianoche.');
    const today = civilDateAt(new Date(), zone);
    const actionDate = actionAt && actionZone ? civilDateAt(actionAt, actionZone) : today;
    if (date < actionDate || (!actionAt && date < today)) throw new ConflictException('La tarea ya estaba en una fecha pasada cuando se creó o editó.');
    if (civilInstant(date, start, zone) >= civilInstant(date, end, zone)) throw new ConflictException('El cambio de hora deja este bloque sin duración efectiva.');
  }

  private async resolveTag(tx: Tx, ownerId: string, name: string) {
    if (!name) return null;
    const normalizedName = name.normalize('NFKC').trim().toLocaleLowerCase('und');
    const [old] = await tx.select({ id: tags.id }).from(tags).where(and(eq(tags.ownerId, ownerId), eq(tags.normalizedName, normalizedName))).limit(1);
    if (old) return old.id;
    const [created] = await tx.insert(tags).values({ ownerId, name, normalizedName }).onConflictDoNothing().returning({ id: tags.id });
    if (created) return created.id;
    const [raced] = await tx.select({ id: tags.id }).from(tags).where(and(eq(tags.ownerId, ownerId), eq(tags.normalizedName, normalizedName))).limit(1);
    if (!raced) throw new ConflictException('No se pudo resolver la etiqueta.');
    return raced.id;
  }

  private async recordEvent(tx: Tx, ownerId: string, task: typeof oneOffTasks.$inferSelect, tagName: string | null, zone: string, changeType: 'UPSERT' | 'DELETE', eventId?: string, effectiveAt = new Date()) {
    await tx.insert(oneOffScheduleEvents).values({ eventId: eventId ?? randomUUID(), ownerId, taskId: task.id, date: task.date,
      effectiveAt, changeType, zoneId: zone, snapshot: { title: task.title, description: task.description,
        tagName, startMinute: task.startMinute, endMinute: task.endMinute } });
  }
}
