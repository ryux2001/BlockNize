import { Body, Controller, Delete, Get, Headers, Param, ParseUUIDPipe, Patch, Post, Query, Res } from '@nestjs/common';
import { ApiBearerAuth, ApiHeader, ApiOperation, ApiTags } from '@nestjs/swagger';
import type { Response } from 'express';
import { CurrentUser, type AuthenticatedUser } from '../common/current-user.decorator';
import { requireVersion, setEtag } from '../common/etag';
import { OneOffDto, OneOffListQuery } from './one-off.dto';
import { OneOffService } from './one-off.service';

@ApiTags('one-off-tasks') @ApiBearerAuth() @Controller('one-off-tasks')
export class OneOffController {
  constructor(private readonly service: OneOffService) {}

  @Get()
  @ApiOperation({ summary: 'Lista tareas temporales actuales del propietario dentro del rango.' })
  list(@CurrentUser() user: AuthenticatedUser, @Query() query: OneOffListQuery) { return this.service.list(user.id, query.from, query.to); }

  @Get(':id')
  async get(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Res({ passthrough: true }) response: Response) {
    const result = await this.service.get(user.id, id); setEtag(response, result.version); return result;
  }

  @Post()
  @ApiHeader({ name: 'Idempotency-Key', required: true })
  @ApiOperation({ summary: 'Crea una tarea temporal y su evento histórico en una transacción.' })
  async create(@CurrentUser() user: AuthenticatedUser, @Body() body: OneOffDto, @Headers('idempotency-key') key: string | undefined,
    @Res({ passthrough: true }) response: Response) {
    const result = await this.service.create(user.id, body, key ?? ''); setEtag(response, result.version); return result;
  }

  @Patch(':id')
  @ApiHeader({ name: 'If-Match', required: true }) @ApiHeader({ name: 'Idempotency-Key', required: true })
  async update(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Headers('if-match') etag: string | undefined,
    @Headers('idempotency-key') key: string | undefined, @Body() body: OneOffDto, @Res({ passthrough: true }) response: Response) {
    const result = await this.service.update(user.id, id, requireVersion(etag), body, key ?? ''); setEtag(response, result.version); return result;
  }

  @Delete(':id')
  @ApiHeader({ name: 'If-Match', required: true }) @ApiHeader({ name: 'Idempotency-Key', required: true }) @ApiHeader({ name: 'X-Client-Event-Id', required: false })
  @ApiHeader({ name: 'X-Client-Action-At', required: false }) @ApiHeader({ name: 'X-Client-Time-Zone', required: false })
  async delete(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Headers('if-match') etag: string | undefined,
    @Headers('idempotency-key') key: string | undefined, @Headers('x-client-event-id') eventId?: string,
    @Headers('x-client-action-at') clientActionAt?: string, @Headers('x-client-time-zone') clientTimeZone?: string) {
    return this.service.delete(user.id, id, requireVersion(etag), key ?? '', eventId, clientActionAt, clientTimeZone);
  }
}
