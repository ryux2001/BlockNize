import { BadRequestException, ConflictException, Injectable, NotFoundException, PreconditionFailedException } from '@nestjs/common';
import { and, eq } from 'drizzle-orm';
import { randomUUID } from 'node:crypto';
import { IdempotencyService } from '../common/idempotency.service';
import { completionRecords, occurrenceExceptions, occurrenceExceptionEvents, users } from '../database/schema';
import { AgendaService } from './agenda.service';
import { civilDateAt, civilInstant } from './civil-time';
import { clientActionZone, clientEffectiveAt, type ClientActionMetadata } from '../common/client-action';

export type ExceptionAction = 'SKIP' | 'REPLACE' | 'RESTORE';
export interface ExceptionCommand {
  date: string; occurrenceKey: string; action: ExceptionAction; exceptionId?: string; eventId?: string; targetDate?: string;
  title?: string; description?: string; tagName?: string | null; startMinute?: number; endMinute?: number; clientActionAt?: string; clientTimeZone?: string;
}

@Injectable()
export class OccurrenceActionsService {
  constructor(private readonly agenda: AgendaService, private readonly idempotency: IdempotencyService) {}

  async setCompletion(ownerId: string, date: string, occurrenceKey: string, completed: boolean, idempotencyKey: string, metadata: ClientActionMetadata = {}) {
    const completedAt = clientEffectiveAt(metadata);
    return this.idempotency.run(ownerId, `completion:${occurrenceKey}`, idempotencyKey, { date, occurrenceKey, completed, metadata }, async (tx) => {
      if (!completed) {
        await tx.delete(completionRecords).where(and(eq(completionRecords.ownerId, ownerId), eq(completionRecords.occurrenceKey, occurrenceKey)));
        return { occurrenceKey, completed: false };
      }
      const agenda = await this.agenda.getDay(ownerId, date, tx);
      const occurrence = agenda.find((item) => item.occurrenceKey === occurrenceKey);
      if (!occurrence || occurrence.exceptionStatus) throw new NotFoundException('La ocurrencia ya no está disponible para completar.');
      if (occurrence.completed) return { occurrenceKey, completed: true };
      await tx.insert(completionRecords).values({ ownerId, occurrenceKey, sourceType: occurrence.sourceType,
        sourceId: occurrence.sourceId, date: occurrence.date, completedAt, titleSnapshot: occurrence.title,
        startMinuteSnapshot: occurrence.startMinute, endMinuteSnapshot: occurrence.endMinute,
        sourceTemplateId: occurrence.templateId, sourceRevisionId: occurrence.revisionId, sourceEventId: occurrence.sourceEventId,
      }).onConflictDoNothing();
      return { occurrenceKey, completed: true };
    }, 200);
  }

  async saveException(ownerId: string, command: ExceptionCommand, expectedVersion: number, idempotencyKey: string) {
    return this.idempotency.run(ownerId, `exception:${command.occurrenceKey}`, idempotencyKey, { command, expectedVersion }, async (tx) => {
      const agenda = await this.agenda.getDay(ownerId, command.date, tx);
      const occurrence = agenda.find((item) => item.occurrenceKey === command.occurrenceKey);
      if (!occurrence || occurrence.sourceType !== 'WEEKLY' || !occurrence.templateId || occurrence.completed) {
        throw new ConflictException('La ocurrencia ha cambiado o no admite excepciones. Vuelve a cargar la agenda.');
      }
      const [profile] = await tx.select({ timeZone: users.timeZone }).from(users).where(eq(users.id, ownerId)).limit(1);
      const zone = profile?.timeZone ?? 'UTC';
      const today = civilDateAt(new Date(), zone);
      const effectiveAt = clientEffectiveAt(command);
      const actionZone = clientActionZone(command, zone);
      const actionDate = civilDateAt(effectiveAt, actionZone);
      if (occurrence.originalDate < actionDate || occurrence.date < actionDate ||
        (!command.clientActionAt && (occurrence.originalDate < today || occurrence.date < today))) {
        throw new ConflictException('La ocurrencia ya estaba en una fecha pasada cuando se registró la acción.');
      }

      const existingId = occurrence.exceptionId;
      const [previousById] = existingId ? await tx.select().from(occurrenceExceptions).where(and(eq(occurrenceExceptions.id, existingId), eq(occurrenceExceptions.ownerId, ownerId))).limit(1) : [];
      const [previousByOrigin] = previousById ? [previousById] : await tx.select().from(occurrenceExceptions).where(and(
        eq(occurrenceExceptions.ownerId, ownerId), eq(occurrenceExceptions.templateId, occurrence.templateId!),
        eq(occurrenceExceptions.blockId, occurrence.sourceId), eq(occurrenceExceptions.originalDate, occurrence.originalDate))).limit(1);
      const previous = previousByOrigin;
      if (command.action === 'RESTORE') {
        if (!occurrence.occurrenceKey.startsWith('exception-origin:') || !previous || previous.action === 'RESTORE') {
          throw new ConflictException('No hay una excepción activa que restaurar.');
        }
      } else if (occurrence.occurrenceKey.startsWith('exception-origin:')) {
        throw new ConflictException('Abre la ocurrencia reprogramada para cambiar su excepción.');
      }
      if ((previous?.version ?? 0) !== expectedVersion) throw new PreconditionFailedException({ code: 'STALE_VERSION', message: 'La excepción cambió; vuelve a cargarla.', currentVersion: previous?.version ?? 0 });

      let targetDate = command.targetDate ?? occurrence.date;
      const priorPayload = previous?.payload as { original?: Record<string, unknown> } | undefined;
      const original = priorPayload?.original ?? { title: occurrence.title, description: occurrence.description,
        tagName: occurrence.tagName, startMinute: occurrence.startMinute, endMinute: occurrence.endMinute,
        revisionId: occurrence.revisionId, sourceEventId: occurrence.sourceEventId };
      let payload: Record<string, unknown> = { original };
      if (command.action === 'REPLACE') {
        if (!command.targetDate || !command.title?.trim() || command.startMinute === undefined || command.endMinute === undefined || command.startMinute >= command.endMinute) {
          throw new BadRequestException('La sustitución requiere destino, título y un horario válido que no cruce medianoche.');
        }
        if (command.targetDate < actionDate || (!command.clientActionAt && command.targetDate < today)) throw new BadRequestException('El destino ya estaba en una fecha pasada cuando se registró la acción.');
        const actionNow = command.clientActionAt ? effectiveAt : new Date();
        if (command.targetDate === actionDate && civilInstant(command.targetDate, command.startMinute, actionZone) < actionNow) {
          throw new BadRequestException('No se puede reprogramar hacia una hora que ya pasó.');
        }
        targetDate = command.targetDate;
        payload = { original, replacement: { title: command.title.trim(), description: command.description?.trim() ?? '',
          tagName: command.tagName?.trim() || null, startMinute: command.startMinute, endMinute: command.endMinute } };
      }

      const now = effectiveAt;
      const values = { ...(previous ? {} : { id: command.exceptionId ?? randomUUID() }), ownerId, templateId: occurrence.templateId, blockId: occurrence.sourceId,
        originalDate: occurrence.originalDate, targetDate, action: command.action, payload,
        zoneId: previous?.zoneId ?? zone, version: (previous?.version ?? 0) + 1, modifiedAt: now };
      let saved;
      if (previous) {
        [saved] = await tx.update(occurrenceExceptions).set(values).where(and(eq(occurrenceExceptions.id, previous.id), eq(occurrenceExceptions.ownerId, ownerId), eq(occurrenceExceptions.version, expectedVersion))).returning();
        if (!saved) throw new PreconditionFailedException('La excepción cambió; vuelve a cargarla.');
      } else {
        [saved] = await tx.insert(occurrenceExceptions).values(values).returning();
      }
      await tx.insert(occurrenceExceptionEvents).values({ eventId: command.eventId ?? randomUUID(), ownerId, exceptionId: saved.id,
        effectiveAt: now, snapshot: { id: saved.id, templateId: saved.templateId, blockId: saved.blockId,
          originalDate: saved.originalDate, targetDate: saved.targetDate, action: saved.action, payload: saved.payload,
          zoneId: saved.zoneId, version: saved.version, createdAt: saved.createdAt, modifiedAt: saved.modifiedAt } });
      return saved;
    }, 200);
  }
}
