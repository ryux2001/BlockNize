'use client';

import { useEffect, useState } from 'react';
import { authClient } from '@/lib/auth/client';
import { ApiError, isUncertainResult, requestJson } from '@/lib/api/client';
import type { UserProfile } from '@/lib/api/types';
import { useWorkspace } from '@/lib/workspace-context';
import { SignOutButton } from '@/components/SignOutButton';

export function AccountScreen() {
  const { refreshEpoch, setStale } = useWorkspace();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [verified, setVerified] = useState<boolean | null>(null);
  const [loadedEpoch, setLoadedEpoch] = useState<number | null>(null);
  const [loadError, setLoadError] = useState<{ epoch: number; message: string } | null>(null);
  const loading = loadedEpoch !== refreshEpoch;
  const error = loadError?.epoch === refreshEpoch ? loadError.message : null;

  useEffect(() => {
    let cancelled = false;
    Promise.all([requestJson<UserProfile>('/me'), authClient.getSession()])
      .then(([profileResult, session]) => {
        if (cancelled) return;
        setProfile(profileResult.data);
        setVerified(session.data?.user?.emailVerified ?? null);
        setLoadError(null);
      })
      .catch((reason: unknown) => {
        if (cancelled) return;
        if (reason instanceof ApiError && reason.status === 401) {
          window.location.replace('/iniciar-sesion');
          return;
        }
        if (isUncertainResult(reason)) setStale(true);
        setLoadError({ epoch: refreshEpoch, message: reason instanceof Error ? reason.message : 'No se pudo cargar la cuenta.' });
      })
      .finally(() => { if (!cancelled) setLoadedEpoch(refreshEpoch); });
    return () => { cancelled = true; };
  }, [refreshEpoch, setStale]);

  return (
    <>
      <div className="page-heading"><div><h1>Cuenta</h1><p>Identidad y horario de tu agenda.</p></div></div>
      {loading && <p className="status-note" role="status">Cargando la cuenta…</p>}
      {error && <p className="status-note error" role="alert">{error}</p>}
      {profile && <>
        <section className="panel" aria-labelledby="account-heading">
          <div className="panel-header"><h2 id="account-heading">Cuenta conectada</h2><span className={`badge ${verified ? 'badge-active' : ''}`}>{verified === null ? 'Estado de correo desconocido' : verified ? 'Correo verificado' : 'Correo pendiente de verificación'}</span></div>
          <dl className="account-details">
            <div className="account-detail"><dt>Correo</dt><dd>{profile.email ?? 'No disponible'}</dd></div>
            <div className="account-detail"><dt>Zona horaria</dt><dd>{profile.timeZone}</dd></div>
            <div className="account-detail"><dt>Cuenta desde</dt><dd>{new Intl.DateTimeFormat('es-ES', { dateStyle: 'long', timeZone: profile.timeZone }).format(new Date(profile.createdAt))}</dd></div>
          </dl>
        </section>
        <section className="panel" aria-labelledby="session-heading">
          <div className="panel-header"><h2 id="session-heading">Sesión</h2></div>
          <p className="muted">La sesión se guarda en una cookie segura de este navegador. Cerrar sesión también actualiza las otras pestañas abiertas.</p>
          <div className="form-actions"><SignOutButton /></div>
        </section>
      </>}
    </>
  );
}
