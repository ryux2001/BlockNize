import { IsDateString, IsInt, IsNotEmpty, IsOptional, IsString, IsUUID, Matches, Max, MaxLength, Min } from 'class-validator';

export class CreateTemplateDto {
  @IsOptional() @IsUUID() id?: string;
  @IsOptional() @IsDateString() clientActionAt?: string;
  @IsOptional() @IsString() @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/) clientTimeZone?: string;
  @IsString() @IsNotEmpty() @MaxLength(120) name!: string;
}

export class UpdateTemplateDto {
  @IsOptional() @IsUUID() revisionId?: string;
  @IsOptional() @IsUUID() eventId?: string;
  @IsOptional() @IsDateString() clientActionAt?: string;
  @IsOptional() @IsString() @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/) clientTimeZone?: string;
  @IsString() @IsNotEmpty() @MaxLength(120) name!: string;
}

export class SaveBlockDto {
  @IsOptional() @IsUUID() id?: string;
  @IsOptional() @IsUUID() taskId?: string;
  @IsOptional() @IsUUID() revisionId?: string;
  @IsOptional() @IsUUID() eventId?: string;
  @IsOptional() @IsDateString() clientActionAt?: string;
  @IsOptional() @IsString() @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/) clientTimeZone?: string;
  @IsString() @IsNotEmpty() @MaxLength(160) title!: string;
  @IsOptional() @IsString() @MaxLength(4000) description = '';
  @IsOptional() @IsString() @MaxLength(80) tagName?: string;
  @IsInt() @Min(1) @Max(127) daysMask!: number;
  @IsInt() @Min(0) @Max(1439) startMinute!: number;
  @IsInt() @Min(1) @Max(1440) endMinute!: number;
}

export class ActivateTemplateDto {
  @IsUUID() templateId!: string;
  @IsOptional() @IsUUID() revisionId?: string;
  @IsOptional() @IsUUID() eventId?: string;
  @IsOptional() @IsDateString() clientActionAt?: string;
  @IsOptional() @IsString() @Matches(/^[A-Za-z_+-]+(?:\/[A-Za-z0-9._+-]+)*$/) clientTimeZone?: string;
}
