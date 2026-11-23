import { randomUUID } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';

export function requestIdMiddleware(req: Request & { requestId?: string }, res: Response, next: NextFunction) {
  const requested = req.header('x-request-id');
  req.requestId = requested && /^[a-zA-Z0-9._-]{1,80}$/.test(requested) ? requested : randomUUID();
  res.setHeader('x-request-id', req.requestId);
  next();
}
