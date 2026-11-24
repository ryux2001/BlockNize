ALTER TABLE "idempotency_requests" ALTER COLUMN "expires_at" DROP NOT NULL;--> statement-breakpoint
ALTER TABLE "idempotency_requests" ADD COLUMN "device_id" uuid;