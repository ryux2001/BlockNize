import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus } from '@nestjs/common';
import type { Request, Response } from 'express';

@Catch()
export class ApiExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    const context = host.switchToHttp();
    const response = context.getResponse<Response>();
    const request = context.getRequest<Request & { requestId?: string }>();
    const status = exception instanceof HttpException ? exception.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
    const raw = exception instanceof HttpException ? exception.getResponse() : undefined;
    const message = typeof raw === 'string' ? raw : raw && typeof raw === 'object' && 'message' in raw ? (raw as { message: unknown }).message : 'Error interno del servidor.';
    const code = raw && typeof raw === 'object' && 'code' in raw ? (raw as { code: unknown }).code : `HTTP_${status}`;
    const details = raw && typeof raw === 'object' ? Object.fromEntries(Object.entries(raw).filter(([key]) => !['statusCode', 'code', 'message'].includes(key))) : undefined;
    response.status(status).json({ statusCode: status, code, message, ...(details && Object.keys(details).length ? { details } : {}), requestId: request.requestId });
  }
}
