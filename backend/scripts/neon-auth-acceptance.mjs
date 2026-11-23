import { randomBytes, randomUUID } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import { config as loadEnv } from 'dotenv';
import { Pool } from 'pg';

const backendDir = resolve(fileURLToPath(new URL('..', import.meta.url)));
const envPath = resolve(backendDir, '.env');
loadEnv({ path: envPath });

const issuer = required('NEON_AUTH_ISSUER');
const jwks = required('NEON_AUTH_JWKS_URL');
const databaseUrl = required('DATABASE_URL');
const apiPort = Number(process.env.PORT ?? 3001);
process.env.BLOCKNIZE_API_URL ??= `http://localhost:${apiPort}/api/v1`;
const apiBase = process.env.BLOCKNIZE_API_URL.replace(/\/$/, '');
const healthUrl = `${apiBase}/health/ready`;
const origin = (process.env.CORS_ORIGINS ?? 'http://localhost:3000').split(',')[0].trim();
const authBase = authBaseUrl(jwks);
const db = new Pool({ connectionString: process.env.MIGRATION_DATABASE_URL ?? databaseUrl, max: 1 });

let server;
let temporaryEmail;
let temporaryCreated = false;
let temporarySubject;
let persistentEmail = process.env.BLOCKNIZE_ACCEPTANCE_EMAIL;
let persistentPassword = process.env.BLOCKNIZE_ACCEPTANCE_PASSWORD;

try {
  if (!persistentEmail || !persistentPassword) {
    persistentEmail = 'blocknize-backend-acceptance@example.test';
    persistentPassword = randomBytes(36).toString('base64url');
    await signUp(persistentEmail, persistentPassword, 'BlockNize Backend Acceptance');
    saveLocalCredentials(persistentEmail, persistentPassword);
    console.log(`Created reusable Neon Auth test account: ${persistentEmail}`);
  }

  const tokenA = await signIn(persistentEmail, persistentPassword);
  const claimsA = jwtClaims(tokenA);
  temporaryEmail = `blocknize-acceptance-${randomUUID()}@example.test`;
  const temporaryPassword = randomBytes(36).toString('base64url');
  await signUp(temporaryEmail, temporaryPassword, 'BlockNize Temporary Acceptance');
  temporaryCreated = true;
  const tokenB = await signIn(temporaryEmail, temporaryPassword);
  temporarySubject = jwtClaims(tokenB).sub;

  await ensureApiRunning();
  const result = spawnSync(process.execPath, [
    '--test', '--test-concurrency=1',
    'test/auth-acceptance.test.mjs', 'test/api-workflows.test.mjs',
  ], {
    cwd: backendDir,
    env: { ...process.env, BLOCKNIZE_ACCEPTANCE_TOKEN_A: tokenA, BLOCKNIZE_ACCEPTANCE_TOKEN_B: tokenB },
    stdio: 'inherit',
    windowsHide: true,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exitCode = result.status ?? 1;
  else runDesktopCloudAcceptance({ email: temporaryEmail, password: temporaryPassword, apiBase, authBase, origin });

  console.log(`Reusable test identity: ${persistentEmail}`);
  console.log('Temporary second account will be deleted after this run.');
} finally {
  if (temporaryCreated) await deleteTemporaryAccount(temporaryEmail, temporarySubject);
  if (server) {
    server.kill();
    await Promise.race([once(server, 'exit'), delay(3000)]);
  }
  await db.end();
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} must be configured in backend/.env.`);
  return value;
}

function authBaseUrl(value) {
  const url = new URL(value);
  const suffix = '/.well-known/jwks.json';
  if (!url.pathname.endsWith(suffix)) throw new Error('NEON_AUTH_JWKS_URL must end with /.well-known/jwks.json.');
  url.pathname = url.pathname.slice(0, -suffix.length).replace(/\/$/, '');
  url.search = '';
  return url.toString().replace(/\/$/, '');
}

async function signUp(email, password, name) {
  const response = await fetch(`${authBase}/sign-up/email`, {
    method: 'POST',
    headers: { Origin: origin, 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, email, password }),
  });
  if (!response.ok) throw new Error(`Neon Auth test-account creation failed (HTTP ${response.status}).`);
  await response.arrayBuffer();
}

async function signIn(email, password) {
  const response = await fetch(`${authBase}/sign-in/email`, {
    method: 'POST',
    headers: { Origin: origin, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) throw new Error(`Neon Auth test-account sign-in failed (HTTP ${response.status}).`);
  const cookies = response.headers.getSetCookie?.() ?? [response.headers.get('set-cookie') ?? ''];
  const cookieHeader = cookies.map((cookie) => cookie.split(';', 1)[0]).filter(Boolean).join('; ');
  if (!cookieHeader) throw new Error('Neon Auth sign-in returned no session cookie.');

  const tokenResponse = await fetch(`${authBase}/token`, { headers: { Origin: origin, Cookie: cookieHeader } });
  if (!tokenResponse.ok) throw new Error(`Neon Auth JWT retrieval failed (HTTP ${tokenResponse.status}).`);
  const payload = await tokenResponse.json();
  const token = typeof payload === 'string'
    ? payload
    : payload.access_token ?? payload.token ?? payload.session?.access_token ?? payload.data?.token;
  if (typeof token !== 'string' || token.split('.').length !== 3) throw new Error('Neon Auth did not return a JWT access token.');
  return token;
}

function jwtClaims(token) {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
}

function saveLocalCredentials(email, password) {
  let contents = readFileSync(envPath, 'utf8');
  const values = {
    BLOCKNIZE_ACCEPTANCE_EMAIL: email,
    BLOCKNIZE_ACCEPTANCE_PASSWORD: password,
  };
  for (const [name, value] of Object.entries(values)) {
    const line = `${name}=${value}`;
    const expression = new RegExp(`^${name}=.*$`, 'm');
    if (expression.test(contents)) contents = contents.replace(expression, line);
    else contents += `${contents.endsWith('\n') ? '' : '\n'}${line}\n`;
  }
  writeFileSync(envPath, contents, { mode: 0o600 });
}

async function ensureApiRunning() {
  if (await isHealthy()) return;
  server = spawn(process.execPath, ['dist/src/main.js'], {
    cwd: backendDir,
    env: process.env,
    stdio: 'ignore',
    windowsHide: true,
  });
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    if (server.exitCode !== null) throw new Error(`Backend exited before readiness (code ${server.exitCode}).`);
    if (await isHealthy()) return;
    await delay(300);
  }
  throw new Error('Backend did not become ready within 30 seconds.');
}

function runDesktopCloudAcceptance({ email, password, apiBase, authBase, origin }) {
  const repositoryDir = resolve(backendDir, '..');
  const gradleWrapper = resolve(repositoryDir, 'android', 'gradlew.bat');
  const result = spawnSync(gradleWrapper, [
    '-p', 'desktop', ':desktopApp:test', '--tests', 'com.blocknize.desktop.DesktopCloudAcceptanceTest',
  ], {
    cwd: repositoryDir,
    env: {
      ...process.env,
      BLOCKNIZE_DESKTOP_CLOUD_ACCEPTANCE: '1',
      BLOCKNIZE_ACCEPTANCE_EMAIL: email,
      BLOCKNIZE_ACCEPTANCE_PASSWORD: password,
      BLOCKNIZE_API_BASE_URL: apiBase,
      BLOCKNIZE_AUTH_BASE_URL: authBase,
      BLOCKNIZE_AUTH_ORIGIN: origin,
    },
    stdio: 'inherit',
    shell: true,
    windowsHide: true,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exitCode = result.status ?? 1;
}

async function isHealthy() {
  try {
    const response = await fetch(healthUrl, { signal: AbortSignal.timeout(1500) });
    return response.ok;
  } catch {
    return false;
  }
}

async function deleteTemporaryAccount(email, subject) {
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    if (subject) await client.query('DELETE FROM public.users WHERE auth_issuer = $1 AND auth_subject = $2', [issuer, subject]);
    await client.query('DELETE FROM neon_auth.verification WHERE identifier = $1', [email]);
    await client.query('DELETE FROM neon_auth."user" WHERE email = $1', [email]);
    await client.query('COMMIT');
    const remainingAuth = await client.query('SELECT count(*)::int AS count FROM neon_auth."user" WHERE email = $1', [email]);
    const remainingProfile = subject
      ? await client.query('SELECT count(*)::int AS count FROM public.users WHERE auth_issuer = $1 AND auth_subject = $2', [issuer, subject])
      : { rows: [{ count: 0 }] };
    if (remainingAuth.rows[0].count !== 0 || remainingProfile.rows[0].count !== 0) {
      throw new Error('Temporary Neon Auth account cleanup could not be verified.');
    }
    console.log('Temporary second account and local profile removed and verified.');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}
