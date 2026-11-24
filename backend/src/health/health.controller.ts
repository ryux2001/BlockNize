import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { sql } from 'drizzle-orm';
import { Public } from '../common/public.decorator';
import { InjectDatabase, type Database } from '../database/database.types';

@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(@InjectDatabase() private readonly db: Database) {}

  @Public()
  @Get('live')
  @ApiOperation({ summary: 'Indica que el proceso NestJS está activo.' })
  live() { return { status: 'ok' }; }

  @Public()
  @Get('ready')
  @ApiOperation({ summary: 'Comprueba la conexión con PostgreSQL.' })
  async ready() {
    try {
      await this.db.execute(sql`select 1`);
      return { status: 'ready', database: 'connected' };
    } catch {
      throw new ServiceUnavailableException({ status: 'not_ready', database: 'unavailable' });
    }
  }
}
