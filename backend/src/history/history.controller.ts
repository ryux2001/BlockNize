import { BadRequestException, Controller, Get, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsDateString, IsOptional, IsInt, Max, Min } from 'class-validator';
import { Type } from 'class-transformer';
import { CurrentUser, type AuthenticatedUser } from '../common/current-user.decorator';
import { AgendaService } from '../agenda/agenda.service';

class HistoryQuery {
  @IsDateString({ strict: true }) from!: string;
  @IsDateString({ strict: true }) to!: string;
  @IsOptional() @Type(() => Number) @IsInt() @Min(1) @Max(31) limit = 7;
}

@ApiTags('history') @ApiBearerAuth() @Controller('history')
export class HistoryController {
  constructor(private readonly agenda: AgendaService) {}

  @Get()
  @ApiOperation({ summary: 'Reconstruye ocurrencias e historial de una ventana de hasta 31 días.' })
  async list(@CurrentUser() user: AuthenticatedUser, @Query() query: HistoryQuery) {
    if (query.from > query.to) throw new BadRequestException('from debe ser anterior o igual a to.');
    const first = Date.parse(`${query.from}T00:00:00Z`);
    const last = Date.parse(`${query.to}T00:00:00Z`);
    const days = Math.floor((last - first) / 86_400_000) + 1;
    if (!Number.isFinite(days) || days > 31) throw new BadRequestException('La consulta de historial no puede superar 31 días.');
    const pageDays = Math.min(days, query.limit);
    const entries = await Promise.all(Array.from({ length: pageDays }, async (_, offset) => {
      const date = new Date(first + offset * 86_400_000).toISOString().slice(0, 10);
      return { date, occurrences: await this.agenda.getDay(user.id, date) };
    }));
    const nextDate = pageDays < days ? new Date(first + pageDays * 86_400_000).toISOString().slice(0, 10) : null;
    return { entries, nextDate };
  }
}
