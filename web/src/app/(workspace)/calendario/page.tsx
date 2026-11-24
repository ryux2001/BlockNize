import type { Metadata } from 'next';
import { AgendaScreen } from '@/components/AgendaScreen';

export const metadata: Metadata = { title: 'Calendario' };

export default function CalendarPage() {
  return <AgendaScreen view="calendar" />;
}
