export type ApiErrorBody = {
  code?: string;
  message?: string | string[];
  requestId?: string;
  currentVersion?: number;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    readonly requestId?: string,
    readonly currentVersion?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export type ApiResult<T> = {
  data: T;
  etag: string | null;
  requestId: string | null;
};

export type MutationIntent = {
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  path: string;
  body?: string;
  headers: Record<string, string>;
};

type MutationInput = {
  method: MutationIntent['method'];
  path: string;
  body?: Record<string, unknown>;
  version?: number;
  timeZone: string;
};

function messageFrom(value: ApiErrorBody, status: number) {
  if (Array.isArray(value.message)) return value.message.join(' ');
  if (typeof value.message === 'string' && value.message) return value.message;
  if (status === 401) return 'Tu sesión terminó. Inicia sesión de nuevo.';
  if (status === 403) return 'La cuenta no tiene acceso a este cambio.';
  if (status === 404) return 'El elemento ya no está disponible.';
  if (status === 409 || status === 412) return 'La agenda cambió en otro dispositivo. Revisa la versión más reciente.';
  if (status === 428) return 'No se pudo confirmar la versión actual. Actualiza la agenda e inténtalo de nuevo.';
  if (status === 429) return 'Hay demasiadas solicitudes. Espera un momento y vuelve a intentarlo.';
  if (status >= 500) return 'La agenda no pudo responder. Puedes volver a intentarlo.';
  return 'No se pudo completar la solicitud.';
}

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<ApiResult<T>> {
  let response: Response;
  try {
    response = await fetch(`/api/backend${path}`, {
      ...init,
      cache: 'no-store',
      credentials: 'same-origin',
      headers: { Accept: 'application/json', ...init.headers },
    });
  } catch {
    throw new ApiError(0, 'NETWORK_UNAVAILABLE', 'No se pudo conectar. El borrador sigue en esta pestaña.');
  }

  let raw: string;
  try {
    raw = await response.text();
  } catch {
    throw new ApiError(0, 'NETWORK_UNAVAILABLE', 'No se pudo confirmar la respuesta. Puedes comprobar el resultado y reenviar la misma operación.');
  }
  let data: unknown = null;
  try {
    data = raw ? JSON.parse(raw) : null;
  } catch {
    data = null;
  }

  if (!response.ok) {
    const error = data && typeof data === 'object' ? data as ApiErrorBody : {};
    throw new ApiError(response.status, error.code, messageFrom(error, response.status),
      response.headers.get('x-request-id') ?? error.requestId, error.currentVersion);
  }

  return {
    data: data as T,
    etag: response.headers.get('etag'),
    requestId: response.headers.get('x-request-id'),
  };
}

export function createMutationIntent({ method, path, body, version, timeZone }: MutationInput): MutationIntent {
  const actionAt = new Date().toISOString();
  const eventId = crypto.randomUUID();
  const revisionId = crypto.randomUUID();
  const carriesTemplateHistory = path.startsWith('/templates/') || path === '/active-selection';
  const carriesOneOffHistory = path === '/one-off-tasks' || path.startsWith('/one-off-tasks/');
  const headers: Record<string, string> = {
    'Idempotency-Key': `bn-web:${crypto.randomUUID()}`,
    'X-Client-Event-Id': eventId,
    'X-Client-Revision-Id': revisionId,
    'X-Client-Action-At': actionAt,
    'X-Client-Time-Zone': timeZone,
  };
  if (version !== undefined) headers['If-Match'] = String(version);

  const payload = body ? {
    ...body,
    ...(carriesTemplateHistory && path !== '/templates' ? { eventId, revisionId } : {}),
    ...(carriesOneOffHistory ? { eventId } : {}),
    clientActionAt: actionAt,
    clientTimeZone: timeZone,
  } : undefined;

  return {
    method,
    path,
    ...(payload ? { body: JSON.stringify(payload) } : {}),
    headers,
  };
}

export function sendMutation<T>(intent: MutationIntent) {
  return requestJson<T>(intent.path, {
    method: intent.method,
    ...(intent.body ? { body: intent.body } : {}),
    headers: {
      ...intent.headers,
      ...(intent.body ? { 'Content-Type': 'application/json' } : {}),
    },
  });
}

export function isUncertainResult(error: unknown) {
  return error instanceof ApiError && [0, 503, 504].includes(error.status);
}
