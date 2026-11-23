import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { requestIdMiddleware } from './common/request-id.middleware';
import { ApiExceptionFilter } from './common/api-exception.filter';
import { json } from 'express';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { bodyParser: false });
  app.use(json({ limit: '32mb' }));
  const config = app.get(ConfigService);
  app.setGlobalPrefix('api/v1');
  app.use(requestIdMiddleware);
  app.useGlobalPipes(new ValidationPipe({ transform: true, whitelist: true, forbidNonWhitelisted: true, transformOptions: { enableImplicitConversion: false } }));
  app.useGlobalFilters(new ApiExceptionFilter());
  const origins = config.getOrThrow<string>('CORS_ORIGINS').split(',').map((value) => value.trim()).filter(Boolean);
  app.enableCors({ origin: origins, credentials: true, methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Authorization', 'Content-Type', 'If-Match', 'Idempotency-Key', 'X-Request-Id', 'X-Client-Event-Id', 'X-Client-Revision-Id', 'X-Client-Action-At', 'X-Client-Time-Zone'], exposedHeaders: ['ETag', 'X-Request-Id'] });
  const openApi = new DocumentBuilder().setTitle('BlockNize API').setDescription('API versionada para agenda, plantillas e historial de BlockNize.').setVersion('1.0.0').addBearerAuth().build();
  SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, openApi), { jsonDocumentUrl: 'openapi.json' });
  app.enableShutdownHooks();
  await app.listen(config.getOrThrow<number>('PORT'), '0.0.0.0');
}

void bootstrap();
