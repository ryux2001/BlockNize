'use client';

import { useEffect, useMemo, useState } from 'react';
import { ApiError, createMutationIntent, requestJson } from '@/lib/api/client';
import type { ActiveSelection, AgendaOccurrence, OneOffTask } from '@/lib/api/types';
import { addCivilDays, formatCivilDate, formatMinute, formatTimeRange, minuteFromTime, mondayOfWeek, todayInTimeZone } from '@/lib/dates';
import { useWorkspace } from '@/lib/workspace-context';
import { useSafeMutation } from '@/lib/use-safe-mutation';

type AgendaDay = { date: string; occurrences: AgendaOccurrence[] };
type AgendaScreenProps = { view: 'today' | 'calendar' };
type TaskDraft = {
  date: string;
  title: string;
  description: string;
  tagName: string;
  startMinute: number;
  endMinute: number;
};

export function AgendaScreen({ view }: AgendaScreenProps) {
  const { timeZone, refreshEpoch, stale, setStale } = useWorkspace();
  const today = todayInTimeZone(timeZone);
  const [date, setDate] = useState(today);
  const [days, setDays] = useState<AgendaDay[]>([]);
  const [active, setActive] = useState<ActiveSelection>(null);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<{ key: string; message: string } | null>(null);
  const [createTask, setCreateTask] = useState(false);
  const [editTask, setEditTask] = useState<{ task: OneOffTask; occurrence: AgendaOccurrence } | null>(null);
  const requestKey = `${view}:${date}:${refreshEpoch}`;
  const loading = loadedKey !== requestKey;
  const error = loadError?.key === requestKey ? loadError.message : null;

  useEffect(() => {
    let cancelled = false;
    const load = view === 'today'
      ? requestJson<AgendaOccurrence[]>(`/agenda?date=${encodeURIComponent(date)}`).then(({ data }) => [{ date, occurrences: data }])
      : requestJson<AgendaDay[]>(`/agenda/week?startDate=${encodeURIComponent(mondayOfWeek(date))}`).then(({ data }) => data);

    Promise.all([load, requestJson<ActiveSelection>('/active-selection')])
      .then(([loadedDays, selection]) => {
        if (cancelled) return;
        setDays(loadedDays);
        setActive(selection.data);
      })
      .catch((reason: unknown) => {
        if (cancelled) return;
        if (reason instanceof ApiError && reason.status === 401) window.location.replace('/iniciar-sesion');
        if (reason instanceof ApiError && [0, 503, 504].includes(reason.status)) setStale(true);
        setLoadError({ key: requestKey, message: reason instanceof Error ? reason.message : 'No se pudo cargar la agenda.' });
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(requestKey);
      });

    return () => { cancelled = true; };
  }, [date, refreshEpoch, requestKey, setStale, view]);

  const selectedDay = useMemo(() => days.find((item) => item.date === date), [date, days]);
  const displayedWeek = useMemo(() => Array.from({ length: 7 }, (_, index) => addCivilDays(mondayOfWeek(date), index)), [date]);
  const moveDate = (amount: number) => setDate((current) => addCivilDays(current, amount));
  const dateTitle = formatCivilDate(date, timeZone, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  return (
    <>
      <div className="page-heading">
        <div><h1>{view === 'today' ? 'Tu día' : 'Calendario'}</h1>
          <p>{active ? `Semana activa · ${active.snapshot?.name ?? 'Seleccionada'}` : 'Todavía no has elegido una semana activa.'}</p>
        </div>
        <div className="heading-actions">
          <button className="button button-primary" type="button" onClick={() => { setEditTask(null); setCreateTask((value) => !value); }}>Añadir temporal</button>
        </div>
      </div>

      <div className="date-controls">
        <button className="button" type="button" onClick={() => moveDate(view === 'today' ? -1 : -7)} aria-label={view === 'today' ? 'Día anterior' : 'Semana anterior'}>Anterior</button>
        <input aria-label="Elegir fecha" type="date" value={date} onChange={(event) => { if (event.currentTarget.value) setDate(event.currentTarget.value); }} />
        <button className="button" type="button" onClick={() => setDate(today)}>Hoy</button>
        <button className="button" type="button" onClick={() => moveDate(view === 'today' ? 1 : 7)} aria-label={view === 'today' ? 'Día siguiente' : 'Semana siguiente'}>Siguiente</button>
      </div>

      {view === 'calendar' && <div className="calendar-mobile-select" aria-label="Navegación diaria">
        <button className="button" type="button" onClick={() => moveDate(-1)}>Día anterior</button>
        <strong>{formatCivilDate(date, timeZone, { weekday: 'short', day: 'numeric' })}</strong>
        <button className="button" type="button" onClick={() => moveDate(1)}>Día siguiente</button>
      </div>}

      {view === 'calendar' && <div className="calendar-week" role="group" aria-label={`Semana de ${formatCivilDate(displayedWeek[0], timeZone, { day: 'numeric', month: 'long' })}`}>
        {displayedWeek.map((dayDate, index) => {
          const day = days.find((item) => item.date === dayDate);
          const count = day?.occurrences.length ?? 0;
          return <button key={dayDate} type="button" className={`calendar-day${dayDate === date ? ' selected' : ''}${dayDate === today ? ' today' : ''}`} onClick={() => setDate(dayDate)} aria-pressed={dayDate === date}>
            <span className="calendar-weekday">{['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'][index]}</span>
            <span className="calendar-number">{Number(dayDate.slice(-2))}</span>
            <span className="calendar-count">{day ? `${count} ${count === 1 ? 'bloque' : 'bloques'}` : 'Cargando'}</span>
          </button>;
        })}
      </div>}

      <section aria-labelledby="agenda-heading">
        <div className="panel-header" style={{ marginTop: 30 }}>
          <div><h2 id="agenda-heading">{view === 'today' ? dateTitle : formatCivilDate(date, timeZone, { weekday: 'long', day: 'numeric', month: 'long' })}</h2>
            <p className="muted">Agenda en {timeZone}</p>
          </div>
          <span className="badge">{selectedDay?.occurrences.length ?? 0} actividades</span>
        </div>

        {loading && <p className="status-note" role="status">Cargando la agenda…</p>}
        {!loading && error && <div className="status-note error" role="alert">{error}</div>}
        {!loading && !error && (selectedDay?.occurrences.length ?? 0) === 0 && <p className="empty-state">No hay actividades para este día.</p>}
        {!loading && !error && selectedDay && <div className="agenda-list">
          {selectedDay.occurrences.map((occurrence) => <OccurrenceCard key={occurrence.occurrenceKey} occurrence={occurrence} timeZone={timeZone} stale={stale}
            onEdit={(task) => { setCreateTask(false); setEditTask({ task, occurrence }); }} />)}
        </div>}
      </section>

      {createTask && <OneOffEditor key={`new-${date}`} initial={{ date, title: '', description: '', tagName: '', startMinute: 9 * 60, endMinute: 10 * 60 }}
        onCancel={() => setCreateTask(false)} onSaved={() => setCreateTask(false)} />}
      {editTask && <OneOffEditor key={editTask.task.id} initial={{
        date: editTask.task.date,
        title: editTask.occurrence.title,
        description: editTask.occurrence.description,
        tagName: editTask.occurrence.tagName ?? '',
        startMinute: editTask.occurrence.startMinute,
        endMinute: editTask.occurrence.endMinute,
      }} task={editTask.task} onCancel={() => setEditTask(null)} onSaved={() => setEditTask(null)} />}
    </>
  );
}

function OccurrenceCard({ occurrence, timeZone, stale, onEdit }: {
  occurrence: AgendaOccurrence;
  timeZone: string;
  stale: boolean;
  onEdit: (task: OneOffTask) => void;
}) {
  const mutation = useSafeMutation();
  const [taskVersion, setTaskVersion] = useState<number | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (occurrence.sourceType !== 'ONE_OFF') return;
    let cancelled = false;
    requestJson<OneOffTask>(`/one-off-tasks/${occurrence.sourceId}`)
      .then(({ data }) => { if (!cancelled) setTaskVersion(data.version); })
      .catch((reason: unknown) => { if (!cancelled) setDeleteError(reason instanceof Error ? reason.message : 'No se pudo cargar la tarea.'); });
    return () => { cancelled = true; };
  }, [occurrence.sourceId, occurrence.sourceType]);

  async function setCompleted(completed: boolean) {
    const intent = createMutationIntent({ method: 'POST', path: '/completions', timeZone, body: {
      date: occurrence.date,
      occurrenceKey: occurrence.occurrenceKey,
      completed,
    } });
    await mutation.send(intent);
  }

  async function edit() {
    try {
      const result = await requestJson<OneOffTask>(`/one-off-tasks/${occurrence.sourceId}`);
      setTaskVersion(result.data.version);
      onEdit({ ...result.data, version: result.data.version });
    } catch (reason) {
      setDeleteError(reason instanceof Error ? reason.message : 'No se pudo abrir la tarea.');
    }
  }

  async function remove() {
    if (taskVersion === null) return;
    const intent = createMutationIntent({ method: 'DELETE', path: `/one-off-tasks/${occurrence.sourceId}`, timeZone, version: taskVersion });
    const result = await mutation.send(intent);
    if (result) setConfirmDelete(false);
  }

  const completed = occurrence.completed;
  return (
    <article className={`agenda-item${completed ? ' agenda-completed' : ''}`}>
      <div className="agenda-time">{formatTimeRange(occurrence.startMinute, occurrence.endMinute)}</div>
      <div>
        <h2>{occurrence.title}</h2>
        {occurrence.description && <p>{occurrence.description}</p>}
        <div className="agenda-meta">
          {occurrence.tagName && <span>{occurrence.tagName}</span>}
          <span>{occurrence.sourceType === 'ONE_OFF' ? 'Temporal' : 'Semana'}</span>
          {completed && <span>Completada</span>}
          {occurrence.exceptionStatus && <span>{occurrence.exceptionStatus}</span>}
        </div>
      </div>
      <div className="agenda-item-actions">
        <button className={`button ${completed ? '' : 'button-primary'}`} type="button" disabled={stale || mutation.busy || !!occurrence.exceptionStatus} onClick={() => void setCompleted(!completed)}>
          {completed ? 'Deshacer' : 'Completar'}
        </button>
        {occurrence.sourceType === 'ONE_OFF' && <>
          <button className="button button-quiet" type="button" disabled={taskVersion === null || stale} onClick={() => void edit()}>Editar</button>
          {!confirmDelete ? <button className="text-button" type="button" disabled={taskVersion === null || stale} onClick={() => setConfirmDelete(true)}>Eliminar</button> : <>
            <span className="muted">¿Eliminar?</span>
            <button className="button button-danger" type="button" disabled={stale || mutation.busy} onClick={() => void remove()}>Confirmar</button>
            <button className="button button-quiet" type="button" onClick={() => setConfirmDelete(false)}>Cancelar</button>
          </>}
        </>}
      </div>
      {(mutation.message || deleteError) && <div className="agenda-card-message status-note error" role="alert">
        {mutation.message ?? deleteError}
        {mutation.pending && <button className="text-button" type="button" onClick={() => void mutation.retryPending()} disabled={mutation.busy}>Reintentar el mismo cambio</button>}
      </div>}
    </article>
  );
}

function OneOffEditor({ initial, task, onCancel, onSaved }: {
  initial: TaskDraft;
  task?: OneOffTask;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const mutation = useSafeMutation();
  const { setEditing } = useWorkspace();
  const [draft, setDraft] = useState(initial);
  const [endOfDay, setEndOfDay] = useState(initial.endMinute === 1440);
  const [currentVersion, setCurrentVersion] = useState(task?.version);
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const { refresh } = useWorkspace();

  useEffect(() => {
    setEditing(true);
    return () => setEditing(false);
  }, [setEditing]);

  function update(field: keyof TaskDraft, value: string | number) {
    setDraft((current) => ({ ...current, [field]: value }));
  }

  async function save(version = currentVersion) {
    if (busy || mutation.busy) return;
    const title = draft.title.trim();
    const startMinute = draft.startMinute;
    const endMinute = endOfDay ? 1440 : draft.endMinute;
    if (!title || startMinute < 0 || startMinute > 1439 || endMinute <= startMinute) {
      setLocalError('Escribe un título y un intervalo válido. El final debe ser posterior al inicio.');
      return;
    }
    if (task && version === undefined) {
      setLocalError('Actualiza la tarea antes de guardarla.');
      return;
    }

    setLocalError(null);
    setBusy(true);
    const id = task?.id ?? crypto.randomUUID();
    const intent = createMutationIntent({
      method: task ? 'PATCH' : 'POST',
      path: task ? `/one-off-tasks/${task.id}` : '/one-off-tasks',
      ...(task && version !== undefined ? { version } : {}),
      timeZone: mutation.timeZone,
      body: {
        ...(task ? {} : { id }),
        date: draft.date,
        title,
        description: draft.description.trim(),
        ...(draft.tagName.trim() ? { tagName: draft.tagName.trim() } : {}),
        startMinute,
        endMinute,
      },
    });
    const result = await mutation.send(intent);
    setBusy(false);
    if (result) onSaved();
    if (!result && task) {
      const current = await requestJson<OneOffTask>(`/one-off-tasks/${task.id}`).catch(() => null);
      if (current) setCurrentVersion(current.data.version);
    }
  }

  async function reloadCurrent() {
    if (!task) return;
    const current = await requestJson<OneOffTask>(`/one-off-tasks/${task.id}`);
    setCurrentVersion(current.data.version);
    void refresh();
    onCancel();
  }

  return (
    <section className="panel inline-form" aria-labelledby="temporal-editor-title">
      <div className="panel-header"><div><h2 id="temporal-editor-title">{task ? 'Editar tarea temporal' : 'Nueva tarea temporal'}</h2><p className="muted">Se guardará en {mutation.timeZone}.</p></div></div>
      <form onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <div className="form-grid">
          <label className="form-field"><span>Título</span><input value={draft.title} onChange={(event) => update('title', event.currentTarget.value)} maxLength={160} required /></label>
          <label className="form-field"><span>Fecha</span><input type="date" value={draft.date} onChange={(event) => update('date', event.currentTarget.value)} required /></label>
          <label className="form-field"><span>Empieza</span><input type="time" value={formatMinute(draft.startMinute)} onChange={(event) => update('startMinute', minuteFromTime(event.currentTarget.value))} required /></label>
          <div className="form-field"><span>Termina</span>
            {endOfDay ? <div className="status-note">24:00 · final del día</div> : <input type="time" value={formatMinute(draft.endMinute)} onChange={(event) => update('endMinute', minuteFromTime(event.currentTarget.value))} required />}
            <label className="checkbox-chip"><input type="checkbox" checked={endOfDay} onChange={(event) => {
              setEndOfDay(event.currentTarget.checked);
              if (!event.currentTarget.checked && draft.endMinute === 1440) update('endMinute', 1439);
            }} />Termina a las 24:00</label>
          </div>
          <label className="form-field"><span>Etiqueta (opcional)</span><input value={draft.tagName} onChange={(event) => update('tagName', event.currentTarget.value)} maxLength={80} /></label>
          <label className="form-field form-field-wide"><span>Descripción (opcional)</span><textarea value={draft.description} onChange={(event) => update('description', event.currentTarget.value)} maxLength={4000} /></label>
        </div>
        {mutation.message && <p className={`form-error${mutation.conflict ? '' : ''}`} role="alert">{mutation.message}</p>}
        {mutation.pending && <div className="form-actions"><button className="button" type="button" disabled={mutation.busy} onClick={() => void mutation.retryPending()}>{mutation.busy ? 'Comprobando…' : 'Reintentar el mismo cambio'}</button></div>}
        {mutation.conflict && <div className="status-note error" role="group" aria-label="Conflicto de versión">
          <p>El servidor confirmó que la tarea cambió. El borrador sigue aquí.</p>
          <div className="form-actions"><button className="button" type="button" onClick={() => void reloadCurrent()}>Descartar borrador y recargar</button>
            {currentVersion !== undefined && <button className="button button-primary" type="button" disabled={busy || mutation.busy} onClick={() => void save(currentVersion)}>Reaplicar mi borrador</button>}</div>
        </div>}
        {localError && <p className="form-error" role="alert">{localError}</p>}
        <div className="form-actions">
          <button className="button button-primary" type="submit" disabled={busy || mutation.busy || mutation.stale || !!mutation.pending || (task && currentVersion === undefined) || (mutation.conflict && !currentVersion)}>{task ? 'Guardar cambios' : 'Crear temporal'}</button>
          <button className="button button-quiet" type="button" onClick={onCancel}>Cancelar</button>
        </div>
      </form>
    </section>
  );
}
