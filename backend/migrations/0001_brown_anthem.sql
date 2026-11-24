CREATE TABLE "sync_commits" (
	"owner_id" uuid NOT NULL,
	"revision" bigint NOT NULL,
	"operation_id" text NOT NULL,
	"operation" text NOT NULL,
	"request_hash" text NOT NULL,
	"committed_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "sync_commits_owner_id_revision_pk" PRIMARY KEY("owner_id","revision"),
	CONSTRAINT "sync_commits_owner_operation_uq" UNIQUE("owner_id","operation_id")
);
--> statement-breakpoint
CREATE TABLE "sync_heads" (
	"owner_id" uuid PRIMARY KEY NOT NULL,
	"revision" bigint DEFAULT 0 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "sync_commits" ADD CONSTRAINT "sync_commits_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "sync_heads" ADD CONSTRAINT "sync_heads_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "sync_commits_owner_time_idx" ON "sync_commits" USING btree ("owner_id","committed_at","revision");