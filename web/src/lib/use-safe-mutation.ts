'use client';

import { useState } from 'react';
import { ApiError, isUncertainResult, sendMutation, type ApiResult, type MutationIntent } from '@/lib/api/client';
import { useWorkspace } from '@/lib/workspace-context';

export function useSafeMutation() {
  const { refresh, setStale, timeZone, stale } = useWorkspace();
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<MutationIntent | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);

  async function send<T>(intent: MutationIntent): Promise<ApiResult<T> | null> {
    if (busy) return null;
    setBusy(true);
    setMessage(null);
    setConflict(false);
    try {
      const result = await sendMutation<T>(intent);
      setPending(null);
      setStale(false);
      void refresh();
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        window.location.replace('/iniciar-sesion');
      } else if (isUncertainResult(error)) {
        setPending(intent);
        setStale(true);
        setMessage('Por confirmar. Puedes comprobar el resultado o reenviar exactamente la misma operación.');
      } else if (error instanceof ApiError && [409, 412].includes(error.status)) {
        setConflict(true);
        setMessage('La agenda cambió desde que la abriste. Tu borrador sigue disponible. Actualiza la versión antes de reaplicar el cambio.');
      } else {
        setMessage(error instanceof Error ? error.message : 'No se pudo guardar el cambio.');
      }
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function retryPending<T>() {
    if (!pending) return null;
    return send<T>(pending);
  }

  return {
    busy,
    stale,
    pending,
    message,
    conflict,
    timeZone,
    send,
    retryPending,
    clearMessage: () => setMessage(null),
  };
}
