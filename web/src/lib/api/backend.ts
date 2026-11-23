import { randomUUID } from 'node:crypto';
import { getAuth } from '@/lib/auth/server';

const maxBodyBytes = 64 * 1024;
const methods = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']);
const queryKeysByRoute = new Map<string, ReadonlySet<string>>([
  ['/agenda', new Set(['date', 'view'])],
  ['/agenda/week', new Set(['startDate'])],
  ['/history', new Set(['from', 'to', 'limit'])],
  ['/one-off-tasks', new Set(['from', 'to'])],
]);

type SessionIdentity = { id: string; token: string };

function backendBaseUrl() {
  const configured = process.env.BLOCKNIZE_API_URL?.trim();
  if (!configured) throw new Error('Configura BLOCKNIZE_API_URL con la URL de la API BlockNize.');

  const url = new URL(configured);
  const localHosts = new Set(['localhost', '127.0.0.1', '[::1]']);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && localHosts.has(url.hostname))) {
    throw new Error('La API requiere HTTPS, salvo en localhost durante el desarrollo.');
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('BLOCKNIZE_API_URL solo puede contener el esquema, host y prefijo de la API.');
  }

  return url.toString().replace(/\/$/, '');
}

function isUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function routeAllowsMethod(method: string, path: string[]) {
  const joined = `/${path.join('/')}`;
  const templateId = path[1] ?? '';
  const blockId = path[3] ?? '';
  const itemId = path[1] ?? '';

  if (joined === '/me') return method === 'GET' || method === 'PATCH';
  if (joined === '/active-selection') return method === 'GET' || method === 'PUT';
  if (joined === '/tags' || joined === '/agenda' || joined === '/agenda/week' ||
      joined === '/history' || joined === '/sync/state') return method === 'GET';
  if (joined === '/templates') return method === 'GET' || method === 'POST';
  if (path.length === 2 && path[0] === 'templates' && isUuid(templateId)) {
    return method === 'GET' || method === 'PATCH';
  }
  if (path.length === 3 && path[0] === 'templates' && isUuid(templateId) && path[2] === 'blocks') {
    return method === 'POST';
  }
  if (path.length === 4 && path[0] === 'templates' && isUuid(templateId) && path[2] === 'blocks' && isUuid(blockId)) {
    return method === 'PATCH' || method === 'DELETE';
  }
  if (joined === '/one-off-tasks') return method === 'GET' || method === 'POST';
  if (path.length === 2 && path[0] === 'one-off-tasks' && isUuid(itemId)) {
    return method === 'GET' || method === 'PATCH' || method === 'DELETE';
  }
  if (joined === '/completions') return method === 'POST';
  return false;
}

function safeQuery(path: string[], searchParams: URLSearchParams) {
  const route = `/${path.join('/')}`;
  const allowed = queryKeysByRoute.get(route);
  for (const key of searchParams.keys()) {
    if (!allowed?.has(key)) return null;
  }
  return searchParams.toString();
}

function noStoreHeaders() {
  return new Headers({
    'Cache-Control': 'private, no-store',
    Vary: 'Cookie',
  });
}

function jsonError(status: number, code: string, message: string) {
  const headers = noStoreHeaders();
  headers.set('Content-Type', 'application/json; charset=utf-8');
  return new Response(JSON.stringify({ statusCode: status, code, message }), { status, headers });
}

async function currentSession(): Promise<SessionIdentity | null> {
  const result = await getAuth().getSession();
  const data = result.data as { session?: { token?: string }; user?: { id?: string } } | null;
  const token = data?.session?.token;
  const id = data?.user?.id;
  return typeof token === 'string' && token.length > 0 && typeof id === 'string' && id.length > 0
    ? { id, token }
    : null;
}

async function readBody(request: Request) {
  const length = Number(request.headers.get('content-length') ?? 0);
  if (length > maxBodyBytes) return { error: jsonError(413, 'BODY_TOO_LARGE', 'El contenido supera el tamaño permitido.') };
  if (!request.body) return { body: '' };

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    totalBytes += value.byteLength;
    if (totalBytes > maxBodyBytes) {
      await reader.cancel();
      return { error: jsonError(413, 'BODY_TOO_LARGE', 'El contenido supera el tamaño permitido.') };
    }
    chunks.push(value);
  }

  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return { body: new TextDecoder().decode(bytes) };
}

function validOrigin(request: Request) {
  const origin = request.headers.get('origin');
  if (!origin) return false;

  let expectedOrigin: string;
  try {
    expectedOrigin = process.env.BLOCKNIZE_WEB_ORIGIN
      ? new URL(process.env.BLOCKNIZE_WEB_ORIGIN).origin
      : new URL(request.url).origin;
    return new URL(origin).origin === expectedOrigin;
  } catch {
    return false;
  }
}

function cleanHeaders(response: Response) {
  const headers = noStoreHeaders();
  headers.set('Content-Type', response.headers.get('content-type') ?? 'application/json; charset=utf-8');
  for (const name of ['etag', 'x-request-id']) {
    const value = response.headers.get(name);
    if (value) headers.set(name, value);
  }
  return headers;
}

async function callBackend(request: Request, method: string, path: string[], query: string, body: string, token: string) {
  const target = `${backendBaseUrl()}/${path.join('/')}${query ? `?${query}` : ''}`;
  const headers = new Headers({
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': randomUUID(),
  });

  for (const name of [
    'content-type', 'if-match', 'idempotency-key', 'x-client-event-id', 'x-client-revision-id',
    'x-client-action-at', 'x-client-time-zone',
  ]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const response = await fetch(target, {
    method,
    headers,
    ...(body ? { body } : {}),
    cache: 'no-store',
    signal: AbortSignal.timeout(25_000),
  });
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: cleanHeaders(response),
  });
}

export async function handleBackendRequest(request: Request, path: string[]) {
  const method = request.method.toUpperCase();
  if (!methods.has(method) || !routeAllowsMethod(method, path)) {
    return jsonError(404, 'ROUTE_NOT_FOUND', 'La ruta solicitada no está disponible.');
  }

  const query = safeQuery(path, new URL(request.url).searchParams);
  if (query === null) return jsonError(400, 'INVALID_QUERY', 'La consulta contiene parámetros no admitidos.');
  if (method !== 'GET' && !validOrigin(request)) {
    return jsonError(403, 'INVALID_ORIGIN', 'La solicitud no procede del sitio de BlockNize.');
  }

  let body = '';
  if (method !== 'GET' && method !== 'DELETE') {
    const contentType = request.headers.get('content-type')?.split(';', 1)[0].trim().toLowerCase();
    if (contentType !== 'application/json') return jsonError(415, 'UNSUPPORTED_MEDIA_TYPE', 'Se esperaba contenido JSON.');
    const parsed = await readBody(request);
    if (parsed.error) return parsed.error;
    body = parsed.body;
    try {
      JSON.parse(body);
    } catch {
      return jsonError(400, 'INVALID_JSON', 'El contenido JSON no es válido.');
    }
  }

  try {
    const session = await currentSession();
    if (!session) return jsonError(401, 'UNAUTHENTICATED', 'Tu sesión terminó. Inicia sesión de nuevo.');

    const firstResponse = await callBackend(request, method, path, query, body, session.token);
    if (firstResponse.status !== 401) return firstResponse;

    const refreshedSession = await currentSession();
    if (!refreshedSession || refreshedSession.id !== session.id) {
      return jsonError(401, 'UNAUTHENTICATED', 'Tu sesión cambió o terminó. Inicia sesión de nuevo.');
    }

    return callBackend(request, method, path, query, body, refreshedSession.token);
  } catch (error) {
    if (error instanceof Error && error.name === 'TimeoutError') {
      return jsonError(504, 'REQUEST_TIMEOUT', 'La API tardó demasiado en responder. Comprueba el estado antes de volver a enviar el cambio.');
    }
    return jsonError(503, 'API_UNAVAILABLE', 'No se pudo conectar con la API. Tus cambios sin enviar siguen en esta pestaña.');
  }
}

