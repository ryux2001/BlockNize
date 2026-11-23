import type { NextRequest } from 'next/server';
import { handleBackendRequest } from '@/lib/api/backend';

type BackendRouteContext = { params: Promise<{ path: string[] }> };

async function handle(request: NextRequest, context: BackendRouteContext) {
  const { path } = await context.params;
  return handleBackendRequest(request, path);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
