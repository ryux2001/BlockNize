import type { Metadata } from 'next';
import { AuthForm } from '@/components/AuthForm';

export const metadata: Metadata = { title: 'Crear cuenta' };

export default function SignUpPage() {
  return <AuthForm mode="sign-up" />;
}
