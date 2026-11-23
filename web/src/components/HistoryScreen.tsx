'use client';

import { FormEvent, useEffect, useState } from 'react';
import { isUncertainResult, requestJson } from '@/lib/api/client';
import type { AgendaOccurrence, HistoryEntry, HistoryPage } from '@/lib/api/types';
import { addCivilDays, formatCivilDate, formatTimeRange, todayInTimeZone } from '@/lib/dates';
import { useWorkspace } from '@/lib/workspace-context';

function dayDistance(first: string, last: string) {
  return Math.floor((Date.parse(`${last}T00:00:00Z`) - Date.parse(`${first}T00:00:00Z`)) / 86_400_000);
}

export function HistoryScreen() {
  const { timeZone, refreshEpoch, stale, setStale } = useWorkspace();
  const today = todayInTimeZone(timeZone);
  const initialRange = { from: addCivilDays(today, -6), to: today };
  const [range, setRange] = useState(initialRange);
  const [draftFrom, setDraftFrom] = useState(initialRange.from);
  const [draftTo, setDraftTo] = useState(initialRange.to);
  const [entries, setEntries] = useState<HistoryEntry[]>([]);
  const [nextDate, setNextDate] = useState<string | null>(null);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [loadError, setLoadError] = useState<{ key: string; message: string } | null>(null);
  const [pageError, setPageError] = useState<{ key: string; message: string } | null>(null);
  const [rangeError, setRangeError] = useState<string | null>(null);
  const queryKey = `${range.from}:${range.to}:${refreshEpoch}`;
  const loading = loadedKey !== queryKey;
  const error = loadError?.key === queryKey ? loadError.message
    : pageError?.key === queryKey ? pageError.message : null;

  useEffect(() => {
    let cancelled = false;
    requestJson<HistoryPage>(`/history?from=${encodeURIComponent(range.from)}&to=${encodeURIComponent(range.to)}&limit=7`)
      .then(({ data }) => {
        if (!cancelled) {
          setEntries(data.entries);
          setNextDate(data.nextDate);
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          if (isUncertainResult(reason)) setStale(true);
          setLoadError({ key: queryKey, message: reason instanceof Error ? reason.message : 'No se pudo cargar el registro.' });
        }
      })
      .finally(() => { if (!cancelled) setLoadedKey(queryKey); });
    return () => { cancelled = true; };
  }, [range, queryKey, refreshEpoch, setStale]);

  async function loadOlder() {
    if (!nextDate || loadingOlder) return;
    setLoadingOlder(true);
    setPageError(null);
    try {
      const page = await requestJson<HistoryPage>(`/history?from=${encodeURIComponent(nextDate)}&to=${encodeURIComponent(range.to)}&limit=7`);
      setEntries((current) => [...current, ...page.data.entries]);
      setNextDate(page.data.nextDate);
    } catch (reason) {
      if (isUncertainResult(reason)) setStale(true);
      setPageError({ key: queryKey, message: reason instanceof Error ? reason.message : 'No se pudo cargar el historial anterior.' });
    } finally {
      setLoadingOlder(false);
    }
  }

  function changeRange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const distance = dayDistance(draftFrom, draftTo);
    if (!Number.isFinite(distance)) {
      setRangeError('Elige dos fechas válidas.');
      return;
    }
    if (distance < 0) {
      setRangeError('La fecha inicial debe ser anterior a la final.');
      return;
    }
    if (distance > 30) {
      setRangeError('El registro se consulta en intervalos de hasta 31 días.');
      return;
    }
    setRangeError(null);
    setRange({ from: draftFrom, to: draftTo });
  }

  return (
    <>
      <div className="page-heading"><div><h1>Registro</h1><p>Actividad y ocurrencias en {timeZone}.</p></div></div>
      <form className="panel" onSubmit={changeRange}>
        <div className="form-grid">
          <label className="form-field"><span>Desde</span><input type="date" value={draftFrom} onChange={(event) => setDraftFrom(event.currentTarget.value)} required /></label>
          <label className="form-field"><span>Hasta</span><input type="date" value={draftTo} onChange={(event) => setDraftTo(event.currentTarget.value)} required /></label>
        </div>
        {rangeError && <p className="form-error" role="alert">{rangeError}</p>}
        <div className="form-actions"><button className="button button-primary" type="submit" disabled={stale}>Ver registro</button></div>
      </form>

      {loading && <p className="status-note" role="status" style={{ marginTop: 18 }}>Cargando el registro…</p>}
      {error && <p className="status-note error" role="alert" style={{ marginTop: 18 }}>{error}</p>}
      {!loading && !error && entries.length === 0 && <p className="empty-state">No hay actividad en este intervalo.</p>}

      {!loading && entries.map((entry) => <section className="history-day" key={entry.date} aria-labelledby={`history-${entry.date}`}>
        <div className="history-day-heading"><h2 id={`history-${entry.date}`}>{formatCivilDate(entry.date, timeZone, { weekday: 'long', day: 'numeric', month: 'long' })}</h2>
          <span className="history-summary">{summary(entry.occurrences)}</span>
        </div>
        {entry.occurrences.length === 0 ? <p className="muted">Sin ocurrencias.</p> : <div className="agenda-list">
          {entry.occurrences.map((occurrence) => <HistoryOccurrence key={occurrence.occurrenceKey} occurrence={occurrence} />)}
        </div>}
      </section>)}

      {nextDate && <div className="form-actions"><button className="button" type="button" onClick={() => void loadOlder()} disabled={loadingOlder || stale}>
        {loadingOlder ? 'Cargando…' : 'Ver más fechas'}
      </button></div>}
    </>
  );
}

function HistoryOccurrence({ occurrence }: { occurrence: AgendaOccurrence }) {
  const status = occurrence.exceptionStatus === 'SKIP' ? 'Omitida'
    : occurrence.exceptionStatus === 'REPLACE' ? 'Modificada'
      : occurrence.completed ? 'Completada' : 'Sin completar';

  return <article className={`agenda-item${occurrence.completed ? ' agenda-completed' : ''}`}>
    <div className="agenda-time">{formatTimeRange(occurrence.startMinute, occurrence.endMinute)}</div>
    <div><h2>{occurrence.title}</h2>{occurrence.description && <p>{occurrence.description}</p>}
      <div className="agenda-meta">{occurrence.tagName && <span>{occurrence.tagName}</span>}<span>{occurrence.sourceType === 'ONE_OFF' ? 'Temporal' : 'Semana'}</span></div>
    </div>
    <span className="badge">{status}</span>
  </article>;
}

function summary(occurrences: AgendaOccurrence[]) {
  const completed = occurrences.filter((item) => item.completed).length;
  const skipped = occurrences.filter((item) => item.exceptionStatus === 'SKIP').length;
  const pending = occurrences.length - completed - skipped;
  return `${completed} completadas · ${skipped} omitidas · ${pending} sin completar`;
}
