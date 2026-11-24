import { Global, Inject, Injectable, Module, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';
import { drizzle, type NodePgDatabase } from 'drizzle-orm/node-postgres';
import * as schema from './schema';

export const DRIZZLE = Symbol('DRIZZLE');
export const DATABASE_POOL = Symbol('DATABASE_POOL');
export type Database = NodePgDatabase<typeof schema>;

@Injectable()
class DatabaseLifecycle implements OnApplicationShutdown {
  constructor(@Inject(DATABASE_POOL) private readonly pool: Pool) {}
  async onApplicationShutdown() { await this.pool.end(); }
}

@Global()
@Module({
  providers: [
    {
      provide: DATABASE_POOL,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        return new Pool({ connectionString: config.getOrThrow<string>('DATABASE_URL'), max: 10, idleTimeoutMillis: 30_000 });
      },
    },
    { provide: DRIZZLE, inject: [DATABASE_POOL], useFactory: (pool: Pool) => drizzle(pool, { schema }) },
    DatabaseLifecycle,
  ],
  exports: [DRIZZLE],
})
export class DatabaseModule {}
