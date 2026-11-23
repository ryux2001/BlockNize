import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ConfigModule } from '@nestjs/config';
import { validateEnv } from './config/env';
import { DatabaseModule } from './database/database.module';
import { NeonJwtGuard } from './auth/neon-jwt.guard';
import { HealthController } from './health/health.controller';
import { UsersController } from './users/users.controller';
import { TemplatesController } from './templates/templates.controller';
import { TemplatesService } from './templates/templates.service';
import { AgendaController } from './agenda/agenda.controller';
import { AgendaService } from './agenda/agenda.service';
import { OccurrenceActionsService } from './agenda/occurrence-actions.service';
import { IdempotencyService } from './common/idempotency.service';
import { OneOffController } from './one-off-tasks/one-off.controller';
import { OneOffService } from './one-off-tasks/one-off.service';
import { HistoryController } from './history/history.controller';
import { TagsController } from './tags/tags.controller';
import { SyncController } from './sync/sync.controller';
import { SyncService } from './sync/sync.service';

@Module({
  imports: [ConfigModule.forRoot({ isGlobal: true, validate: validateEnv }), DatabaseModule],
  controllers: [HealthController, UsersController, TemplatesController, AgendaController, OneOffController, HistoryController, TagsController, SyncController],
  providers: [TemplatesService, AgendaService, OccurrenceActionsService, OneOffService, SyncService, IdempotencyService, { provide: APP_GUARD, useClass: NeonJwtGuard }],
})
export class AppModule {}
