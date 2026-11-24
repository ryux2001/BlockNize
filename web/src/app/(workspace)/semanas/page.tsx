import type { Metadata } from 'next';
import { WeeksScreen } from '@/components/WeeksScreen';

export const metadata: Metadata = { title: 'Semanas' };

export default function WeeksPage() {
  return <WeeksScreen />;
}
