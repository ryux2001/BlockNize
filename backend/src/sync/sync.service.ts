import { BadRequestException, ConflictException, Injectable } from '@nestjs/common';
import { and, asc, eq, gt, sql } from 'drizzle-orm';
import { InjectDatabase, type Database } from '../database/database.types';
import {
  activeSelections, completionRecords, occurrenceExceptionEvents, occurrenceExceptions,
  oneOffScheduleEvents, oneOffTasks, scheduleBlocks, scheduleChangeEvents, syncCommits,
  syncHeads, tags, taskDefinitions, templateRevisions, templates, users,
} from '../database/schema';
import { IdempotencyService } from '../common/idempotency.service';
import { SyncImportDto } from './sync-import.dto';
import { randomUUID } from 'node:crypto';

type ImportRow = Record<string, unknown>;
const IMPORT_TABLES = ['routine_templates', 'tags', 'task_definitions', 'schedule_blocks', 'template_revisions',
  'schedule_change_events', 'active_template_selection', 'one_off_tasks', 'one_off_schedule_events',
  'completion_records', 'occurrence_exceptions', 'occurrence_exception_events'] as const;

function object(value: unknown, label: string): ImportRow {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new BadRequestException(`${label} debe ser un objeto.`);
  return value as ImportRow;
}
function rows(data: Record<string, unknown>, table: typeof IMPORT_TABLES[number]): ImportRow[] {
  const value = data[table];
  if (!Array.isArray(value)) throw new BadRequestException(`Falta la tabla ${table}.`);
  return value.map((row, index) => object(row, `${table}[${index}]`));
}
function str(row: ImportRow, key: string, max = 4000): string {
  const value = row[key];
  if (typeof value !== 'string' || value.length > max) throw new BadRequestException(`Campo ${key} no válido.`);
  return value;
}
function nullableStr(row: ImportRow, key: string): string | null {
  const value = row[key];
  if (value === null || value === undefined) return null;
  if (typeof value !== 'string') throw new BadRequestException(`Campo ${key} no válido.`);
  return value;
}
function num(row: ImportRow, key: string, min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER): number {
  const value = row[key];
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < min || value > max) throw new BadRequestException(`Campo ${key} no válido.`);
  return value;
}
function uuid(row: ImportRow, key: string, optional = false): string | null {
  const value = row[key];
  if ((value === null || value === undefined) && optional) return null;
  if (typeof value !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
    throw new BadRequestException(`Identificador ${key} no válido.`);
  }
  return value;
}
function jsonObject(row: ImportRow, key: string): Record<string, unknown> {
  const value = row[key];
  if (typeof value !== 'string') throw new BadRequestException(`Campo ${key} no válido.`);
  try {
    const parsed: unknown = JSON.parse(value);
    return object(parsed, key);
  } catch (error) {
    if (error instanceof BadRequestException) throw error;
    throw new BadRequestException(`Campo ${key} contiene JSON no válido.`);
  }
}
function instant(row: ImportRow, key: string): Date {
  return new Date(num(row, key));
}
function civilDate(row: ImportRow, key: string): string {
  const epochDay = num(row, key, -365_000, 365_000);
  return new Date(epochDay * 86_400_000).toISOString().slice(0, 10);
}
function interval(row: ImportRow, startKey = 'startMinute', endKey = 'endMinute') {
  const startMinute = num(row, startKey, 0, 1439);
  const endMinute = num(row, endKey, 1, 1440);
  if (startMinute >= endMinute) throw new BadRequestException('La copia contiene un intervalo horario no válido.');
  return { startMinute, endMinute };
}

@Injectable()
export class SyncService {
  constructor(@InjectDatabase() private readonly db: Database, private readonly idempotency: IdempotencyService) {}

  async importLocalData(ownerId: string, input: SyncImportDto, idempotencyKey: string) {
    if (input.formatVersion !== 1) throw new BadRequestException('Versión de importación local no compatible.');
    const counts = Object.fromEntries(IMPORT_TABLES.map((table) => [table, rows(input.data, table).length]));
    const totalRows = Object.values(counts).reduce((total, count) => total + count, 0);
    if (totalRows > 100_000) throw new BadRequestException('La importación supera el límite de 100.000 registros.');

    return this.idempotency.run(ownerId, 'sync:initial-import', idempotencyKey, input, async (tx) => {
      const [priorHead] = await tx.select({ revision: syncHeads.revision }).from(syncHeads).where(eq(syncHeads.ownerId, ownerId)).limit(1);
      if (priorHead && priorHead.revision > 0) throw new ConflictException({ code: 'ACCOUNT_NOT_EMPTY', message: 'La cuenta ya tiene actividad; no se importó ningún dato local.' });
      for (const table of [templates, tags, taskDefinitions, scheduleBlocks, templateRevisions, scheduleChangeEvents,
        activeSelections, oneOffTasks, oneOffScheduleEvents, completionRecords, occurrenceExceptions, occurrenceExceptionEvents]) {
        const [existing] = await tx.select({ ownerId: table.ownerId }).from(table).where(eq(table.ownerId, ownerId)).limit(1);
        if (existing) throw new ConflictException({ code: 'ACCOUNT_NOT_EMPTY', message: 'La cuenta ya contiene datos; no se importó ningún dato local.' });
      }

      const localTemplates = rows(input.data, 'routine_templates').map((row) => ({
        id: uuid(row, 'id')!, ownerId, name: str(row, 'name', 120), createdAt: instant(row, 'createdAt'), updatedAt: instant(row, 'updatedAt'),
      }));
      const localTags = rows(input.data, 'tags').map((row) => {
        const name = str(row, 'name', 80).trim();
        if (!name) throw new BadRequestException('La copia contiene una etiqueta vacía.');
        return { id: uuid(row, 'id')!, ownerId, name, normalizedName: name.normalize('NFKC').toLocaleLowerCase('und') };
      });
      const localTasks = rows(input.data, 'task_definitions').map((row) => ({
        id: uuid(row, 'id')!, ownerId, templateId: uuid(row, 'templateId')!, title: str(row, 'title', 160),
        description: str(row, 'description', 4000), tagId: uuid(row, 'tagId', true),
      }));
      const localBlocks = rows(input.data, 'schedule_blocks').map((row) => ({
        id: uuid(row, 'id')!, ownerId, templateId: uuid(row, 'templateId')!, taskId: uuid(row, 'taskId')!,
        daysMask: num(row, 'daysMask', 1, 127), ...interval(row),
      }));
      const localActive = rows(input.data, 'active_template_selection');
      if (localActive.length > 1) throw new BadRequestException('La copia contiene más de una selección activa.');
      const localEvents = rows(input.data, 'schedule_change_events').map((row) => ({
        sequence: num(row, 'sequence', 1), eventId: uuid(row, 'eventId')!, ownerId, effectiveAt: instant(row, 'effectiveAt'),
        previousTemplateId: uuid(row, 'previousTemplateId', true), templateId: uuid(row, 'templateId')!,
        revisionId: uuid(row, 'revisionId')!, zoneId: str(row, 'zoneId', 100),
        changeType: str(row, 'changeType', 32) as 'ACTIVATION' | 'ACTIVE_EDIT',
      }));
      const revisionVersions = new Map<string, number>();
      const revisionsByTemplate = new Map<string, Set<string>>();
      for (const event of [...localEvents].sort((a, b) => a.sequence - b.sequence)) {
        const current = revisionsByTemplate.get(event.templateId) ?? new Set<string>();
        if (!current.has(event.revisionId)) {
          current.add(event.revisionId);
          revisionVersions.set(event.revisionId, current.size);
          revisionsByTemplate.set(event.templateId, current);
        }
      }
      const localRevisions = rows(input.data, 'template_revisions').map((row) => {
        const id = uuid(row, 'id')!;
        const templateId = uuid(row, 'templateId')!;
        const current = revisionsByTemplate.get(templateId) ?? new Set<string>();
        if (!current.has(id)) {
          current.add(id);
          revisionVersions.set(id, current.size);
          revisionsByTemplate.set(templateId, current);
        }
        return { id, ownerId, templateId, version: revisionVersions.get(id)!, createdAt: instant(row, 'createdAt'),
          payload: jsonObject(row, 'payloadJson') as typeof templateRevisions.$inferInsert.payload };
      });
      const rawOneOffEvents = rows(input.data, 'one_off_schedule_events');
      const localOneOffTasks: (typeof oneOffTasks.$inferInsert)[] = rows(input.data, 'one_off_tasks').map((row) => ({
        id: uuid(row, 'id')!, ownerId, date: civilDate(row, 'dateEpochDay'), title: str(row, 'title', 160),
        description: str(row, 'description', 4000), tagId: uuid(row, 'tagId', true), ...interval(row),
      }));
      const knownOneOffIds = new Set(localOneOffTasks.map((row) => row.id));
      for (const row of rawOneOffEvents) {
        const id = uuid(row, 'taskId')!;
        if (knownOneOffIds.has(id)) continue;
        const startMinute = num(row, 'startMinute', 0, 1439);
        const endMinute = num(row, 'endMinute', 1, 1440);
        if (startMinute >= endMinute) throw new BadRequestException('La copia contiene un evento temporal no válido.');
        localOneOffTasks.push({ id, ownerId, date: civilDate(row, 'dateEpochDay'), title: str(row, 'title', 160),
          description: str(row, 'description', 4000), tagId: null, startMinute, endMinute,
          version: 1, updatedAt: instant(row, 'effectiveAt'), deletedAt: new Date() });
        knownOneOffIds.add(id);
      }
      const localOneOffEvents = rawOneOffEvents.map((row) => ({
        sequence: num(row, 'sequence', 1), eventId: uuid(row, 'eventId')!, ownerId, taskId: uuid(row, 'taskId')!,
        date: civilDate(row, 'dateEpochDay'), effectiveAt: instant(row, 'effectiveAt'), changeType: str(row, 'changeType', 16) as 'UPSERT' | 'DELETE',
        snapshot: { title: str(row, 'title', 160), description: str(row, 'description', 4000), tagName: nullableStr(row, 'tagName'), ...interval(row),
          zoneId: str(row, 'zoneId', 100) }, zoneId: str(row, 'zoneId', 100),
      }));
      const localCompletions = rows(input.data, 'completion_records').map((row) => {
        const time = interval(row, 'startMinuteSnapshot', 'endMinuteSnapshot');
        return { ownerId, occurrenceKey: str(row, 'occurrenceKey', 600), sourceType: str(row, 'sourceType', 16) as 'WEEKLY' | 'ONE_OFF',
          sourceId: uuid(row, 'sourceId')!, date: civilDate(row, 'dateEpochDay'), completedAt: instant(row, 'completedAt'),
          titleSnapshot: str(row, 'titleSnapshot', 160), startMinuteSnapshot: time.startMinute, endMinuteSnapshot: time.endMinute,
          sourceTemplateId: uuid(row, 'sourceTemplateId', true), sourceRevisionId: uuid(row, 'sourceRevisionId', true),
          sourceEventId: uuid(row, 'sourceEventId', true) };
      });
      const rawExceptionEvents = rows(input.data, 'occurrence_exception_events');
      const exceptionVersions = new Map<string, number>();
      for (const row of [...rawExceptionEvents].sort((a, b) => num(a, 'sequence', 1) - num(b, 'sequence', 1))) {
        const exceptionId = uuid(row, 'exceptionId')!;
        exceptionVersions.set(exceptionId, (exceptionVersions.get(exceptionId) ?? 0) + 1);
      }
      const localExceptions = rows(input.data, 'occurrence_exceptions').map((row) => ({
        id: uuid(row, 'id')!, ownerId, templateId: uuid(row, 'templateId')!, blockId: uuid(row, 'blockId')!,
        originalDate: civilDate(row, 'originalEpochDay'), targetDate: civilDate(row, 'targetEpochDay'),
        action: str(row, 'action', 16) as 'SKIP' | 'REPLACE' | 'RESTORE',
        payload: jsonObject(row, 'payloadJson'), zoneId: str(row, 'zoneId', 100), version: exceptionVersions.get(str(row, 'id')) ?? 1,
        createdAt: instant(row, 'createdAt'), modifiedAt: instant(row, 'modifiedAt'),
      }));
      const eventVersionBySequence = new Map<number, number>();
      const eventVersions = new Map<string, number>();
      for (const row of [...rawExceptionEvents].sort((a, b) => num(a, 'sequence', 1) - num(b, 'sequence', 1))) {
        const exceptionId = uuid(row, 'exceptionId')!;
        const next = (eventVersions.get(exceptionId) ?? 0) + 1;
        eventVersions.set(exceptionId, next);
        eventVersionBySequence.set(num(row, 'sequence', 1), next);
      }
      const localExceptionEvents = rawExceptionEvents.map((row) => {
        const state: ImportRow = {
          id: uuid(row, 'state_id')!, templateId: uuid(row, 'state_templateId')!, blockId: uuid(row, 'state_blockId')!,
          originalEpochDay: num(row, 'state_originalEpochDay'), targetEpochDay: num(row, 'state_targetEpochDay'),
          action: str(row, 'state_action', 16), payloadJson: str(row, 'state_payloadJson'), zoneId: str(row, 'state_zoneId', 100),
          createdAt: num(row, 'state_createdAt'), modifiedAt: num(row, 'state_modifiedAt'),
        };
        const sequence = num(row, 'sequence', 1);
        return { sequence, eventId: nullableStr(row, 'eventId') ?? randomUUID(), ownerId,
          exceptionId: uuid(row, 'exceptionId')!, effectiveAt: instant(row, 'effectiveAt'), snapshot: {
            id: state.id, templateId: state.templateId, blockId: state.blockId,
            originalDate: civilDate(state, 'originalEpochDay'), targetDate: civilDate(state, 'targetEpochDay'),
            action: state.action, payload: jsonObject(state, 'payloadJson'), zoneId: state.zoneId,
            version: eventVersionBySequence.get(sequence) ?? 1, createdAt: new Date(state.createdAt as number), modifiedAt: new Date(state.modifiedAt as number),
          } };
      });

      if (localTemplates.length) await tx.insert(templates).values(localTemplates);
      if (localTags.length) await tx.insert(tags).values(localTags);
      if (localTasks.length) await tx.insert(taskDefinitions).values(localTasks);
      if (localBlocks.length) await tx.insert(scheduleBlocks).values(localBlocks);
      if (localRevisions.length) await tx.insert(templateRevisions).values(localRevisions);
      if (localActive.length) {
        const row = localActive[0];
        await tx.insert(activeSelections).values({ ownerId, templateId: uuid(row, 'templateId')!, revisionId: uuid(row, 'revisionId')!, effectiveAt: instant(row, 'effectiveAt'), version: Math.max(1, localEvents.length) });
      }
      if (localEvents.length) await tx.insert(scheduleChangeEvents).values(localEvents);
      if (localOneOffTasks.length) await tx.insert(oneOffTasks).values(localOneOffTasks);
      if (localOneOffEvents.length) await tx.insert(oneOffScheduleEvents).values(localOneOffEvents);
      if (localCompletions.length) await tx.insert(completionRecords).values(localCompletions);
      if (localExceptions.length) await tx.insert(occurrenceExceptions).values(localExceptions);
      if (localExceptionEvents.length) await tx.insert(occurrenceExceptionEvents).values(localExceptionEvents);

      for (const [table, count] of [['schedule_change_events', localEvents.length], ['one_off_schedule_events', localOneOffEvents.length], ['occurrence_exception_events', localExceptionEvents.length]] as const) {
        if (count > 0) await tx.execute(sql`SELECT setval(pg_get_serial_sequence(${table}, 'sequence'), (SELECT MAX(sequence) FROM ${sql.raw(table)}), true)`);
      }
      return { imported: true, importId: input.importId, counts };
    }, 201);
  }

  async state(ownerId: string) {
    const [head] = await this.db.select({ revision: syncHeads.revision })
      .from(syncHeads).where(eq(syncHeads.ownerId, ownerId)).limit(1);
    return { cursor: String(head?.revision ?? 0), protocolVersion: 1 };
  }

  async bootstrap(ownerId: string) {
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY`);
      const [head] = await tx.select({ revision: syncHeads.revision })
        .from(syncHeads).where(eq(syncHeads.ownerId, ownerId)).limit(1);
      const [profile] = await tx.execute(sql`SELECT time_zone AS "timeZone" FROM users WHERE id = ${ownerId} LIMIT 1`)
        .then((result) => result.rows as Array<{ timeZone: string }>);
      const rows = {
        templates: await tx.select().from(templates).where(eq(templates.ownerId, ownerId)),
        tags: await tx.select().from(tags).where(eq(tags.ownerId, ownerId)),
        taskDefinitions: await tx.select().from(taskDefinitions).where(eq(taskDefinitions.ownerId, ownerId)),
        scheduleBlocks: await tx.select().from(scheduleBlocks).where(eq(scheduleBlocks.ownerId, ownerId)),
        templateRevisions: await tx.select().from(templateRevisions).where(eq(templateRevisions.ownerId, ownerId)),
        activeSelection: await tx.select().from(activeSelections).where(eq(activeSelections.ownerId, ownerId)).limit(1),
        scheduleChangeEvents: await tx.select().from(scheduleChangeEvents).where(eq(scheduleChangeEvents.ownerId, ownerId)).orderBy(asc(scheduleChangeEvents.sequence)),
        oneOffTasks: await tx.select().from(oneOffTasks).where(eq(oneOffTasks.ownerId, ownerId)),
        oneOffScheduleEvents: await tx.select().from(oneOffScheduleEvents).where(eq(oneOffScheduleEvents.ownerId, ownerId)).orderBy(asc(oneOffScheduleEvents.sequence)),
        completions: await tx.select().from(completionRecords).where(eq(completionRecords.ownerId, ownerId)),
        occurrenceExceptions: await tx.select().from(occurrenceExceptions).where(eq(occurrenceExceptions.ownerId, ownerId)),
        occurrenceExceptionEvents: await tx.select().from(occurrenceExceptionEvents).where(eq(occurrenceExceptionEvents.ownerId, ownerId)).orderBy(asc(occurrenceExceptionEvents.sequence)),
      };
      return { protocolVersion: 1, cursor: String(head?.revision ?? 0), timeZone: profile?.timeZone ?? 'UTC', data: rows };
    });
  }

  async changes(ownerId: string, rawCursor: string | undefined, rawLimit: string | undefined) {
    const cursor = rawCursor === undefined ? 0 : Number(rawCursor);
    const limit = rawLimit === undefined ? 100 : Number(rawLimit);
    if (!Number.isSafeInteger(cursor) || cursor < 0) throw new BadRequestException('El cursor debe ser un entero no negativo.');
    if (!Number.isInteger(limit) || limit < 1 || limit > 500) throw new BadRequestException('El límite debe estar entre 1 y 500.');

    const [head] = await this.db.select({ revision: syncHeads.revision })
      .from(syncHeads).where(eq(syncHeads.ownerId, ownerId)).limit(1);
    const currentRevision = head?.revision ?? 0;
    const rows = await this.db.select({
      revision: syncCommits.revision,
      operation: syncCommits.operation,
      committedAt: syncCommits.committedAt,
    }).from(syncCommits).where(and(eq(syncCommits.ownerId, ownerId), gt(syncCommits.revision, cursor)))
      .orderBy(asc(syncCommits.revision)).limit(limit + 1);
    const hasMore = rows.length > limit;
    const changes = rows.slice(0, limit);
    const nextCursor = changes.length ? changes[changes.length - 1].revision : cursor;
    return {
      protocolVersion: 1,
      cursor: String(cursor),
      currentRevision: String(currentRevision),
      nextCursor: String(nextCursor),
      hasMore,
      changes: changes.map((row) => ({ revision: String(row.revision), operation: row.operation, committedAt: row.committedAt })),
    };
  }
}
