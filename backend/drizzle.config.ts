import { defineConfig } from 'drizzle-kit';
import { config as loadDotEnv } from 'dotenv';

loadDotEnv();

export default defineConfig({
  schema: './src/database/schema.ts',
  out: './migrations',
  dialect: 'postgresql',
  dbCredentials: { url: process.env.MIGRATION_DATABASE_URL ?? process.env.DATABASE_URL ?? '' },
  strict: true,
  verbose: true,
});
