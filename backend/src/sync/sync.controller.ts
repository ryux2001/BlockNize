import { Body, Controller, Get, Headers, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiHeader, ApiOperation, ApiQuery, ApiTags } from '@nestjs/swagger';
import { CurrentUser, type AuthenticatedUser } from '../common/current-user.decorator';
import { SyncService } from './sync.service';
import { SyncImportDto } from './sync-import.dto';

@ApiTags('sync') @ApiBearerAuth() @Controller('sync')
export class SyncController {
  constructor(private readonly service: SyncService) {}

  @Get('state') @ApiOperation({ summary: 'Devuelve la posición confirmada de cambios de la cuenta.' })
  state(@CurrentUser() user: AuthenticatedUser) { return this.service.state(user.id); }

  @Get('bootstrap') @ApiOperation({ summary: 'Descarga una instantánea consistente de los datos de la cuenta.' })
  bootstrap(@CurrentUser() user: AuthenticatedUser) { return this.service.bootstrap(user.id); }

  @Get('changes')
  @ApiOperation({ summary: 'Paginación ordenada de cambios confirmados desde un cursor de cuenta.' })
  @ApiQuery({ name: 'cursor', required: false, example: '0' })
  @ApiQuery({ name: 'limit', required: false, example: '100' })
  changes(@CurrentUser() user: AuthenticatedUser, @Query('cursor') cursor?: string, @Query('limit') limit?: string) {
    return this.service.changes(user.id, cursor, limit);
  }

  @Post('import') @ApiHeader({ name: 'Idempotency-Key', required: true })
  @ApiOperation({ summary: 'Importa una única copia local a una cuenta vacía sin regenerar identidades ni eventos.' })
  import(@CurrentUser() user: AuthenticatedUser, @Body() body: SyncImportDto, @Headers('idempotency-key') key?: string) {
    return this.service.importLocalData(user.id, body, key ?? '');
  }
}
