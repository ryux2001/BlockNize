import { Controller, Get } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { asc, eq } from 'drizzle-orm';
import { CurrentUser, type AuthenticatedUser } from '../common/current-user.decorator';
import { InjectDatabase, type Database } from '../database/database.types';
import { tags } from '../database/schema';

@ApiTags('tags') @ApiBearerAuth() @Controller('tags')
export class TagsController {
  constructor(@InjectDatabase() private readonly db: Database) {}

  @Get()
  @ApiOperation({ summary: 'Lista etiquetas de la cuenta autenticada.' })
  list(@CurrentUser() user: AuthenticatedUser) {
    return this.db.select({ id: tags.id, name: tags.name }).from(tags).where(eq(tags.ownerId, user.id)).orderBy(asc(tags.normalizedName));
  }
}
