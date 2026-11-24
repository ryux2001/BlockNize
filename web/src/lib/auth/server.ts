import { createNeonAuth } from '@neon/auth/next/server';

let authInstance: ReturnType<typeof createNeonAuth> | undefined;

export function getAuth() {
  if (authInstance) return authInstance;

  const baseUrl = process.env.NEON_AUTH_BASE_URL?.trim();
  const cookieSecret = process.env.NEON_AUTH_COOKIE_SECRET;
  if (!baseUrl || !cookieSecret || cookieSecret.length < 32) {
    throw new Error('Configura NEON_AUTH_BASE_URL y un NEON_AUTH_COOKIE_SECRET de al menos 32 caracteres.');
  }

  authInstance = createNeonAuth({
    baseUrl,
    cookies: {
      secret: cookieSecret,
      sameSite: 'strict',
    },
  });

  return authInstance;
}
