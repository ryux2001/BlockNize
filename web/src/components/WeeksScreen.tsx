'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { createMutationIntent, isUncertainResult, requestJson } from '@/lib/api/client';
import type { ActiveSelection, ScheduleBlock, ScheduleTemplate } from '@/lib/api/types';
import { formatMinute, minuteFromTime } from '@/lib/dates';
import { useWorkspace } from '@/lib/workspace-context';
import { useSafeMutation } from '@/lib/use-safe-mutation';

const weekdays = [
  { label: 'Lunes', short: 'L', bit: 1 },
  { label: 'Martes', short: 'M', bit: 2 },
  { label: 'Miércoles', short: 'X', bit: 4 },
  { label: 'Jueves', short: 'J', bit: 8 },
  { label: 'Viernes', short: 'V', bit: 16 },
  { label: 'Sábado', short: 'S', bit: 32 },
  { label: 'Domingo', short: 'D', bit: 64 },
];

export function WeeksScreen() {
  const { refreshEpoch, setStale } = useWorkspace();
  const activation = useSafeMutation();
  const [templates, setTemplates] = useState<ScheduleTemplate[]>([]);
  const [selection, setSelection] = useState<ActiveSelection>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ScheduleTemplate | null>(null);
  const [loadedEpoch, setLoadedEpoch] = useState<number | null>(null);
  const [loadError, setLoadError] = useState<{ epoch: number; message: string } | null>(null);
  const [selectionError, setSelectionError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [editingBlock, setEditingBlock] = useState<ScheduleBlock | null>(null);
  const [addingBlock, setAddingBlock] = useState(false);
  const selectedIdRef = useRef<string | null>(null);
  const loading = loadedEpoch !== refreshEpoch;
  const error = loadError?.epoch === refreshEpoch ? loadError.message : selectionError;

  async function loadTemplates(preferredId = selectedId) {
    const [templateResult, activeResult] = await Promise.all([
      requestJson<ScheduleTemplate[]>('/templates'),
      requestJson<ActiveSelection>('/active-selection'),
    ]);
    setTemplates(templateResult.data);
    setSelection(activeResult.data);
    const nextId = preferredId && templateResult.data.some((item) => item.id === preferredId)
      ? preferredId
      : activeResult.data?.templateId ?? templateResult.data[0]?.id ?? null;
    setSelectedId(nextId);
    selectedIdRef.current = nextId;
    if (nextId) {
      const detailResult = await requestJson<ScheduleTemplate>(`/templates/${nextId}`);
      setDetail(detailResult.data);
    } else {
      setDetail(null);
    }
  }

  useEffect(() => {
    let cancelled = false;
    Promise.all([requestJson<ScheduleTemplate[]>('/templates'), requestJson<ActiveSelection>('/active-selection')])
      .then(async ([templateResult, activeResult]) => {
        if (cancelled) return;
        setTemplates(templateResult.data);
        setSelection(activeResult.data);
        const preferredId = selectedIdRef.current;
        const nextId = preferredId && templateResult.data.some((item) => item.id === preferredId)
          ? preferredId
          : activeResult.data?.templateId ?? templateResult.data[0]?.id ?? null;
        setSelectedId(nextId);
        selectedIdRef.current = nextId;
        setLoadError(null);
        if (nextId) {
          const result = await requestJson<ScheduleTemplate>(`/templates/${nextId}`);
          if (!cancelled) setDetail(result.data);
        } else {
          setDetail(null);
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          if (isUncertainResult(reason)) setStale(true);
          setLoadError({ epoch: refreshEpoch, message: reason instanceof Error ? reason.message : 'No se pudieron cargar las semanas.' });
        }
      })
      .finally(() => { if (!cancelled) setLoadedEpoch(refreshEpoch); });
    return () => { cancelled = true; };
  }, [refreshEpoch, setStale]);

  const selectedTemplate = useMemo(() => templates.find((template) => template.id === selectedId) ?? null, [selectedId, templates]);

  async function chooseTemplate(id: string) {
    setSelectionError(null);
    setSelectedId(id);
    selectedIdRef.current = id;
    setDetail(null);
    setEditingName(false);
    setEditingBlock(null);
    setAddingBlock(false);
    try {
      const result = await requestJson<ScheduleTemplate>(`/templates/${id}`);
      setDetail(result.data);
    } catch (reason) {
      setSelectionError(reason instanceof Error ? reason.message : 'No se pudo abrir la semana.');
    }
  }

  async function activate() {
    if (!selectedTemplate || activation.stale || activation.busy || activation.pending) return;
    const intent = createMutationIntent({
      method: 'PUT',
      path: '/active-selection',
      version: selection?.version ?? 0,
      timeZone: activation.timeZone,
      body: { templateId: selectedTemplate.id },
    });
    const result = await activation.send<{ version: number; templateId: string }>(intent);
    if (result) {
      setSelection({ templateId: result.data.templateId, version: result.data.version, effectiveAt: new Date().toISOString() });
      await loadTemplates(selectedTemplate.id);
    }
  }

  return (
    <>
      <div className="page-heading"><div><h1>Semanas</h1><p>Elige la rutina y organiza sus bloques.</p></div>
        <div className="heading-actions"><button className="button button-primary" type="button" onClick={() => { setCreating((value) => !value); setEditingName(false); }}>Nueva semana</button></div>
      </div>

      {error && <p className="status-note error" role="alert">{error}</p>}
      {creating && <TemplateNameForm mode="create" onCancel={() => setCreating(false)} onSaved={async (template) => {
        setCreating(false);
        await loadTemplates(template.id);
      }} />}

      {loading && <p className="status-note" role="status">Cargando semanas…</p>}
      {!loading && templates.length === 0 && !creating && <section className="panel empty-state">
        <h2>Aún no hay semanas</h2><p>Crea una semana para añadir tus bloques y usarla en la agenda.</p>
        <button className="button button-primary" type="button" onClick={() => setCreating(true)}>Crear semana</button>
      </section>}
      {!loading && templates.length > 0 && <>
        <section className="panel" aria-labelledby="templates-heading">
          <div className="panel-header"><h2 id="templates-heading">Tus semanas</h2><span className="badge">{templates.length}</span></div>
          <div className="template-list">
            {templates.map((template) => <div className={`template-row${template.id === selectedId ? ' selected' : ''}`} key={template.id}>
              <div className="template-main"><button type="button" onClick={() => void chooseTemplate(template.id)}>{template.name}</button>
                {template.id === selection?.templateId && <span className="badge badge-active">Activa</span>}
              </div>
              <div className="template-actions">
                {template.id !== selection?.templateId && <button className="button button-quiet" type="button" onClick={() => { void chooseTemplate(template.id); }}>Editar</button>}
                {template.id === selection?.templateId && <span className="muted">Seleccionada para la agenda</span>}
              </div>
            </div>)}
          </div>
          <p className="muted" style={{ marginTop: 16 }}>La API todavía no ofrece una operación para borrar semanas.</p>
        </section>

        {selectedTemplate && detail && <section className="panel" aria-labelledby="week-detail-heading">
          <div className="panel-header"><div><h2 id="week-detail-heading">{detail.name}</h2><p className="muted">{detail.blocks?.length ?? 0} bloques</p></div>
            <div className="action-row">
              {selection?.templateId !== detail.id && <button className="button button-primary" type="button" disabled={activation.stale || activation.busy || !!activation.pending} onClick={() => void activate()}>Usar esta semana</button>}
              <button className="button button-quiet" type="button" onClick={() => { setEditingName((value) => !value); setEditingBlock(null); setAddingBlock(false); }}>{editingName ? 'Cancelar nombre' : 'Cambiar nombre'}</button>
              <button className="button" type="button" onClick={() => { setAddingBlock((value) => !value); setEditingBlock(null); setEditingName(false); }}>{addingBlock ? 'Cancelar bloque' : 'Añadir bloque'}</button>
            </div>
          </div>
          {activation.message && <div className="status-note error" role="alert">{activation.message}</div>}
          {activation.pending && <div className="form-actions"><button className="button" type="button" disabled={activation.busy} onClick={async () => {
            const result = await activation.retryPending<{ version: number; templateId: string }>();
            if (result) await loadTemplates(selectedTemplate.id);
          }}>Reintentar la misma activación</button></div>}

          {editingName && <TemplateNameForm mode="rename" template={detail} onCancel={() => setEditingName(false)} onSaved={async () => {
            setEditingName(false);
            await loadTemplates(detail.id);
          }} />}
          {addingBlock && <BlockForm templateId={detail.id} version={detail.version} onCancel={() => setAddingBlock(false)} onSaved={async () => {
            setAddingBlock(false);
            await loadTemplates(detail.id);
          }} onReload={async () => {
            await loadTemplates(detail.id);
            setAddingBlock(false);
          }} />}

          {!addingBlock && !editingBlock && (detail.blocks?.length ? <div>
            {detail.blocks.map((block) => <div className="block-row" key={block.id}>
              <span className="block-days">{daysLabel(block.daysMask)}</span>
              <div><div className="block-title">{formatMinute(block.startMinute)}–{formatMinute(block.endMinute)} · {block.title}</div>
                {block.tagName && <div className="block-tag">{block.tagName}</div>}
                {block.description && <div className="muted">{block.description}</div>}
              </div>
              <div className="action-row"><button className="button button-quiet" type="button" onClick={() => { setEditingBlock(block); setEditingName(false); }}>Editar</button>
                <DeleteBlockButton templateId={detail.id} block={block} version={detail.version} onDeleted={() => void loadTemplates(detail.id)} />
              </div>
            </div>)}
          </div> : <p className="empty-state">Esta semana todavía no tiene bloques.</p>)}

          {editingBlock && <BlockForm key={editingBlock.id} templateId={detail.id} version={detail.version} block={editingBlock}
            onCancel={() => setEditingBlock(null)} onSaved={async () => {
              setEditingBlock(null);
              await loadTemplates(detail.id);
            }} onReload={async () => {
              await loadTemplates(detail.id);
              setEditingBlock(null);
            }} />}
        </section>}
      </>}
    </>
  );
}

function TemplateNameForm({ mode, template, onCancel, onSaved }: {
  mode: 'create' | 'rename';
  template?: ScheduleTemplate;
  onCancel: () => void;
  onSaved: (template: ScheduleTemplate) => void | Promise<void>;
}) {
  const mutation = useSafeMutation();
  const { setEditing } = useWorkspace();
  const [name, setName] = useState(template?.name ?? '');
  const [currentVersion, setCurrentVersion] = useState(template?.version);

  useEffect(() => { setEditing(true); return () => setEditing(false); }, [setEditing]);

  async function save(version = currentVersion) {
    const cleanName = name.trim();
    if (!cleanName) return;
    const id = template?.id ?? crypto.randomUUID();
    const intent = createMutationIntent({
      method: mode === 'create' ? 'POST' : 'PATCH',
      path: mode === 'create' ? '/templates' : `/templates/${id}`,
      ...(mode === 'rename' && version !== undefined ? { version } : {}),
      timeZone: mutation.timeZone,
      body: { ...(mode === 'create' ? { id } : {}), name: cleanName },
    });
    const result = await mutation.send<ScheduleTemplate>(intent);
    if (result) await onSaved(result.data);
    else if (template) {
      const current = await requestJson<ScheduleTemplate>(`/templates/${template.id}`).catch(() => null);
      if (current) setCurrentVersion(current.data.version);
    }
  }

  return <form className="inline-form" onSubmit={(event) => { event.preventDefault(); void save(); }}>
    <label className="form-field"><span>{mode === 'create' ? 'Nombre de la semana' : 'Nuevo nombre'}</span><input value={name} onChange={(event) => setName(event.currentTarget.value)} maxLength={120} required autoFocus /></label>
    {mutation.message && <p className="form-error" role="alert">{mutation.message}</p>}
    {mutation.pending && <div className="form-actions"><button className="button" type="button" onClick={async () => {
      const result = await mutation.retryPending<ScheduleTemplate>();
      if (result) await onSaved(result.data);
    }} disabled={mutation.busy}>Reintentar el mismo cambio</button></div>}
    {mutation.conflict && <div className="status-note error" role="group"><p>La semana cambió. Puedes reaplicar el nombre sobre la versión actual.</p>
      <button className="button button-primary" type="button" disabled={mutation.busy || currentVersion === undefined} onClick={() => void save(currentVersion)}>Reaplicar nombre</button>
    </div>}
    <div className="form-actions"><button className="button button-primary" type="submit" disabled={mutation.busy || mutation.stale || !!mutation.pending || (mode === 'rename' && currentVersion === undefined)}>{mode === 'create' ? 'Crear semana' : 'Guardar nombre'}</button>
      <button className="button button-quiet" type="button" onClick={onCancel} disabled={!!mutation.pending}>Cancelar</button></div>
  </form>;
}

function BlockForm({ templateId, version, block, onCancel, onSaved, onReload }: {
  templateId: string;
  version: number;
  block?: ScheduleBlock;
  onCancel: () => void;
  onSaved: () => void | Promise<void>;
  onReload: () => void | Promise<void>;
}) {
  const mutation = useSafeMutation();
  const { setEditing } = useWorkspace();
  const [title, setTitle] = useState(block?.title ?? '');
  const [description, setDescription] = useState(block?.description ?? '');
  const [tagName, setTagName] = useState(block?.tagName ?? '');
  const [daysMask, setDaysMask] = useState(block?.daysMask ?? 1);
  const [start, setStart] = useState(formatMinute(block?.startMinute ?? 9 * 60));
  const [end, setEnd] = useState(formatMinute(block?.endMinute ?? 10 * 60));
  const [endOfDay, setEndOfDay] = useState(block?.endMinute === 1440);
  const [currentVersion, setCurrentVersion] = useState(version);
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => { setEditing(true); return () => setEditing(false); }, [setEditing]);

  function toggleDay(bit: number, checked: boolean) {
    setDaysMask((current) => checked ? current | bit : current & ~bit);
  }

  async function save(nextVersion = currentVersion) {
    const startMinute = minuteFromTime(start);
    const endMinute = endOfDay ? 1440 : minuteFromTime(end);
    if (!title.trim() || daysMask === 0 || endMinute <= startMinute) {
      setLocalError('Añade un nombre, elige al menos un día y usa un intervalo válido.');
      return;
    }
    setLocalError(null);
    const id = block?.id ?? crypto.randomUUID();
    const intent = createMutationIntent({
      method: block ? 'PATCH' : 'POST',
      path: block ? `/templates/${templateId}/blocks/${block.id}` : `/templates/${templateId}/blocks`,
      version: nextVersion,
      timeZone: mutation.timeZone,
      body: {
        id,
        ...(block ? { taskId: block.taskId } : {}),
        title: title.trim(),
        description: description.trim(),
        ...(tagName.trim() ? { tagName: tagName.trim() } : {}),
        daysMask,
        startMinute,
        endMinute,
      },
    });
    const result = await mutation.send<ScheduleTemplate>(intent);
    if (result) await onSaved();
    else if (block) {
      const latest = await requestJson<ScheduleTemplate>(`/templates/${templateId}`).catch(() => null);
      if (latest) setCurrentVersion(latest.data.version);
    }
  }

  function reloadVersion() {
    void onReload();
  }

  return <form className="inline-form" onSubmit={(event) => { event.preventDefault(); void save(); }}>
    <div className="form-grid">
      <label className="form-field"><span>Nombre del bloque</span><input value={title} onChange={(event) => setTitle(event.currentTarget.value)} maxLength={160} required autoFocus /></label>
      <label className="form-field"><span>Etiqueta (opcional)</span><input value={tagName} onChange={(event) => setTagName(event.currentTarget.value)} maxLength={80} /></label>
      <fieldset className="form-field form-field-wide" style={{ padding: 0, border: 0 }}><legend className="field-label" style={{ marginBottom: 8 }}>Días</legend>
        <div className="checkbox-list">{weekdays.map((day) => <label key={day.bit} className="checkbox-chip"><input type="checkbox" checked={(daysMask & day.bit) !== 0} onChange={(event) => toggleDay(day.bit, event.currentTarget.checked)} />{day.label}</label>)}</div>
      </fieldset>
      <label className="form-field"><span>Empieza</span><input type="time" value={start} onChange={(event) => setStart(event.currentTarget.value)} required /></label>
      <div className="form-field"><span>Termina</span>
        {endOfDay ? <div className="status-note">24:00 · final del día</div> : <input type="time" value={end} onChange={(event) => setEnd(event.currentTarget.value)} required />}
        <label className="checkbox-chip"><input type="checkbox" checked={endOfDay} onChange={(event) => {
          setEndOfDay(event.currentTarget.checked);
          if (!event.currentTarget.checked && end === '24:00') setEnd('23:59');
        }} />Termina a las 24:00</label>
      </div>
      <label className="form-field form-field-wide"><span>Descripción (opcional)</span><textarea value={description} onChange={(event) => setDescription(event.currentTarget.value)} maxLength={4000} /></label>
    </div>
    {localError && <p className="form-error" role="alert">{localError}</p>}
    {mutation.message && <p className="form-error" role="alert">{mutation.message}</p>}
    {mutation.pending && <div className="form-actions"><button className="button" type="button" disabled={mutation.busy} onClick={async () => {
      const result = await mutation.retryPending<ScheduleTemplate>();
      if (result) await onSaved();
    }}>Reintentar el mismo cambio</button></div>}
    {mutation.conflict && <div className="status-note error" role="group"><p>La semana cambió en otro dispositivo. El bloque sigue como borrador.</p>
      <div className="form-actions"><button className="button" type="button" onClick={reloadVersion}>Descartar borrador y recargar</button>
        <button className="button button-primary" type="button" disabled={mutation.busy} onClick={() => void save(currentVersion)}>Reaplicar mi borrador</button></div>
    </div>}
    <div className="form-actions"><button className="button button-primary" type="submit" disabled={mutation.busy || mutation.stale || !!mutation.pending}>{block ? 'Guardar bloque' : 'Crear bloque'}</button>
      <button className="button button-quiet" type="button" onClick={onCancel} disabled={!!mutation.pending}>Cancelar</button></div>
  </form>;
}

function DeleteBlockButton({ templateId, block, version, onDeleted }: {
  templateId: string;
  block: ScheduleBlock;
  version: number;
  onDeleted: () => void;
}) {
  const mutation = useSafeMutation();
  const [confirm, setConfirm] = useState(false);
  const [currentVersion, setCurrentVersion] = useState(version);

  async function remove(nextVersion = currentVersion) {
    const intent = createMutationIntent({ method: 'DELETE', path: `/templates/${templateId}/blocks/${block.id}`, version: nextVersion, timeZone: mutation.timeZone });
    const result = await mutation.send<ScheduleTemplate>(intent);
    if (result) onDeleted();
    else {
      const latest = await requestJson<ScheduleTemplate>(`/templates/${templateId}`).catch(() => null);
      if (latest) setCurrentVersion(latest.data.version);
    }
  }

  return <>
    {!confirm ? <button className="text-button" type="button" onClick={() => setConfirm(true)}>Eliminar</button> : <>
      <span className="muted">¿Eliminar el bloque?</span>
      <button className="button button-danger" type="button" disabled={mutation.busy || mutation.stale} onClick={() => void remove()}>Confirmar</button>
      <button className="button button-quiet" type="button" onClick={() => setConfirm(false)}>Cancelar</button>
    </>}
    {mutation.message && <p className="danger-text" role="alert">{mutation.message}</p>}
    {mutation.pending && <button className="button" type="button" disabled={mutation.busy} onClick={async () => {
      const result = await mutation.retryPending<ScheduleTemplate>();
      if (result) onDeleted();
    }}>Reintentar el mismo cambio</button>}
    {mutation.conflict && <button className="button" type="button" onClick={() => void remove(currentVersion)}>Actualizar versión y volver a eliminar</button>}
  </>;
}

function daysLabel(mask: number) {
  return weekdays.filter((day) => mask & day.bit).map((day) => day.short).join(' · ');
}
