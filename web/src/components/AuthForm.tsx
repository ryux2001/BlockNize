'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FormEvent, useState } from 'react';
import { authClient } from '@/lib/auth/client';
import { ApiError, requestJson } from '@/lib/api/client';
import type { UserProfile } from '@/lib/api/types';

type AuthFormProps = { mode: 'sign-in' | 'sign-up' };

export function AuthForm({ mode }: AuthFormProps) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const signingUp = mode === 'sign-up';

  async function connectToAgenda(email: string) {
    const [session, profileResult] = await Promise.all([
      authClient.getSession(),
      requestJson<UserProfile>('/me'),
    ]);
    const sessionEmail = session.data?.user?.email;
    if (session.error || !session.data?.user) {
      throw new Error('Neon Auth no devolvió una sesión web. Comprueba su configuración y el origen permitido.');
    }
    if (profileResult.data.email && sessionEmail && profileResult.data.email.toLowerCase() !== sessionEmail.toLowerCase()) {
      await authClient.signOut();
      throw new Error('La identidad de Neon Auth no coincide con la cuenta de la API. Vuelve a iniciar sesión con la cuenta correcta.');
    }
    if (email && sessionEmail && email.toLowerCase() !== sessionEmail.toLowerCase()) {
      throw new Error('Neon Auth devolvió una identidad distinta a la dirección indicada.');
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setMessage(null);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get('email') ?? '').trim();
    const password = String(formData.get('password') ?? '');

    try {
      if (signingUp) {
        const name = String(formData.get('name') ?? '').trim();
        const response = await authClient.signUp.email({ name, email, password });
        if (response.error) throw new Error(response.error.message || 'No se pudo crear la cuenta.');
        if (!response.data?.user?.emailVerified) {
          setMessage({ text: 'Revisa tu correo para completar la verificación de la cuenta. Después podrás iniciar sesión.', error: false });
          setBusy(false);
          return;
        }
      } else {
        const response = await authClient.signIn.email({ email, password, rememberMe: true });
        if (response.error) throw new Error(response.error.message || 'No se pudo iniciar sesión.');
      }

      try {
        await connectToAgenda(email);
      } catch (error) {
        if (error instanceof ApiError && [0, 503, 504].includes(error.status)) {
          throw new Error('La sesión se creó, pero la API no responde. Comprueba el servidor NestJS y vuelve a intentarlo.');
        }
        if (error instanceof ApiError && error.status === 401) {
          throw new Error('Neon Auth aceptó la sesión, pero la API NestJS rechazó su JWT. Revisa el emisor y el audience de la rama Neon.');
        }
        throw error;
      }

      router.replace('/hoy');
      router.refresh();
    } catch (error) {
      setMessage({ text: error instanceof Error ? error.message : 'No se pudo completar el acceso.', error: true });
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="auth-title">
        <Link href="/" className="brand"><span className="brand-mark" aria-hidden="true"><svg viewBox="0 0 28 28" fill="none"><path d="M4 5h8v8H4zM16 5h8v5h-8zM16 14h8v9h-8zM4 17h8v6H4z" fill="currentColor" /></svg></span><span>BlockNize</span></Link>
        <h1 id="auth-title">{signingUp ? 'Crear cuenta' : 'Iniciar sesión'}</h1>
        <p>{signingUp ? 'Usa tu cuenta para consultar la agenda desde este navegador.' : 'Continúa con la cuenta que ya usas en BlockNize.'}</p>
        <form className="auth-form" onSubmit={submit}>
          {signingUp && <label className="form-field"><span>Nombre</span><input name="name" autoComplete="name" maxLength={80} required /></label>}
          <label className="form-field"><span>Correo electrónico</span><input name="email" type="email" autoComplete="email" maxLength={255} required /></label>
          <label className="form-field"><span>Contraseña</span><input name="password" type="password" autoComplete={signingUp ? 'new-password' : 'current-password'} minLength={8} required /></label>
          <button className="button button-primary" type="submit" disabled={busy}>{busy ? 'Conectando…' : signingUp ? 'Crear cuenta' : 'Iniciar sesión'}</button>
        </form>
        {message && <p className={`auth-message status-note ${message.error ? 'error' : 'success'}`} role={message.error ? 'alert' : 'status'}>{message.text}</p>}
        <p className="auth-footer">{signingUp ? '¿Ya tienes una cuenta?' : '¿Todavía no tienes cuenta?'}{' '}
          <Link href={signingUp ? '/iniciar-sesion' : '/crear-cuenta'}>{signingUp ? 'Iniciar sesión' : 'Crear cuenta'}</Link>
        </p>
      </section>
    </main>
  );
}
