import { Inject } from '@nestjs/common';
import { DRIZZLE, type Database } from './database.module';

export const InjectDatabase = () => Inject(DRIZZLE);
export type { Database };
