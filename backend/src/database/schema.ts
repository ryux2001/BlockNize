import {
  bigserial,
  bigint,
  check,
  date,
  foreignKey,
  index,
  integer,
  jsonb,
  pgTable,
  primaryKey,
  text,
  timestamp,
  unique,
  uuid,
} from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';

const instant = (name: string) => timestamp(name, { withTimezone: true, mode: 'date' });
const ownerKey = (name: string) => uuid(name).notNull();

export const users = pgTable('users', {
  id: uuid('id').defaultRandom().primaryKey(),
  authIssuer: text('auth_issuer').notNull(),
  authSubject: text('auth_subject').notNull(),
  email: text('email'),
  timeZone: text('time_zone').notNull().default('Europe/Madrid'),
  createdAt: instant('created_at').notNull().defaultNow(),
  updatedAt: instant('updated_at').notNull().defaultNow(),
}, (t) => [unique('users_auth_identity_uq').on(t.authIssuer, t.authSubject)]);

export const tags = pgTable('tags', {
  id: uuid('id').defaultRandom().primaryKey(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  normalizedName: text('normalized_name').notNull(),
  createdAt: instant('created_at').notNull().defaultNow(),
}, (t) => [unique('tags_owner_name_uq').on(t.ownerId, t.normalizedName), unique('tags_id_owner_uq').on(t.id, t.ownerId)]);

export const templates = pgTable('templates', {
  id: uuid('id').defaultRandom().primaryKey(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  version: integer('version').notNull().default(1),
  createdAt: instant('created_at').notNull().defaultNow(),
  updatedAt: instant('updated_at').notNull().defaultNow(),
  deletedAt: instant('deleted_at'),
}, (t) => [unique('templates_id_owner_uq').on(t.id, t.ownerId), check('templates_version_positive_ck', sql`${t.version} > 0`)]);

export const taskDefinitions = pgTable('task_definitions', {
  id: uuid('id').defaultRandom().primaryKey(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  templateId: uuid('template_id').notNull(),
  title: text('title').notNull(),
  description: text('description').notNull().default(''),
  tagId: uuid('tag_id'),
  deletedAt: instant('deleted_at'),
}, (t) => [
  unique('task_definitions_id_owner_uq').on(t.id, t.ownerId),
  unique('task_definitions_id_owner_template_uq').on(t.id, t.ownerId, t.templateId),
  index('task_definitions_template_idx').on(t.ownerId, t.templateId),
  foreignKey({ name: 'task_definitions_template_owner_fk', columns: [t.templateId, t.ownerId], foreignColumns: [templates.id, templates.ownerId] }).onDelete('cascade'),
  foreignKey({ name: 'task_definitions_tag_owner_fk', columns: [t.tagId, t.ownerId], foreignColumns: [tags.id, tags.ownerId] }),
]);

export const scheduleBlocks = pgTable('schedule_blocks', {
  id: uuid('id').defaultRandom().primaryKey(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  templateId: uuid('template_id').notNull(),
  taskId: uuid('task_id').notNull(),
  daysMask: integer('days_mask').notNull(),
  startMinute: integer('start_minute').notNull(),
  endMinute: integer('end_minute').notNull(),
  deletedAt: instant('deleted_at'),
}, (t) => [
  unique('schedule_blocks_id_owner_uq').on(t.id, t.ownerId),
  index('schedule_blocks_template_idx').on(t.ownerId, t.templateId),
  foreignKey({ name: 'schedule_blocks_template_owner_fk', columns: [t.templateId, t.ownerId], foreignColumns: [templates.id, templates.ownerId] }).onDelete('cascade'),
  foreignKey({ name: 'schedule_blocks_task_owner_template_fk', columns: [t.taskId, t.ownerId, t.templateId], foreignColumns: [taskDefinitions.id, taskDefinitions.ownerId, taskDefinitions.templateId] }).onDelete('cascade'),
  check('schedule_blocks_days_ck', sql`${t.daysMask} BETWEEN 1 AND 127`),
  check('schedule_blocks_interval_ck', sql`${t.startMinute} BETWEEN 0 AND 1439 AND ${t.endMinute} BETWEEN 1 AND 1440 AND ${t.startMinute} < ${t.endMinute}`),
]);

export const templateRevisions = pgTable('template_revisions', {
  id: uuid('id').defaultRandom().primaryKey(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  templateId: uuid('template_id').notNull(),
  version: integer('version').notNull(),
  createdAt: instant('created_at').notNull().defaultNow(),
  payload: jsonb('payload').notNull().$type<{ schemaVersion: 1; templateId: string; name: string; blocks: Array<Record<string, string | number | null>> }>(),
}, (t) => [unique('template_revisions_id_owner_uq').on(t.id, t.ownerId), unique('template_revisions_template_version_uq').on(t.ownerId, t.templateId, t.version), foreignKey({ name: 'template_revisions_template_owner_fk', columns: [t.templateId, t.ownerId], foreignColumns: [templates.id, templates.ownerId] }).onDelete('restrict')]);

export const activeSelections = pgTable('active_selections', {
  ownerId: ownerKey('owner_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
  templateId: uuid('template_id').notNull(),
  revisionId: uuid('revision_id').notNull(),
  effectiveAt: instant('effective_at').notNull(),
  version: integer('version').notNull().default(1),
}, (t) => [foreignKey({ name: 'active_selection_template_owner_fk', columns: [t.templateId, t.ownerId], foreignColumns: [templates.id, templates.ownerId] }).onDelete('restrict'), foreignKey({ name: 'active_selection_revision_owner_fk', columns: [t.revisionId, t.ownerId], foreignColumns: [templateRevisions.id, templateRevisions.ownerId] }).onDelete('restrict')]);

export const scheduleChangeEvents = pgTable('schedule_change_events', {
  sequence: bigserial('sequence', { mode: 'number' }).primaryKey(),
  eventId: uuid('event_id').notNull().unique(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  effectiveAt: instant('effective_at').notNull(),
  previousTemplateId: uuid('previous_template_id'),
  templateId: uuid('template_id').notNull(),
  revisionId: uuid('revision_id').notNull(),
  zoneId: text('zone_id').notNull(),
  changeType: text('change_type').notNull(),
}, (t) => [index('schedule_change_events_owner_time_idx').on(t.ownerId, t.effectiveAt, t.sequence), check('schedule_change_events_type_ck', sql`${t.changeType} IN ('ACTIVATION', 'ACTIVE_EDIT')`), foreignKey({ name: 'schedule_events_template_owner_fk', columns: [t.templateId, t.ownerId], foreignColumns: [templates.id, templates.ownerId] }).onDelete('restrict'), foreignKey({ name: 'schedule_events_previous_template_owner_fk', columns: [t.previousTemplateId, t.ownerId], foreignColumns: [templates.id, templates.ownerId] }).onDelete('restrict'), foreignKey({ name: 'schedule_events_revision_owner_fk', columns: [t.revisionId, t.ownerId], foreignColumns: [templateRevisions.id, templateRevisions.ownerId] }).onDelete('restrict')]);

export const oneOffTasks = pgTable('one_off_tasks', {
  id: uuid('id').defaultRandom().primaryKey(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  date: date('date', { mode: 'string' }).notNull(),
  title: text('title').notNull(),
  description: text('description').notNull().default(''),
  tagId: uuid('tag_id'),
  startMinute: integer('start_minute').notNull(),
  endMinute: integer('end_minute').notNull(),
  version: integer('version').notNull().default(1),
  updatedAt: instant('updated_at').notNull().defaultNow(),
  deletedAt: instant('deleted_at'),
}, (t) => [unique('one_off_tasks_id_owner_uq').on(t.id, t.ownerId), index('one_off_tasks_owner_date_idx').on(t.ownerId, t.date), check('one_off_tasks_interval_ck', sql`${t.startMinute} BETWEEN 0 AND 1439 AND ${t.endMinute} BETWEEN 1 AND 1440 AND ${t.startMinute} < ${t.endMinute}`), foreignKey({ name: 'one_off_tasks_tag_owner_fk', columns: [t.tagId, t.ownerId], foreignColumns: [tags.id, tags.ownerId] })]);

export const oneOffScheduleEvents = pgTable('one_off_schedule_events', {
  sequence: bigserial('sequence', { mode: 'number' }).primaryKey(),
  eventId: uuid('event_id').notNull().unique(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  taskId: uuid('task_id').notNull(),
  date: date('date', { mode: 'string' }).notNull(),
  effectiveAt: instant('effective_at').notNull(),
  changeType: text('change_type').notNull(),
  snapshot: jsonb('snapshot').notNull().$type<Record<string, unknown>>(),
  zoneId: text('zone_id').notNull(),
}, (t) => [index('one_off_events_owner_time_idx').on(t.ownerId, t.effectiveAt, t.sequence), check('one_off_events_type_ck', sql`${t.changeType} IN ('UPSERT', 'DELETE')`), foreignKey({ name: 'one_off_events_task_owner_fk', columns: [t.taskId, t.ownerId], foreignColumns: [oneOffTasks.id, oneOffTasks.ownerId] }).onDelete('restrict')]);

export const completionRecords = pgTable('completion_records', {
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  occurrenceKey: text('occurrence_key').notNull(),
  sourceType: text('source_type').notNull(),
  sourceId: uuid('source_id').notNull(),
  date: date('date', { mode: 'string' }).notNull(),
  completedAt: instant('completed_at').notNull(),
  titleSnapshot: text('title_snapshot').notNull(),
  startMinuteSnapshot: integer('start_minute_snapshot').notNull(),
  endMinuteSnapshot: integer('end_minute_snapshot').notNull(),
  sourceTemplateId: uuid('source_template_id'),
  sourceRevisionId: uuid('source_revision_id'),
  sourceEventId: uuid('source_event_id'),
}, (t) => [primaryKey({ columns: [t.ownerId, t.occurrenceKey] }), index('completion_records_owner_date_idx').on(t.ownerId, t.date), check('completion_records_source_ck', sql`${t.sourceType} IN ('WEEKLY', 'ONE_OFF')`)]);

export const occurrenceExceptions = pgTable('occurrence_exceptions', {
  id: uuid('id').defaultRandom().primaryKey(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  templateId: uuid('template_id').notNull(),
  blockId: uuid('block_id').notNull(),
  originalDate: date('original_date', { mode: 'string' }).notNull(),
  targetDate: date('target_date', { mode: 'string' }).notNull(),
  action: text('action').notNull(),
  payload: jsonb('payload').notNull().$type<Record<string, unknown>>(),
  zoneId: text('zone_id').notNull(),
  version: integer('version').notNull().default(1),
  createdAt: instant('created_at').notNull().defaultNow(),
  modifiedAt: instant('modified_at').notNull().defaultNow(),
}, (t) => [unique('occurrence_exceptions_origin_uq').on(t.ownerId, t.templateId, t.blockId, t.originalDate), unique('occurrence_exceptions_id_owner_uq').on(t.id, t.ownerId), check('occurrence_exceptions_action_ck', sql`${t.action} IN ('SKIP', 'REPLACE', 'RESTORE')`), foreignKey({ name: 'occurrence_exceptions_template_owner_fk', columns: [t.templateId, t.ownerId], foreignColumns: [templates.id, templates.ownerId] }).onDelete('restrict')]);

export const occurrenceExceptionEvents = pgTable('occurrence_exception_events', {
  sequence: bigserial('sequence', { mode: 'number' }).primaryKey(),
  eventId: uuid('event_id').notNull().unique(),
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  exceptionId: uuid('exception_id').notNull(),
  effectiveAt: instant('effective_at').notNull(),
  snapshot: jsonb('snapshot').notNull().$type<Record<string, unknown>>(),
}, (t) => [index('exception_events_owner_time_idx').on(t.ownerId, t.effectiveAt, t.sequence), foreignKey({ name: 'exception_events_exception_owner_fk', columns: [t.exceptionId, t.ownerId], foreignColumns: [occurrenceExceptions.id, occurrenceExceptions.ownerId] }).onDelete('restrict')]);

export const idempotencyRequests = pgTable('idempotency_requests', {
  ownerId: ownerKey('owner_id').references(() => users.id, { onDelete: 'cascade' }),
  key: text('key').notNull(),
  operation: text('operation').notNull(),
  requestHash: text('request_hash').notNull(),
  responseStatus: integer('response_status').notNull(),
  responseBody: jsonb('response_body'),
  deviceId: uuid('device_id'),
  createdAt: instant('created_at').notNull().defaultNow(),
  // Null expiry is reserved for stable BlockNize outbox receipts.
  expiresAt: instant('expires_at'),
}, (t) => [primaryKey({ columns: [t.ownerId, t.key, t.operation] }), index('idempotency_expiry_idx').on(t.expiresAt)]);

// A per-owner counter is updated in the same transaction as domain writes. Unlike a
// PostgreSQL sequence, its revisions are observed in commit order for that owner.
export const syncHeads = pgTable('sync_heads', {
  ownerId: ownerKey('owner_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
  revision: bigint('revision', { mode: 'number' }).notNull().default(0),
  updatedAt: instant('updated_at').notNull().defaultNow(),
});

export const syncCommits = pgTable('sync_commits', {
  ownerId: ownerKey('owner_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  revision: bigint('revision', { mode: 'number' }).notNull(),
  operationId: text('operation_id').notNull(),
  operation: text('operation').notNull(),
  requestHash: text('request_hash').notNull(),
  committedAt: instant('committed_at').notNull().defaultNow(),
}, (t) => [
  primaryKey({ columns: [t.ownerId, t.revision] }),
  unique('sync_commits_owner_operation_uq').on(t.ownerId, t.operationId),
  index('sync_commits_owner_time_idx').on(t.ownerId, t.committedAt, t.revision),
]);

// Ownership is explicit and composite foreign keys prevent cross-account relations.
