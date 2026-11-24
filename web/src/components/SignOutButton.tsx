'use client';

import { useState } from 'react';
import { authClient } from '@/lib/auth/client';

export function SignOutButton() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function signOut() {
    if (busy) return;
    setBusy(true);
    setError(null);
    const result = await authClient.signOut();
    if (result.error) {
      setError('No se pudo confirmar el cierre de sesión. Comprueba la conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('blocknize-session');
    channel?.postMessage('signed-out');
    channel?.close();
    window.location.replace('/iniciar-sesion');
  }

  return <div><button className="button button-danger" type="button" onClick={signOut} disabled={busy}>{busy ? 'Cerrando sesión…' : 'Cerrar sesión'}</button>{error && <p className="form-error" role="alert">{error}</p>}</div>;
}
