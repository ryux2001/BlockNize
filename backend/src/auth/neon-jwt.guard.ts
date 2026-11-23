import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Reflector } from '@nestjs/core';
import { createRemoteJWKSet, jwtVerify, type JWTPayload } from 'jose';
import { and, eq } from 'drizzle-orm';
import type { AuthenticatedRequest } from '../common/current-user.decorator';
import { IS_PUBLIC_KEY } from '../common/public.decorator';
import { InjectDatabase, type Database } from '../database/database.types';
import { users } from '../database/schema';

@Injectable()
export class NeonJwtGuard implements CanActivate {
  private readonly jwks: ReturnType<typeof createRemoteJWKSet>;
  private readonly issuer: string;
  private readonly audience?: string;

  constructor(
    private readonly config: ConfigService,
    private readonly reflector: Reflector,
    @InjectDatabase() private readonly db: Database,
  ) {
    this.jwks = createRemoteJWKSet(new URL(this.config.getOrThrow<string>('NEON_AUTH_JWKS_URL')));
    this.issuer = this.config.getOrThrow<string>('NEON_AUTH_ISSUER');
    this.audience = this.config.get<string>('NEON_AUTH_AUDIENCE') || undefined;
  }

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [context.getHandler(), context.getClass()]);
    if (isPublic) return true;
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const header = request.headers.authorization;
    if (!header?.startsWith('Bearer ')) throw new UnauthorizedException('Falta un token Bearer válido.');

    let payload: JWTPayload;
    try {
      const token = header.slice('Bearer '.length).trim();
      if (!token || token.length > 8192) throw new Error('invalid token length');
      const { payload: verified } = await jwtVerify(token, this.jwks, {
        issuer: this.issuer,
        requiredClaims: ['exp', 'sub'],
        ...(this.audience ? { audience: this.audience } : {}),
        algorithms: ['RS256', 'ES256', 'EdDSA'],
      });
      if (typeof verified.sub !== 'string' || verified.sub.length < 1 || verified.sub.length > 255) throw new Error('invalid subject');
      payload = verified;
    } catch {
      throw new UnauthorizedException('El token de sesión no es válido o ha caducado.');
    }
    const [known] = await this.db.select({ id: users.id }).from(users)
      .where(and(eq(users.authIssuer, this.issuer), eq(users.authSubject, payload.sub!))).limit(1);
    const [user] = known ? [known] : await this.db.insert(users).values({
      authIssuer: this.issuer,
      authSubject: payload.sub!,
      ...(typeof payload.email === 'string' ? { email: payload.email } : {}),
    }).onConflictDoNothing().returning({ id: users.id });
    const profile = user ?? (await this.db.select({ id: users.id }).from(users)
      .where(and(eq(users.authIssuer, this.issuer), eq(users.authSubject, payload.sub!))).limit(1))[0];
    if (!profile) throw new Error('Verified identity profile could not be resolved.');
    request.user = { id: profile.id, issuer: this.issuer, subject: payload.sub!,
      ...(typeof payload.email === 'string' ? { email: payload.email } : {}) };
    return true;
  }
}
