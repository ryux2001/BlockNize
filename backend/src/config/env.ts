import { z } from 'zod';

const optionalUrl = z.union([z.literal(''), z.string().url()]).optional();

export const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().min(1).max(65535).default(3001),
  DATABASE_URL: z.string().min(1),
  MIGRATION_DATABASE_URL: z.string().optional(),
  NEON_AUTH_JWKS_URL: z.string().url(),
  NEON_AUTH_ISSUER: z.string().url(),
  NEON_AUTH_AUDIENCE: z.union([z.literal(''), z.string().min(1)]).optional(),
  CORS_ORIGINS: z.string().default('http://localhost:3000'),
});

export function validateEnv(config: Record<string, unknown>) {
  const parsed = envSchema.safeParse(config);
  if (!parsed.success) {
    throw new Error(`Invalid environment configuration: ${z.treeifyError(parsed.error).errors.join('; ')}`);
  }
  return parsed.data;
}
