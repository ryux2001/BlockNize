import { HttpException, HttpStatus } from '@nestjs/common';

export function requireVersion(value: string | undefined): number {
  if (!value) throw new HttpException('Se requiere If-Match con la versión cargada.', HttpStatus.PRECONDITION_REQUIRED);
  const match = /^"?(\d+)"?$/.exec(value.trim());
  if (!match) throw new HttpException('If-Match debe contener el número de versión ETag.', HttpStatus.BAD_REQUEST);
  return Number(match[1]);
}

export function setEtag(response: { setHeader(name: string, value: string): void }, version: number) {
  response.setHeader('ETag', `"${version}"`);
}
