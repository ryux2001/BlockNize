import { BadRequestException } from '@nestjs/common';

export interface ClientActionMetadata {
  clientActionAt?: string;
  clientTimeZone?: string;
}

/** Separates the time the user made an offline change from the time the server received it. */
export function clientEffectiveAt(metadata: ClientActionMetadata, receivedAt = new Date()): Date {
  clientActionZone(metadata, 'UTC');
  if (!metadata.clientActionAt) return receivedAt;
  const effectiveAt = new Date(metadata.clientActionAt);
  if (!Number.isFinite(effectiveAt.getTime()) || effectiveAt.getTime() > receivedAt.getTime() + 5 * 60_000) {
    throw new BadRequestException('La hora declarada de la acción no es válida o está demasiado adelantada.');
  }
  return effectiveAt;
}

export function clientActionZone(metadata: ClientActionMetadata, fallback: string): string {
  if (!metadata.clientTimeZone) return fallback;
  try { new Intl.DateTimeFormat('en-US', { timeZone: metadata.clientTimeZone }); }
  catch { throw new BadRequestException('La zona horaria declarada por el cliente no es válida.'); }
  return metadata.clientTimeZone;
}
