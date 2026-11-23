import { IsDateString, IsInt, IsOptional, IsString, IsUUID, Matches, Max, MaxLength, Min } from 'class-validator';

export class OneOffDto {
  @IsOptional() @IsUUID() id?: string;
  @IsOptional() @IsUUID() eventId?: string;
  @IsDateString({ strict: true }) date!: string;
  @IsString() @MaxLength(160) title!: string;
  @IsOptional() @IsString() @MaxLength(4000) description = '';
  @IsOptional() @IsString() @MaxLength(80) tagName?: string;
  @IsOptional() @IsDateString() clientActionAt?: string;
  @IsOptional() @IsString() @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/) clientTimeZone?: string;
  @IsInt() @Min(0) @Max(1439) startMinute!: number;
  @IsInt() @Min(1) @Max(1440) endMinute!: number;
}

export class OneOffListQuery {
  @IsDateString({ strict: true }) from!: string;
  @IsDateString({ strict: true }) to!: string;
}
