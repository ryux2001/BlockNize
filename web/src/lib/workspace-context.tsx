'use client';

import { createContext, useContext } from 'react';

export type WorkspaceContextValue = {
  timeZone: string;
  refreshEpoch: number;
  stale: boolean;
  setStale: (stale: boolean) => void;
  setEditing: (editing: boolean) => void;
  refresh: () => Promise<void>;
};

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

export const WorkspaceContextProvider = WorkspaceContext.Provider;

export function useWorkspace() {
  const value = useContext(WorkspaceContext);
  if (!value) throw new Error('La vista debe estar dentro del espacio de BlockNize.');
  return value;
}
