import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import type { Request } from 'express';

export interface AuthenticatedUser {
  id: string;
  issuer: string;
  subject: string;
  email?: string;
}

export type AuthenticatedRequest = Request & { user: AuthenticatedUser; requestId: string };

export const CurrentUser = createParamDecorator((_data: unknown, context: ExecutionContext) =>
  context.switchToHttp().getRequest<AuthenticatedRequest>().user,
);
