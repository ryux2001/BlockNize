export type UserProfile = {
  id: string;
  email: string | null;
  timeZone: string;
  createdAt: string;
};

export type ScheduleBlock = {
  id: string;
  taskId: string;
  templateId: string;
  title: string;
  description: string;
  tagName: string | null;
  daysMask: number;
  startMinute: number;
  endMinute: number;
};

export type ScheduleTemplate = {
  id: string;
  name: string;
  version: number;
  active: boolean;
  blocks?: ScheduleBlock[];
};

export type ActiveSelection = {
  templateId: string;
  version: number;
  effectiveAt: string;
  snapshot?: { name?: string } | null;
} | null;

export type AgendaOccurrence = {
  occurrenceKey: string;
  sourceType: 'WEEKLY' | 'ONE_OFF';
  sourceId: string;
  date: string;
  title: string;
  description: string;
  tagName: string | null;
  startMinute: number;
  endMinute: number;
  start: string;
  end: string;
  templateId: string | null;
  completed: boolean;
  historical: boolean;
  exceptionStatus?: string;
};

export type HistoryEntry = {
  date: string;
  occurrences: AgendaOccurrence[];
};

export type HistoryPage = {
  entries: HistoryEntry[];
  nextDate: string | null;
};

export type OneOffTask = {
  id: string;
  date: string;
  title: string;
  description: string;
  tagId: string | null;
  startMinute: number;
  endMinute: number;
  version: number;
};

export type SyncState = {
  cursor: string;
  protocolVersion: number;
};
