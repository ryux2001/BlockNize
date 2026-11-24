import type { Metadata } from 'next';
import { AgendaScreen } from '@/components/AgendaScreen';

export const metadata: Metadata = { title: 'Hoy' };

export default function TodayPage() {
  return <AgendaScreen view="today" />;
}
