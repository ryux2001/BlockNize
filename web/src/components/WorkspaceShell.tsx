'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { authClient } from '@/lib/auth/client';
import { ApiError, requestJson } from '@/lib/api/client';
import type { SyncState, UserProfile } from '@/lib/api/types';
import { WorkspaceContextProvider } from '@/lib/workspace-context';

const navigation = [
  { href: '/hoy', label: 'Hoy', symbol: 'sun' },
  { href: '/calendario', label: 'Calendario', symbol: 'calendar' },
  { href: '/semanas', label: 'Semanas', symbol: 'layers' },
  { href: '/registro', label: 'Registro', symbol: 'history' },
  { href: '/cuenta', label: 'Cuenta', symbol: 'user' },
] as const;

function NavIcon({ name }: { name: string }) {
  const paths: Record<string, React.ReactNode> = {
    sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" /></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 10h18" /></>,
    layers: <><path d="m12 3 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5M3 16l9 5 9-5" /></>,
    history: <><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5m4-1v5l3 2" /></>,
    user: <><circle cx="12" cy="8" r="4" /><path d="M5 21v-2a7 7 0 0 1 14 0v2" /></>,
  };

  return <svg aria-hidden="true" viewBox="0 0 24 24" className="nav-icon" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>;
}

function RefreshIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="button-icon" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M20 7v5h-5M4 17v-5h5" /><path d="M5.6 9a7 7 0 0 1 11.6-2L20 12M4 12l2.8 5a7 7 0 0 0 11.6-2" /></svg>;
}

export function WorkspaceShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [refreshEpoch, setRefreshEpoch] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [stale, setStale] = useState(false);
  const editingRef = useRef(false);
  const needsRefreshRef = useRef(false);
  const sessionIdentity = useRef<string | null>(null);

  const setWorkspaceEditing = useCallback((nextEditing: boolean) => {
    editingRef.current = nextEditing;
    if (!nextEditing && needsRefreshRef.current) {
      needsRefreshRef.current = false;
      setRefreshEpoch((value) => value + 1);
    }
  }, []);

  const refresh = useCallback(async () => {
    try {
      const result = await requestJson<SyncState>('/sync/state');
      setStale(false);
      if (cursor !== null && cursor !== result.data.cursor) {
        if (editingRef.current) needsRefreshRef.current = true;
        else setRefreshEpoch((value) => value + 1);
      }
      setCursor(result.data.cursor);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        window.location.replace('/iniciar-sesion');
        return;
      }
      setStale(true);
    }
  }, [cursor]);

  const context = useMemo(() => ({
    timeZone: profile?.timeZone ?? 'UTC',
    refreshEpoch,
    stale,
    setStale,
    setEditing: setWorkspaceEditing,
    refresh,
  }), [profile?.timeZone, refreshEpoch, stale, refresh, setWorkspaceEditing]);

  useEffect(() => {
    let cancelled = false;
    requestJson<UserProfile>('/me')
      .then(({ data }) => {
        if (!cancelled) {
          setProfile(data);
          setProfileError(null);
        }
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof ApiError && error.status === 401) {
          window.location.replace('/iniciar-sesion');
        } else {
          setProfileError(error instanceof Error ? error.message : 'No se pudo cargar la cuenta.');
        }
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!profile) return;
    const checkSession = async () => {
      if (document.visibilityState !== 'visible') return;
      let result;
      try {
        result = await authClient.getSession();
      } catch {
        setStale(true);
        return;
      }
      if (result.error) {
        setStale(true);
        return;
      }
      const user = result.data?.user;
      if (!user) {
        window.location.replace('/iniciar-sesion');
        return;
      }
      if (sessionIdentity.current && sessionIdentity.current !== user.id) {
        window.location.replace('/hoy');
        return;
      }
      sessionIdentity.current = user.id;
      await refresh();
    };
    void checkSession();
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refresh();
    }, 30_000);
    window.addEventListener('focus', checkSession);
    window.addEventListener('pageshow', checkSession);
    document.addEventListener('visibilitychange', checkSession);
    window.addEventListener('online', checkSession);
    const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('blocknize-session');
    channel?.addEventListener('message', () => window.location.replace('/iniciar-sesion'));
    const markOffline = () => setStale(true);
    window.addEventListener('offline', markOffline);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', checkSession);
      window.removeEventListener('pageshow', checkSession);
      document.removeEventListener('visibilitychange', checkSession);
      window.removeEventListener('online', checkSession);
      window.removeEventListener('offline', markOffline);
      channel?.close();
    };
  }, [profile, refresh]);

  if (profileError) {
    return <main className="setup-state"><div className="setup-card"><Brand /><h1>La agenda no está disponible</h1><p>{profileError}</p><button className="button button-primary" onClick={() => { setProfileError(null); router.refresh(); }}>Volver a intentar</button></div></main>;
  }
  if (!profile) return <main className="loading-state" aria-live="polite"><Brand /><p>Cargando tu agenda…</p></main>;

  return (
    <WorkspaceContextProvider value={context}>
      <div className="workspace">
        <aside className="sidebar">
          <Brand />
          <nav className="navigation" aria-label="Secciones">
            {navigation.map((item) => <Link key={item.href} href={item.href} className={`nav-link${pathname === item.href ? ' active' : ''}`} aria-current={pathname === item.href ? 'page' : undefined}>
              <NavIcon name={item.symbol} /><span>{item.label}</span>
            </Link>)}
          </nav>
          <p className="sidebar-account" title={profile.email ?? undefined}>{profile.email ?? 'Cuenta de BlockNize'}</p>
        </aside>
        <div className="workspace-main">
          <header className="topbar">
            <span className="topbar-title">{navigation.find((item) => item.href === pathname)?.label ?? 'BlockNize'}</span>
            <button className="button button-quiet" onClick={() => void refresh()} disabled={stale && typeof navigator !== 'undefined' && !navigator.onLine}>
              <RefreshIcon /><span>Actualizar</span>
            </button>
          </header>
          {stale && <div className="stale-banner" role="status">Sin conexión con la agenda. Los datos mostrados pueden estar desactualizados.</div>}
          <main className="page-content">{children}</main>
        </div>
        <nav className="mobile-navigation" aria-label="Secciones">
          {navigation.map((item) => <Link key={item.href} href={item.href} className={`mobile-nav-link${pathname === item.href ? ' active' : ''}`} aria-current={pathname === item.href ? 'page' : undefined}>
            <NavIcon name={item.symbol} /><span>{item.label}</span>
          </Link>)}
        </nav>
      </div>
    </WorkspaceContextProvider>
  );
}

function Brand() {
  return <Link href="/hoy" className="brand" aria-label="BlockNize, ir a Hoy"><span className="brand-mark" aria-hidden="true"><svg viewBox="0 0 28 28" fill="none"><path d="M4 5h8v8H4zM16 5h8v5h-8zM16 14h8v9h-8zM4 17h8v6H4z" fill="currentColor" /></svg></span><span>BlockNize</span></Link>;
}
