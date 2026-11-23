import { IsInt, IsObject, IsUUID, Min } from 'class-validator';

export class SyncImportDto {
  @IsUUID() importId!: string;
  @IsInt() @Min(1) formatVersion!: number;
  @IsObject() data!: Record<string, unknown>;
}
