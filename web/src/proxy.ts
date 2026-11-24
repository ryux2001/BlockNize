import type { NextRequest } from 'next/server';
import { getAuth } from '@/lib/auth/server';

export function proxy(request: NextRequest) {
  return getAuth().middleware({
    loginUrl: '/iniciar-sesion',
    publicRoutes: ['/crear-cuenta'],
  })(request);
}

export const config = {
  matcher: ['/hoy/:path*', '/calendario/:path*', '/semanas/:path*', '/registro/:path*', '/cuenta/:path*'],
};
