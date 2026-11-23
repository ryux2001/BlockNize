import { ConflictException, Injectable, NotFoundException, PreconditionFailedException } from '@nestjs/common';
import { and, asc, desc, eq, isNull, max } from 'drizzle-orm';
import { randomUUID } from 'node:crypto';
import { IdempotencyService } from '../common/idempotency.service';
import { InjectDatabase, type Database } from '../database/database.types';
import { activeSelections, scheduleBlocks, scheduleChangeEvents, tags, taskDefinitions, templateRevisions, templates, users } from '../database/schema';
import { clientEffectiveAt, type ClientActionMetadata } from '../common/client-action';

type Tx = Parameters<Parameters<Database['transaction']>[0]>[0];

@Injectable()
export class TemplatesService {
  constructor(@InjectDatabase() private readonly db: Database, private readonly idempotency: IdempotencyService) {}

  async list(ownerId: string) {
    const [rows, active] = await Promise.all([
      this.db.select().from(templates).where(and(eq(templates.ownerId, ownerId), isNull(templates.deletedAt))).orderBy(asc(templates.name)),
      this.db.select({ templateId: activeSelections.templateId }).from(activeSelections).where(eq(activeSelections.ownerId, ownerId)).limit(1),
    ]);
    return rows.map((row) => ({ ...row, active: active[0]?.templateId === row.id }));
  }

  async get(ownerId: string, id: string) {
    const template = await this.requireTemplate(this.db, ownerId, id);
    return { ...template, blocks: await this.readBlocks(this.db, ownerId, id) };
  }

  async create(ownerId: string, name: string, key: string, id?: string, metadata: ClientActionMetadata = {}) {
    return this.idempotency.run(ownerId, 'template:create', key, { id: id ?? null, name, metadata }, async (tx) => {
      const effectiveAt = clientEffectiveAt(metadata);
      const [created] = await tx.insert(templates).values({ ...(id ? { id } : {}), ownerId, name: name.trim(), createdAt: effectiveAt, updatedAt: effectiveAt }).returning();
      return { ...created, blocks: [] };
    }, 201);
  }

  async rename(ownerId: string, id: string, version: number, name: string, key: string, syncIds?: { revisionId?: string; eventId?: string } & ClientActionMetadata) {
    return this.idempotency.run(ownerId, `template:rename:${id}`, key, { id, version, name, syncIds: syncIds ?? null }, async (tx) => {
      const current = await this.requireTemplate(tx, ownerId, id);
      this.assertVersion(current.version, version);
      const cleanName = name.trim();
      if (current.name === cleanName) return { ...current, blocks: await this.readBlocks(tx, ownerId, id) };
      const now = clientEffectiveAt(syncIds ?? {});
      const [updated] = await tx.update(templates).set({ name: cleanName, version: current.version + 1, updatedAt: now })
        .where(and(eq(templates.id, id), eq(templates.ownerId, ownerId), eq(templates.version, version))).returning();
      if (!updated) throw new ConflictException('La plantilla cambió; vuelve a cargarla.');
      const [active] = await tx.select().from(activeSelections).where(eq(activeSelections.ownerId, ownerId)).limit(1);
      if (active?.templateId === id) await this.recordActiveEdit(tx, ownerId, updated, now, active, syncIds);
      return { ...updated, blocks: await this.readBlocks(tx, ownerId, id) };
    });
  }

  async saveBlock(ownerId: string, templateId: string, version: number, input: {
    id?: string; taskId?: string; revisionId?: string; eventId?: string; title: string; description?: string; tagName?: string; daysMask: number; startMinute: number; endMinute: number;
  } & ClientActionMetadata, key: string, create = false) {
    if (input.startMinute >= input.endMinute) throw new ConflictException('El inicio debe ser anterior al fin; no se admiten bloques que crucen medianoche.');
    return this.idempotency.run(ownerId, `template:block:${templateId}:${input.id ?? 'create'}`, key, input, async (tx) => {
      const template = await this.requireTemplate(tx, ownerId, templateId);
      this.assertVersion(template.version, version);
      const existing = input.id ? (await tx.select().from(scheduleBlocks).where(and(eq(scheduleBlocks.id, input.id), eq(scheduleBlocks.ownerId, ownerId), eq(scheduleBlocks.templateId, templateId), isNull(scheduleBlocks.deletedAt))).limit(1))[0] : undefined;
      if (!create && input.id && !existing) throw new NotFoundException('No se encontró el bloque.');
      if (create && existing) throw new ConflictException('Ya existe un bloque con ese ID.');
      const prior = create ? undefined : existing;
      const tagId = await this.resolveTag(tx, ownerId, input.tagName?.trim() ?? '');
      const title = input.title.trim();
      const description = input.description?.trim() ?? '';
      if (prior) {
        const [oldTask] = await tx.select().from(taskDefinitions).where(and(eq(taskDefinitions.id, prior.taskId), eq(taskDefinitions.ownerId, ownerId))).limit(1);
        if (oldTask && oldTask.title === title && oldTask.description === description && oldTask.tagId === tagId &&
          prior.daysMask === input.daysMask && prior.startMinute === input.startMinute && prior.endMinute === input.endMinute) {
          return { ...template, blocks: await this.readBlocks(tx, ownerId, templateId) };
        }
      }
      const now = clientEffectiveAt(input);
      if (prior) {
        await tx.update(taskDefinitions).set({ title, description, tagId }).where(and(eq(taskDefinitions.id, prior.taskId), eq(taskDefinitions.ownerId, ownerId)));
        await tx.update(scheduleBlocks).set({ daysMask: input.daysMask, startMinute: input.startMinute, endMinute: input.endMinute })
          .where(and(eq(scheduleBlocks.id, prior.id), eq(scheduleBlocks.ownerId, ownerId)));
      } else {
        const taskId = input.taskId ?? randomUUID();
        await tx.insert(taskDefinitions).values({ id: taskId, ownerId, templateId, title, description, tagId });
        await tx.insert(scheduleBlocks).values({ id: input.id ?? randomUUID(), ownerId, templateId, taskId, daysMask: input.daysMask, startMinute: input.startMinute, endMinute: input.endMinute });
      }
      const [updated] = await tx.update(templates).set({ version: template.version + 1, updatedAt: now })
        .where(and(eq(templates.id, templateId), eq(templates.ownerId, ownerId), eq(templates.version, version))).returning();
      if (!updated) throw new ConflictException('La plantilla cambió; vuelve a cargarla.');
      const [active] = await tx.select().from(activeSelections).where(eq(activeSelections.ownerId, ownerId)).limit(1);
      if (active?.templateId === templateId) await this.recordActiveEdit(tx, ownerId, updated, now, active, input);
      return { ...updated, blocks: await this.readBlocks(tx, ownerId, templateId) };
    }, input.id ? 200 : 201);
  }

  async deleteBlock(ownerId: string, templateId: string, blockId: string, version: number, key: string, syncIds?: { revisionId?: string; eventId?: string } & ClientActionMetadata) {
    return this.idempotency.run(ownerId, `template:block:delete:${templateId}:${blockId}`, key, { templateId, blockId, version, syncIds: syncIds ?? null }, async (tx) => {
      const template = await this.requireTemplate(tx, ownerId, templateId);
      this.assertVersion(template.version, version);
      const [block] = await tx.select().from(scheduleBlocks).where(and(eq(scheduleBlocks.id, blockId), eq(scheduleBlocks.ownerId, ownerId), eq(scheduleBlocks.templateId, templateId), isNull(scheduleBlocks.deletedAt))).limit(1);
      if (!block) throw new NotFoundException('No se encontró el bloque.');
      await tx.delete(scheduleBlocks).where(eq(scheduleBlocks.id, blockId));
      await tx.delete(taskDefinitions).where(and(eq(taskDefinitions.id, block.taskId), eq(taskDefinitions.ownerId, ownerId)));
      const now = clientEffectiveAt(syncIds ?? {});
      const [updated] = await tx.update(templates).set({ version: template.version + 1, updatedAt: now })
        .where(and(eq(templates.id, templateId), eq(templates.ownerId, ownerId), eq(templates.version, version))).returning();
      if (!updated) throw new ConflictException('La plantilla cambió; vuelve a cargarla.');
      const [active] = await tx.select().from(activeSelections).where(eq(activeSelections.ownerId, ownerId)).limit(1);
      if (active?.templateId === templateId) await this.recordActiveEdit(tx, ownerId, updated, now, active, syncIds);
      return { ...updated, blocks: await this.readBlocks(tx, ownerId, templateId) };
    });
  }

  async activate(ownerId: string, templateId: string, expectedVersion: number, timeZone: string, key: string, syncIds?: { revisionId?: string; eventId?: string } & ClientActionMetadata) {
    return this.idempotency.run(ownerId, 'active-selection:update', key, { templateId, expectedVersion, syncIds: syncIds ?? null }, async (tx) => {
      const target = await this.requireTemplate(tx, ownerId, templateId);
      const [prior] = await tx.select().from(activeSelections).where(eq(activeSelections.ownerId, ownerId)).limit(1);
      const actualVersion = prior?.version ?? 0;
      this.assertVersion(actualVersion, expectedVersion);
      if (prior?.templateId === templateId) return { templateId, revisionId: prior.revisionId, version: prior.version, effectiveAt: prior.effectiveAt };
      const now = clientEffectiveAt(syncIds ?? {});
      const revisionId = await this.createRevision(tx, ownerId, target, syncIds?.revisionId, now);
      const nextVersion = actualVersion + 1;
      await tx.insert(activeSelections).values({ ownerId, templateId, revisionId, effectiveAt: now, version: nextVersion })
        .onConflictDoUpdate({ target: activeSelections.ownerId, set: { templateId, revisionId, effectiveAt: now, version: nextVersion } });
      await tx.insert(scheduleChangeEvents).values({ eventId: syncIds?.eventId ?? randomUUID(), ownerId, effectiveAt: now,
        previousTemplateId: prior?.templateId ?? null, templateId, revisionId, zoneId: timeZone, changeType: 'ACTIVATION' });
      return { templateId, revisionId, version: nextVersion, effectiveAt: now };
    });
  }

  async active(ownerId: string) {
    const [selection] = await this.db.select().from(activeSelections).where(eq(activeSelections.ownerId, ownerId)).limit(1);
    if (!selection) return null;
    const [revision] = await this.db.select().from(templateRevisions).where(and(eq(templateRevisions.id, selection.revisionId), eq(templateRevisions.ownerId, ownerId))).limit(1);
    return { ...selection, snapshot: revision?.payload ?? null };
  }

  async revisions(ownerId: string, templateId: string) {
    await this.requireTemplate(this.db, ownerId, templateId);
    return this.db.select().from(templateRevisions).where(and(eq(templateRevisions.ownerId, ownerId), eq(templateRevisions.templateId, templateId))).orderBy(desc(templateRevisions.version));
  }

  async revision(ownerId: string, templateId: string, revisionId: string) {
    const [row] = await this.db.select().from(templateRevisions).where(and(eq(templateRevisions.ownerId, ownerId), eq(templateRevisions.templateId, templateId), eq(templateRevisions.id, revisionId))).limit(1);
    if (!row) throw new NotFoundException('No se encontró la revisión.');
    return row;
  }

  private async requireTemplate(db: Database | Tx, ownerId: string, id: string) {
    const [row] = await db.select().from(templates).where(and(eq(templates.id, id), eq(templates.ownerId, ownerId), isNull(templates.deletedAt))).limit(1);
    if (!row) throw new NotFoundException('No se encontró la plantilla.');
    return row;
  }

  private async readBlocks(db: Database | Tx, ownerId: string, templateId: string) {
    return db.select({ id: scheduleBlocks.id, taskId: taskDefinitions.id, templateId: scheduleBlocks.templateId,
      title: taskDefinitions.title, description: taskDefinitions.description, tagId: tags.id, tagName: tags.name,
      daysMask: scheduleBlocks.daysMask, startMinute: scheduleBlocks.startMinute, endMinute: scheduleBlocks.endMinute })
      .from(scheduleBlocks).innerJoin(taskDefinitions, and(eq(taskDefinitions.id, scheduleBlocks.taskId), eq(taskDefinitions.ownerId, ownerId)))
      .leftJoin(tags, and(eq(tags.id, taskDefinitions.tagId), eq(tags.ownerId, ownerId)))
      .where(and(eq(scheduleBlocks.ownerId, ownerId), eq(scheduleBlocks.templateId, templateId), isNull(scheduleBlocks.deletedAt)))
      .orderBy(asc(scheduleBlocks.startMinute), asc(scheduleBlocks.id));
  }

  private async resolveTag(tx: Tx, ownerId: string, name: string) {
    if (!name) return null;
    const normalizedName = name.normalize('NFKC').trim().toLocaleLowerCase('und');
    const [existing] = await tx.select({ id: tags.id }).from(tags).where(and(eq(tags.ownerId, ownerId), eq(tags.normalizedName, normalizedName))).limit(1);
    if (existing) return existing.id;
    const [created] = await tx.insert(tags).values({ ownerId, name, normalizedName }).onConflictDoNothing().returning({ id: tags.id });
    if (created) return created.id;
    const [raced] = await tx.select({ id: tags.id }).from(tags).where(and(eq(tags.ownerId, ownerId), eq(tags.normalizedName, normalizedName))).limit(1);
    if (!raced) throw new ConflictException('No se pudo resolver la etiqueta.');
    return raced.id;
  }

  private assertVersion(actual: number, expected: number) {
    if (actual !== expected) throw new PreconditionFailedException({ code: 'STALE_VERSION', message: 'El recurso cambió; vuelve a cargarlo antes de guardar.', expectedVersion: expected, currentVersion: actual });
  }

  private async createRevision(tx: Tx, ownerId: string, template: typeof templates.$inferSelect, id?: string, createdAt = new Date()) {
    const [latest] = await tx.select({ version: max(templateRevisions.version) }).from(templateRevisions)
      .where(and(eq(templateRevisions.ownerId, ownerId), eq(templateRevisions.templateId, template.id)));
    const version = Number(latest?.version ?? 0) + 1;
    const blocks = await this.readBlocks(tx, ownerId, template.id);
    const payload = { schemaVersion: 1 as const, templateId: template.id, name: template.name,
      blocks: blocks.map((block) => ({ blockId: block.id, taskId: block.taskId, title: block.title,
        description: block.description, tagName: block.tagName, daysMask: block.daysMask,
        startMinute: block.startMinute, endMinute: block.endMinute })) };
    const [revision] = await tx.insert(templateRevisions).values({ ...(id ? { id } : {}), ownerId, templateId: template.id, version, payload, createdAt }).returning({ id: templateRevisions.id });
    return revision.id;
  }

  private async recordActiveEdit(tx: Tx, ownerId: string, template: typeof templates.$inferSelect, now: Date, active: typeof activeSelections.$inferSelect, syncIds?: { revisionId?: string; eventId?: string }) {
    const revisionId = await this.createRevision(tx, ownerId, template, syncIds?.revisionId, now);
    const nextVersion = active.version + 1;
    await tx.update(activeSelections).set({ revisionId, effectiveAt: now, version: nextVersion }).where(eq(activeSelections.ownerId, ownerId));
    await tx.insert(scheduleChangeEvents).values({ eventId: syncIds?.eventId ?? randomUUID(), ownerId, effectiveAt: now,
      previousTemplateId: template.id, templateId: template.id, revisionId, zoneId: (await tx.select({ timeZone: users.timeZone }).from(users).where(eq(users.id, ownerId)).limit(1))[0]?.timeZone ?? 'UTC', changeType: 'ACTIVE_EDIT' });
  }
}
