import { Body, Controller, Delete, Get, Headers, Param, ParseUUIDPipe, Patch, Post, Put, Res } from '@nestjs/common';
import { ApiBearerAuth, ApiHeader, ApiOperation, ApiParam, ApiTags } from '@nestjs/swagger';
import type { Response } from 'express';
import { CurrentUser, type AuthenticatedUser } from '../common/current-user.decorator';
import { requireVersion, setEtag } from '../common/etag';
import { InjectDatabase, type Database } from '../database/database.types';
import { users } from '../database/schema';
import { eq } from 'drizzle-orm';
import { ActivateTemplateDto, CreateTemplateDto, SaveBlockDto, UpdateTemplateDto } from './templates.dto';
import { TemplatesService } from './templates.service';

@ApiTags('templates') @ApiBearerAuth() @Controller()
export class TemplatesController {
  constructor(private readonly service: TemplatesService, @InjectDatabase() private readonly db: Database) {}

  @Get('templates') @ApiOperation({ summary: 'Lista plantillas de la cuenta autenticada.' })
  list(@CurrentUser() user: AuthenticatedUser) { return this.service.list(user.id); }

  @Post('templates') @ApiHeader({ name: 'Idempotency-Key', required: true })
  @ApiOperation({ summary: 'Crea una plantilla vacía.' })
  async create(@CurrentUser() user: AuthenticatedUser, @Body() body: CreateTemplateDto, @Headers('idempotency-key') key: string | undefined,
    @Res({ passthrough: true }) response: Response) {
    const result = await this.service.create(user.id, body.name, key ?? '', body.id, body); setEtag(response, result.version); return result;
  }

  @Get('templates/:id') @ApiParam({ name: 'id', format: 'uuid' })
  async get(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Res({ passthrough: true }) response: Response) {
    const result = await this.service.get(user.id, id); setEtag(response, result.version); return result;
  }

  @Patch('templates/:id') @ApiHeader({ name: 'If-Match', required: true }) @ApiHeader({ name: 'Idempotency-Key', required: true })
  async rename(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Headers('if-match') etag: string | undefined,
    @Body() body: UpdateTemplateDto, @Headers('idempotency-key') key: string | undefined, @Res({ passthrough: true }) response: Response) {
    const result = await this.service.rename(user.id, id, requireVersion(etag), body.name, key ?? '', body); setEtag(response, result.version); return result;
  }

  @Post('templates/:id/blocks') @ApiHeader({ name: 'If-Match', required: true }) @ApiHeader({ name: 'Idempotency-Key', required: true })
  async createBlock(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Headers('if-match') etag: string | undefined,
    @Body() body: SaveBlockDto, @Headers('idempotency-key') key: string | undefined, @Res({ passthrough: true }) response: Response) {
    const result = await this.service.saveBlock(user.id, id, requireVersion(etag), body, key ?? '', true); setEtag(response, result.version); return result;
  }

  @Patch('templates/:id/blocks/:blockId') @ApiHeader({ name: 'If-Match', required: true }) @ApiHeader({ name: 'Idempotency-Key', required: true })
  async updateBlock(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Param('blockId', ParseUUIDPipe) blockId: string,
    @Headers('if-match') etag: string | undefined, @Body() body: SaveBlockDto, @Headers('idempotency-key') key: string | undefined,
    @Res({ passthrough: true }) response: Response) {
    const result = await this.service.saveBlock(user.id, id, requireVersion(etag), { ...body, id: blockId }, key ?? ''); setEtag(response, result.version); return result;
  }

  @Delete('templates/:id/blocks/:blockId') @ApiHeader({ name: 'If-Match', required: true }) @ApiHeader({ name: 'Idempotency-Key', required: true })
  @ApiHeader({ name: 'X-Client-Action-At', required: false }) @ApiHeader({ name: 'X-Client-Time-Zone', required: false })
  async deleteBlock(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Param('blockId', ParseUUIDPipe) blockId: string,
    @Headers('if-match') etag: string | undefined, @Headers('idempotency-key') key: string | undefined,
    @Headers('x-client-revision-id') revisionId: string | undefined, @Headers('x-client-event-id') eventId: string | undefined,
    @Headers('x-client-action-at') clientActionAt: string | undefined, @Headers('x-client-time-zone') clientTimeZone: string | undefined,
    @Res({ passthrough: true }) response: Response) {
    const result = await this.service.deleteBlock(user.id, id, blockId, requireVersion(etag), key ?? '', { revisionId, eventId, clientActionAt, clientTimeZone }); setEtag(response, result.version); return result;
  }

  @Get('active-selection') @ApiTags('agenda')
  active(@CurrentUser() user: AuthenticatedUser, @Res({ passthrough: true }) response: Response) {
    return this.service.active(user.id).then((result) => { if (result) setEtag(response, result.version); return result; });
  }

  @Put('active-selection') @ApiTags('agenda') @ApiHeader({ name: 'If-Match', required: true }) @ApiHeader({ name: 'Idempotency-Key', required: true })
  async activate(@CurrentUser() user: AuthenticatedUser, @Headers('if-match') etag: string | undefined, @Body() body: ActivateTemplateDto,
    @Headers('idempotency-key') key: string | undefined, @Res({ passthrough: true }) response: Response) {
    const [profile] = await this.db.select({ timeZone: users.timeZone }).from(users).where(eq(users.id, user.id)).limit(1);
    const result = await this.service.activate(user.id, body.templateId, requireVersion(etag), profile?.timeZone ?? 'UTC', key ?? '', body);
    setEtag(response, result.version); return result;
  }

  @Get('templates/:id/revisions') @ApiTags('history')
  revisions(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string) { return this.service.revisions(user.id, id); }

  @Get('templates/:id/revisions/:revisionId') @ApiTags('history')
  revision(@CurrentUser() user: AuthenticatedUser, @Param('id', ParseUUIDPipe) id: string, @Param('revisionId', ParseUUIDPipe) revisionId: string) {
    return this.service.revision(user.id, id, revisionId);
  }
}
