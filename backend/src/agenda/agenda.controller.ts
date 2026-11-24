import { Body, Controller, Get, Headers, HttpCode, HttpStatus, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiHeader, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsDateString, IsIn, IsInt, IsOptional, IsString, IsUUID, Matches, Max, MaxLength, Min } from 'class-validator';
import { CurrentUser, type AuthenticatedUser } from '../common/current-user.decorator';
import { ApiProperty } from '@nestjs/swagger';
import { AgendaService } from './agenda.service';
import { OccurrenceActionsService } from './occurrence-actions.service';
import { requireVersion } from '../common/etag';

class DayQuery {
  @IsDateString({ strict: true }) date!: string;
  @IsOptional() @IsIn(['day', 'calendar']) view: 'day' | 'calendar' = 'day';
}
class WeekQuery { @IsDateString({ strict: true }) startDate!: string; }
class CompletionBody {
  @IsDateString({ strict: true }) date!: string;
  @IsString() @MaxLength(600) occurrenceKey!: string;
  @ApiProperty({ type: Boolean }) @IsIn([true, false]) completed!: boolean;
  @IsOptional() @IsDateString() clientActionAt?: string;
  @IsOptional() @IsString() @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/) clientTimeZone?: string;
}
class ExceptionBody {
  @IsOptional() @IsUUID() exceptionId?: string;
  @IsOptional() @IsUUID() eventId?: string;
  @IsDateString({ strict: true }) date!: string;
  @IsString() @MaxLength(600) occurrenceKey!: string;
  @IsIn(['SKIP', 'REPLACE', 'RESTORE']) action!: 'SKIP' | 'REPLACE' | 'RESTORE';
  @IsOptional() @IsDateString({ strict: true }) targetDate?: string;
  @IsOptional() @IsString() @MaxLength(160) title?: string;
  @IsOptional() @IsString() @MaxLength(4000) description?: string;
  @IsOptional() @IsString() @MaxLength(80) tagName?: string | null;
  @IsOptional() @IsInt() @Min(0) @Max(1439) startMinute?: number;
  @IsOptional() @IsInt() @Min(1) @Max(1440) endMinute?: number;
  @IsOptional() @IsDateString() clientActionAt?: string;
  @IsOptional() @IsString() @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/) clientTimeZone?: string;
}

@ApiTags('agenda') @ApiBearerAuth() @Controller()
export class AgendaController {
  constructor(private readonly agenda: AgendaService, private readonly actions: OccurrenceActionsService) {}

  @Get('agenda')
  @ApiOperation({ summary: 'Obtiene la agenda de una fecha civil de la cuenta.' })
  day(@CurrentUser() user: AuthenticatedUser, @Query() query: DayQuery) { return this.agenda.getDay(user.id, query.date); }

  @Get('agenda/week')
  @ApiOperation({ summary: 'Obtiene siete fechas consecutivas desde startDate.' })
  week(@CurrentUser() user: AuthenticatedUser, @Query() query: WeekQuery) { return this.agenda.getWeek(user.id, query.startDate); }

  @Post('completions')
  @HttpCode(HttpStatus.OK)
  @ApiHeader({ name: 'Idempotency-Key', required: true, description: 'Identificador único para reintentos.' })
  @ApiOperation({ summary: 'Marca o deshace la finalización de una ocurrencia.' })
  async completion(@CurrentUser() user: AuthenticatedUser, @Body() body: CompletionBody, @Headers('idempotency-key') key: string | undefined) {
    return this.actions.setCompletion(user.id, body.date, body.occurrenceKey, body.completed, key ?? '', body);
  }

  @Post('occurrence-exceptions')
  @HttpCode(HttpStatus.OK)
  @ApiHeader({ name: 'If-Match', required: true, description: 'Versión de excepción, o "0" para crear una.' })
  @ApiHeader({ name: 'Idempotency-Key', required: true, description: 'Identificador único para reintentos.' })
  @ApiOperation({ summary: 'Omite, sustituye o restaura una ocurrencia semanal.' })
  async exception(@CurrentUser() user: AuthenticatedUser, @Body() body: ExceptionBody, @Headers('if-match') etag: string | undefined,
    @Headers('idempotency-key') key: string | undefined) {
    return this.actions.saveException(user.id, body, requireVersion(etag), key ?? '');
  }
}
