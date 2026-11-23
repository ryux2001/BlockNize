CREATE TABLE "active_selections" (
	"owner_id" uuid PRIMARY KEY NOT NULL,
	"template_id" uuid NOT NULL,
	"revision_id" uuid NOT NULL,
	"effective_at" timestamp with time zone NOT NULL,
	"version" integer DEFAULT 1 NOT NULL
);
--> statement-breakpoint
CREATE TABLE "completion_records" (
	"owner_id" uuid NOT NULL,
	"occurrence_key" text NOT NULL,
	"source_type" text NOT NULL,
	"source_id" uuid NOT NULL,
	"date" date NOT NULL,
	"completed_at" timestamp with time zone NOT NULL,
	"title_snapshot" text NOT NULL,
	"start_minute_snapshot" integer NOT NULL,
	"end_minute_snapshot" integer NOT NULL,
	"source_template_id" uuid,
	"source_revision_id" uuid,
	"source_event_id" uuid,
	CONSTRAINT "completion_records_owner_id_occurrence_key_pk" PRIMARY KEY("owner_id","occurrence_key"),
	CONSTRAINT "completion_records_source_ck" CHECK ("completion_records"."source_type" IN ('WEEKLY', 'ONE_OFF'))
);
--> statement-breakpoint
CREATE TABLE "idempotency_requests" (
	"owner_id" uuid NOT NULL,
	"key" text NOT NULL,
	"operation" text NOT NULL,
	"request_hash" text NOT NULL,
	"response_status" integer NOT NULL,
	"response_body" jsonb,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	CONSTRAINT "idempotency_requests_owner_id_key_operation_pk" PRIMARY KEY("owner_id","key","operation")
);
--> statement-breakpoint
CREATE TABLE "occurrence_exception_events" (
	"sequence" bigserial PRIMARY KEY NOT NULL,
	"event_id" uuid NOT NULL,
	"owner_id" uuid NOT NULL,
	"exception_id" uuid NOT NULL,
	"effective_at" timestamp with time zone NOT NULL,
	"snapshot" jsonb NOT NULL,
	CONSTRAINT "occurrence_exception_events_event_id_unique" UNIQUE("event_id")
);
--> statement-breakpoint
CREATE TABLE "occurrence_exceptions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"owner_id" uuid NOT NULL,
	"template_id" uuid NOT NULL,
	"block_id" uuid NOT NULL,
	"original_date" date NOT NULL,
	"target_date" date NOT NULL,
	"action" text NOT NULL,
	"payload" jsonb NOT NULL,
	"zone_id" text NOT NULL,
	"version" integer DEFAULT 1 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"modified_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "occurrence_exceptions_origin_uq" UNIQUE("owner_id","template_id","block_id","original_date"),
	CONSTRAINT "occurrence_exceptions_id_owner_uq" UNIQUE("id","owner_id"),
	CONSTRAINT "occurrence_exceptions_action_ck" CHECK ("occurrence_exceptions"."action" IN ('SKIP', 'REPLACE', 'RESTORE'))
);
--> statement-breakpoint
CREATE TABLE "one_off_schedule_events" (
	"sequence" bigserial PRIMARY KEY NOT NULL,
	"event_id" uuid NOT NULL,
	"owner_id" uuid NOT NULL,
	"task_id" uuid NOT NULL,
	"date" date NOT NULL,
	"effective_at" timestamp with time zone NOT NULL,
	"change_type" text NOT NULL,
	"snapshot" jsonb NOT NULL,
	"zone_id" text NOT NULL,
	CONSTRAINT "one_off_schedule_events_event_id_unique" UNIQUE("event_id"),
	CONSTRAINT "one_off_events_type_ck" CHECK ("one_off_schedule_events"."change_type" IN ('UPSERT', 'DELETE'))
);
--> statement-breakpoint
CREATE TABLE "one_off_tasks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"owner_id" uuid NOT NULL,
	"date" date NOT NULL,
	"title" text NOT NULL,
	"description" text DEFAULT '' NOT NULL,
	"tag_id" uuid,
	"start_minute" integer NOT NULL,
	"end_minute" integer NOT NULL,
	"version" integer DEFAULT 1 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"deleted_at" timestamp with time zone,
	CONSTRAINT "one_off_tasks_id_owner_uq" UNIQUE("id","owner_id"),
	CONSTRAINT "one_off_tasks_interval_ck" CHECK ("one_off_tasks"."start_minute" BETWEEN 0 AND 1439 AND "one_off_tasks"."end_minute" BETWEEN 1 AND 1440 AND "one_off_tasks"."start_minute" < "one_off_tasks"."end_minute")
);
--> statement-breakpoint
CREATE TABLE "schedule_blocks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"owner_id" uuid NOT NULL,
	"template_id" uuid NOT NULL,
	"task_id" uuid NOT NULL,
	"days_mask" integer NOT NULL,
	"start_minute" integer NOT NULL,
	"end_minute" integer NOT NULL,
	"deleted_at" timestamp with time zone,
	CONSTRAINT "schedule_blocks_id_owner_uq" UNIQUE("id","owner_id"),
	CONSTRAINT "schedule_blocks_days_ck" CHECK ("schedule_blocks"."days_mask" BETWEEN 1 AND 127),
	CONSTRAINT "schedule_blocks_interval_ck" CHECK ("schedule_blocks"."start_minute" BETWEEN 0 AND 1439 AND "schedule_blocks"."end_minute" BETWEEN 1 AND 1440 AND "schedule_blocks"."start_minute" < "schedule_blocks"."end_minute")
);
--> statement-breakpoint
CREATE TABLE "schedule_change_events" (
	"sequence" bigserial PRIMARY KEY NOT NULL,
	"event_id" uuid NOT NULL,
	"owner_id" uuid NOT NULL,
	"effective_at" timestamp with time zone NOT NULL,
	"previous_template_id" uuid,
	"template_id" uuid NOT NULL,
	"revision_id" uuid NOT NULL,
	"zone_id" text NOT NULL,
	"change_type" text NOT NULL,
	CONSTRAINT "schedule_change_events_event_id_unique" UNIQUE("event_id"),
	CONSTRAINT "schedule_change_events_type_ck" CHECK ("schedule_change_events"."change_type" IN ('ACTIVATION', 'ACTIVE_EDIT'))
);
--> statement-breakpoint
CREATE TABLE "tags" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"owner_id" uuid NOT NULL,
	"name" text NOT NULL,
	"normalized_name" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "tags_owner_name_uq" UNIQUE("owner_id","normalized_name"),
	CONSTRAINT "tags_id_owner_uq" UNIQUE("id","owner_id")
);
--> statement-breakpoint
CREATE TABLE "task_definitions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"owner_id" uuid NOT NULL,
	"template_id" uuid NOT NULL,
	"title" text NOT NULL,
	"description" text DEFAULT '' NOT NULL,
	"tag_id" uuid,
	"deleted_at" timestamp with time zone,
	CONSTRAINT "task_definitions_id_owner_uq" UNIQUE("id","owner_id"),
	CONSTRAINT "task_definitions_id_owner_template_uq" UNIQUE("id","owner_id","template_id")
);
--> statement-breakpoint
CREATE TABLE "template_revisions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"owner_id" uuid NOT NULL,
	"template_id" uuid NOT NULL,
	"version" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"payload" jsonb NOT NULL,
	CONSTRAINT "template_revisions_id_owner_uq" UNIQUE("id","owner_id"),
	CONSTRAINT "template_revisions_template_version_uq" UNIQUE("owner_id","template_id","version")
);
--> statement-breakpoint
CREATE TABLE "templates" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"owner_id" uuid NOT NULL,
	"name" text NOT NULL,
	"version" integer DEFAULT 1 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"deleted_at" timestamp with time zone,
	CONSTRAINT "templates_id_owner_uq" UNIQUE("id","owner_id"),
	CONSTRAINT "templates_version_positive_ck" CHECK ("templates"."version" > 0)
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"auth_issuer" text NOT NULL,
	"auth_subject" text NOT NULL,
	"email" text,
	"time_zone" text DEFAULT 'Europe/Madrid' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "users_auth_identity_uq" UNIQUE("auth_issuer","auth_subject")
);
--> statement-breakpoint
ALTER TABLE "active_selections" ADD CONSTRAINT "active_selections_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "active_selections" ADD CONSTRAINT "active_selection_template_owner_fk" FOREIGN KEY ("template_id","owner_id") REFERENCES "public"."templates"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "active_selections" ADD CONSTRAINT "active_selection_revision_owner_fk" FOREIGN KEY ("revision_id","owner_id") REFERENCES "public"."template_revisions"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "completion_records" ADD CONSTRAINT "completion_records_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "idempotency_requests" ADD CONSTRAINT "idempotency_requests_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "occurrence_exception_events" ADD CONSTRAINT "occurrence_exception_events_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "occurrence_exception_events" ADD CONSTRAINT "exception_events_exception_owner_fk" FOREIGN KEY ("exception_id","owner_id") REFERENCES "public"."occurrence_exceptions"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "occurrence_exceptions" ADD CONSTRAINT "occurrence_exceptions_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "occurrence_exceptions" ADD CONSTRAINT "occurrence_exceptions_template_owner_fk" FOREIGN KEY ("template_id","owner_id") REFERENCES "public"."templates"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "one_off_schedule_events" ADD CONSTRAINT "one_off_schedule_events_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "one_off_schedule_events" ADD CONSTRAINT "one_off_events_task_owner_fk" FOREIGN KEY ("task_id","owner_id") REFERENCES "public"."one_off_tasks"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "one_off_tasks" ADD CONSTRAINT "one_off_tasks_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "one_off_tasks" ADD CONSTRAINT "one_off_tasks_tag_owner_fk" FOREIGN KEY ("tag_id","owner_id") REFERENCES "public"."tags"("id","owner_id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "schedule_blocks" ADD CONSTRAINT "schedule_blocks_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "schedule_blocks" ADD CONSTRAINT "schedule_blocks_template_owner_fk" FOREIGN KEY ("template_id","owner_id") REFERENCES "public"."templates"("id","owner_id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "schedule_blocks" ADD CONSTRAINT "schedule_blocks_task_owner_template_fk" FOREIGN KEY ("task_id","owner_id","template_id") REFERENCES "public"."task_definitions"("id","owner_id","template_id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "schedule_change_events" ADD CONSTRAINT "schedule_change_events_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "schedule_change_events" ADD CONSTRAINT "schedule_events_template_owner_fk" FOREIGN KEY ("template_id","owner_id") REFERENCES "public"."templates"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "schedule_change_events" ADD CONSTRAINT "schedule_events_previous_template_owner_fk" FOREIGN KEY ("previous_template_id","owner_id") REFERENCES "public"."templates"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "schedule_change_events" ADD CONSTRAINT "schedule_events_revision_owner_fk" FOREIGN KEY ("revision_id","owner_id") REFERENCES "public"."template_revisions"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "tags" ADD CONSTRAINT "tags_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "task_definitions" ADD CONSTRAINT "task_definitions_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "task_definitions" ADD CONSTRAINT "task_definitions_template_owner_fk" FOREIGN KEY ("template_id","owner_id") REFERENCES "public"."templates"("id","owner_id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "task_definitions" ADD CONSTRAINT "task_definitions_tag_owner_fk" FOREIGN KEY ("tag_id","owner_id") REFERENCES "public"."tags"("id","owner_id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "template_revisions" ADD CONSTRAINT "template_revisions_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "template_revisions" ADD CONSTRAINT "template_revisions_template_owner_fk" FOREIGN KEY ("template_id","owner_id") REFERENCES "public"."templates"("id","owner_id") ON DELETE restrict ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "templates" ADD CONSTRAINT "templates_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "completion_records_owner_date_idx" ON "completion_records" USING btree ("owner_id","date");--> statement-breakpoint
CREATE INDEX "idempotency_expiry_idx" ON "idempotency_requests" USING btree ("expires_at");--> statement-breakpoint
CREATE INDEX "exception_events_owner_time_idx" ON "occurrence_exception_events" USING btree ("owner_id","effective_at","sequence");--> statement-breakpoint
CREATE INDEX "one_off_events_owner_time_idx" ON "one_off_schedule_events" USING btree ("owner_id","effective_at","sequence");--> statement-breakpoint
CREATE INDEX "one_off_tasks_owner_date_idx" ON "one_off_tasks" USING btree ("owner_id","date");--> statement-breakpoint
CREATE INDEX "schedule_blocks_template_idx" ON "schedule_blocks" USING btree ("owner_id","template_id");--> statement-breakpoint
CREATE INDEX "schedule_change_events_owner_time_idx" ON "schedule_change_events" USING btree ("owner_id","effective_at","sequence");--> statement-breakpoint
CREATE INDEX "task_definitions_template_idx" ON "task_definitions" USING btree ("owner_id","template_id");