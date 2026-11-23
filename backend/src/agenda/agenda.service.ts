import { Injectable } from '@nestjs/common';
import { and, asc, desc, eq, gte, inArray, lt, or } from 'drizzle-orm';
import { InjectDatabase, type Database } from '../database/database.types';
import { completionRecords, occurrenceExceptionEvents, oneOffScheduleEvents, scheduleChangeEvents, templateRevisions, users } from '../database/schema';
import { civilInstant, weekdayBit } from './civil-time';

export interface AgendaOccurrence {
  occurrenceKey: string; sourceType: 'WEEKLY' | 'ONE_OFF'; sourceId: string; date: string;
  title: string; description: string; tagName: string | null; startMinute: number; endMinute: number;
  start: string; end: string; templateId: string | null; revisionId: string | null; sourceEventId: string | null;
  completed: boolean; historical: boolean; originalDate: string; exceptionId?: string; exceptionVersion?: number; exceptionStatus?: string;
}

type Tx = Parameters<Parameters<Database['transaction']>[0]>[0];

@Injectable()
export class AgendaService {
  constructor(@InjectDatabase() private readonly db: Database) {}

  async getDay(ownerId: string, date: string, executor?: Database | Tx): Promise<AgendaOccurrence[]> {
    if (!executor) return this.db.transaction((tx) => this.getDay(ownerId, date, tx), { isolationLevel: 'repeatable read', accessMode: 'read only' });
    const db = executor;
    const [profile] = await db.select({ timeZone: users.timeZone }).from(users).where(eq(users.id, ownerId)).limit(1);
    const zone = profile?.timeZone ?? 'UTC';
    const dayStart = civilInstant(date, 0, zone);
    const dayEnd = civilInstant(date, 1440, zone);
    const prior = await db.select().from(scheduleChangeEvents).where(and(eq(scheduleChangeEvents.ownerId, ownerId), lt(scheduleChangeEvents.effectiveAt, dayStart)))
      .orderBy(desc(scheduleChangeEvents.effectiveAt), desc(scheduleChangeEvents.sequence)).limit(1);
    const sameDay = await db.select().from(scheduleChangeEvents).where(and(eq(scheduleChangeEvents.ownerId, ownerId), gte(scheduleChangeEvents.effectiveAt, dayStart), lt(scheduleChangeEvents.effectiveAt, dayEnd)))
      .orderBy(asc(scheduleChangeEvents.effectiveAt), asc(scheduleChangeEvents.sequence));
    const events = [...prior, ...sameDay];
    const revisionIds = [...new Set(events.map((event) => event.revisionId))];
    const revisions = revisionIds.length ? await db.select().from(templateRevisions).where(and(eq(templateRevisions.ownerId, ownerId), inArray(templateRevisions.id, revisionIds))) : [];
    const revisionMap = new Map(revisions.map((revision) => [revision.id, revision.payload]));
    const weekly: AgendaOccurrence[] = [];
    for (let i = 0; i < events.length; i++) {
      const event = events[i];
      const next = events[i + 1];
      const segmentStart = Math.max(dayStart.getTime(), event.effectiveAt.getTime());
      const segmentEnd = Math.min(dayEnd.getTime(), next?.effectiveAt.getTime() ?? dayEnd.getTime());
      if (segmentStart >= segmentEnd) continue;
      const payload = revisionMap.get(event.revisionId) as { blocks?: Array<Record<string, unknown>> } | undefined;
      for (const block of payload?.blocks ?? []) {
        const daysMask = Number(block.daysMask); const startMinute = Number(block.startMinute); const endMinute = Number(block.endMinute);
        const blockId = String(block.blockId ?? '');
        if (!blockId || !(daysMask & weekdayBit(date)) || startMinute < 0 || startMinute > 1439 || endMinute < 1 || endMinute > 1440 || endMinute <= startMinute) continue;
        const start = Math.max(civilInstant(date, startMinute, event.zoneId).getTime(), segmentStart);
        const end = Math.min(civilInstant(date, endMinute, event.zoneId).getTime(), segmentEnd);
        if (start >= end) continue;
        weekly.push({ occurrenceKey: `weekly:${event.eventId}:${blockId}:${date}`, sourceType: 'WEEKLY', sourceId: blockId,
          date, originalDate: date, title: String(block.title ?? ''), description: String(block.description ?? ''),
          tagName: typeof block.tagName === 'string' ? block.tagName : null, startMinute, endMinute,
          start: new Date(start).toISOString(), end: new Date(end).toISOString(), templateId: event.templateId,
          revisionId: event.revisionId, sourceEventId: event.eventId, completed: false, historical: true });
      }
    }

    const taskIds = db.selectDistinct({ taskId: oneOffScheduleEvents.taskId }).from(oneOffScheduleEvents)
      .where(and(eq(oneOffScheduleEvents.ownerId, ownerId), eq(oneOffScheduleEvents.date, date)));
    const rawOneOff = await db.select().from(oneOffScheduleEvents)
      .where(and(eq(oneOffScheduleEvents.ownerId, ownerId), lt(oneOffScheduleEvents.effectiveAt, dayEnd), inArray(oneOffScheduleEvents.taskId, taskIds)))
      .orderBy(asc(oneOffScheduleEvents.effectiveAt), asc(oneOffScheduleEvents.sequence));
    const oneOff: AgendaOccurrence[] = [];
    const grouped = new Map<string, typeof rawOneOff>();
    for (const event of rawOneOff) grouped.set(event.taskId, [...(grouped.get(event.taskId) ?? []), event]);
    for (const history of grouped.values()) {
      for (let i = 0; i < history.length; i++) {
        const event = history[i];
        if (event.changeType !== 'UPSERT' || event.date !== date) continue;
        const segmentStart = Math.max(dayStart.getTime(), event.effectiveAt.getTime());
        const segmentEnd = Math.min(dayEnd.getTime(), history[i + 1]?.effectiveAt.getTime() ?? dayEnd.getTime());
        if (segmentStart >= segmentEnd) continue;
        const snapshot = event.snapshot as { title?: string; description?: string; tagName?: string | null; startMinute?: number; endMinute?: number };
        const startMinute = Number(snapshot.startMinute); const endMinute = Number(snapshot.endMinute);
        const start = Math.max(civilInstant(event.date, startMinute, event.zoneId).getTime(), segmentStart);
        const end = Math.min(civilInstant(event.date, endMinute, event.zoneId).getTime(), segmentEnd);
        if (start >= end) continue;
        oneOff.push({ occurrenceKey: `oneoff:${event.eventId}:${event.taskId}:${date}`, sourceType: 'ONE_OFF', sourceId: event.taskId,
          date, originalDate: date, title: snapshot.title ?? '', description: snapshot.description ?? '', tagName: snapshot.tagName ?? null,
          startMinute, endMinute, start: new Date(start).toISOString(), end: new Date(end).toISOString(), templateId: null,
          revisionId: null, sourceEventId: event.eventId, completed: false, historical: true });
      }
    }

    const base = [...weekly, ...oneOff];
    const dayRecords = await db.select().from(completionRecords).where(and(eq(completionRecords.ownerId, ownerId), eq(completionRecords.date, date)));
    const exceptionEvents = await db.select().from(occurrenceExceptionEvents)
      .where(and(eq(occurrenceExceptionEvents.ownerId, ownerId), lt(occurrenceExceptionEvents.effectiveAt, dayEnd)))
      .orderBy(asc(occurrenceExceptionEvents.effectiveAt), asc(occurrenceExceptionEvents.sequence));
    const groupedExceptions = new Map<string, typeof exceptionEvents>();
    for (const event of exceptionEvents) groupedExceptions.set(event.exceptionId, [...(groupedExceptions.get(event.exceptionId) ?? []), event]);
    let current = base;
    const additions: AgendaOccurrence[] = [];
    for (const history of groupedExceptions.values()) {
      for (let index = 0; index < history.length; index++) {
        const event = history[index];
        const state = event.snapshot as Record<string, unknown>;
        if (state.action === 'RESTORE') continue;
        const from = Math.max(dayStart.getTime(), event.effectiveAt.getTime());
        const until = Math.min(dayEnd.getTime(), history[index + 1]?.effectiveAt.getTime() ?? dayEnd.getTime());
        if (from >= until) continue;
        current = current.flatMap((task) => {
          if (task.sourceType !== 'WEEKLY' || task.templateId !== state.templateId || task.sourceId !== state.blockId ||
            task.originalDate !== state.originalDate || Date.parse(task.end) <= from || Date.parse(task.start) >= until) return [task];
          const record = dayRecords.find((item) => task.occurrenceKey === item.occurrenceKey || item.occurrenceKey.startsWith(task.occurrenceKey + ':'));
          const segments: AgendaOccurrence[] = [];
          if (Date.parse(task.start) < from) segments.push({ ...task, end: new Date(from).toISOString(),
            completed: !!record && record.completedAt.getTime() < from, historical: true,
            occurrenceKey: `${task.occurrenceKey}:before:${event.sequence}` });
          if (Date.parse(task.end) > until) segments.push({ ...task, start: new Date(until).toISOString(),
            completed: !!record && record.completedAt.getTime() >= until, historical: true,
            occurrenceKey: `${task.occurrenceKey}:after:${event.sequence}` });
          return segments;
        });
        const payload = state.payload as { original?: Record<string, unknown>; replacement?: Record<string, unknown> };
        const original = payload.original ?? {};
        if (state.originalDate === date) {
          const startMinute = Number(original.startMinute); const endMinute = Number(original.endMinute);
          const start = civilInstant(date, startMinute, String(state.zoneId)); const end = civilInstant(date, endMinute, String(state.zoneId));
          additions.push({ occurrenceKey: `exception-origin:${state.id}:${event.sequence}`, sourceType: 'WEEKLY', sourceId: String(state.blockId),
            date, originalDate: String(state.originalDate), title: String(original.title ?? ''), description: String(original.description ?? ''),
            tagName: typeof original.tagName === 'string' ? original.tagName : null, startMinute, endMinute,
            start: start.toISOString(), end: end.toISOString(), templateId: String(state.templateId),
            revisionId: typeof original.revisionId === 'string' ? original.revisionId : null,
            sourceEventId: typeof original.sourceEventId === 'string' ? original.sourceEventId : null, completed: false, historical: true,
            exceptionId: String(state.id), exceptionVersion: Number(state.version), exceptionStatus: String(state.action) });
        }
        if (state.action === 'REPLACE' && state.targetDate === date && payload.replacement) {
          const replacement = payload.replacement;
          const startMinute = Number(replacement.startMinute); const endMinute = Number(replacement.endMinute);
          const start = civilInstant(date, startMinute, String(state.zoneId)); const end = civilInstant(date, endMinute, String(state.zoneId));
          const clippedStart = Math.max(start.getTime(), from); const clippedEnd = Math.min(end.getTime(), until);
          if (clippedStart < clippedEnd) additions.push({ occurrenceKey: index === history.length - 1 ? `exception:${state.id}` : `exception:${state.id}:segment:${event.sequence}`,
            sourceType: 'WEEKLY', sourceId: String(state.blockId), date, originalDate: String(state.originalDate),
            title: String(replacement.title ?? ''), description: String(replacement.description ?? ''),
            tagName: typeof replacement.tagName === 'string' ? replacement.tagName : null, startMinute, endMinute,
            start: new Date(clippedStart).toISOString(), end: new Date(clippedEnd).toISOString(), templateId: String(state.templateId),
            revisionId: typeof original.revisionId === 'string' ? original.revisionId : null,
            sourceEventId: typeof original.sourceEventId === 'string' ? original.sourceEventId : null, completed: false, historical: true,
            exceptionId: String(state.id), exceptionVersion: Number(state.version) });
        }
      }
      const latest = history[history.length - 1].snapshot as Record<string, unknown>;
      if (latest.action === 'RESTORE' && latest.originalDate === date) {
        current = current.map((task) => task.sourceType === 'WEEKLY' && task.templateId === latest.templateId &&
          task.sourceId === latest.blockId && task.originalDate === latest.originalDate
          ? { ...task, exceptionId: String(latest.id), exceptionVersion: Number(latest.version) } : task);
      }
    }
    current.push(...additions);
    const keys = current.map((item) => item.occurrenceKey);
    const records = keys.length ? await db.select().from(completionRecords).where(and(eq(completionRecords.ownerId, ownerId), inArray(completionRecords.occurrenceKey, keys))) : [];
    const completedKeys = new Set(records.map((record) => record.occurrenceKey));
    const agendaWithCompletions = [...current, ...dayRecords.filter((record) => !current.some((item) => item.occurrenceKey === record.occurrenceKey)).map((record): AgendaOccurrence => ({
      occurrenceKey: record.occurrenceKey, sourceType: record.sourceType as 'WEEKLY' | 'ONE_OFF', sourceId: record.sourceId, date, originalDate: date,
      title: record.titleSnapshot, description: '', tagName: null, startMinute: record.startMinuteSnapshot, endMinute: record.endMinuteSnapshot,
      start: civilInstant(date, record.startMinuteSnapshot, zone).toISOString(), end: civilInstant(date, record.endMinuteSnapshot, zone).toISOString(),
      templateId: record.sourceTemplateId, revisionId: record.sourceRevisionId, sourceEventId: record.sourceEventId,
      completed: true, historical: true,
    }))];
    return agendaWithCompletions.map((item) => ({ ...item, completed: item.completed || completedKeys.has(item.occurrenceKey) }))
      .sort((a, b) => a.start.localeCompare(b.start) || a.occurrenceKey.localeCompare(b.occurrenceKey));
  }

  async getWeek(ownerId: string, startDate: string) {
    const [year, month, day] = startDate.split('-').map(Number);
    const monday = new Date(Date.UTC(year, month - 1, day));
    return Promise.all(Array.from({ length: 7 }, (_, offset) => {
      const date = new Date(monday.getTime() + offset * 86_400_000).toISOString().slice(0, 10);
      return this.getDay(ownerId, date).then((occurrences) => ({ date, occurrences }));
    }));
  }
}
