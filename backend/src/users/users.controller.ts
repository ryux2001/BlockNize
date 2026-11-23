import { BadRequestException, Body, Controller, Get, Patch } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { IsString, Matches } from 'class-validator';
import { CurrentUser, type AuthenticatedUser } from '../common/current-user.decorator';
import { InjectDatabase, type Database } from '../database/database.types';
import { users } from '../database/schema';
import { eq } from 'drizzle-orm';

class UpdateUserDto {
  @IsString()
  @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/)
  timeZone!: string;
}

@ApiTags('account')
@ApiBearerAuth()
@Controller('me')
export class UsersController {
  constructor(@InjectDatabase() private readonly db: Database) {}

  @Get()
  async me(@CurrentUser() user: AuthenticatedUser) {
    const [profile] = await this.db.select({ id: users.id, email: users.email, timeZone: users.timeZone, createdAt: users.createdAt })
      .from(users).where(eq(users.id, user.id)).limit(1);
    return profile;
  }

  @Patch()
  async update(@CurrentUser() user: AuthenticatedUser, @Body() body: UpdateUserDto) {
    try { new Intl.DateTimeFormat('en-US', { timeZone: body.timeZone }); }
    catch { throw new BadRequestException('Zona horaria IANA no válida.'); }
    const [profile] = await this.db.update(users).set({ timeZone: body.timeZone, updatedAt: new Date() })
      .where(eq(users.id, user.id)).returning({ id: users.id, email: users.email, timeZone: users.timeZone, updatedAt: users.updatedAt });
    return profile;
  }
}
