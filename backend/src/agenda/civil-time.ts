const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(zone: string) {
  let value = formatters.get(zone);
  if (!value) {
    value = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
    formatters.set(zone, value);
  }
  return value;
}

function partsAt(epochMs: number, zone: string) {
  const p = Object.fromEntries(formatter(zone).formatToParts(epochMs).map((part) => [part.type, Number(part.value)]));
  return { year: p.year, month: p.month, day: p.day, hour: p.hour, minute: p.minute, second: p.second };
}

function wallValue(p: ReturnType<typeof partsAt>) {
  return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
}

function sameWall(a: ReturnType<typeof partsAt>, b: ReturnType<typeof partsAt>) {
  return a.year === b.year && a.month === b.month && a.day === b.day && a.hour === b.hour && a.minute === b.minute;
}

function addCivilMinutes(date: string, minute: number) {
  const [year, month, day] = date.split('-').map(Number);
  const value = new Date(Date.UTC(year, month - 1, day, 0, minute));
  return { year: value.getUTCFullYear(), month: value.getUTCMonth() + 1, day: value.getUTCDate(), hour: value.getUTCHours(), minute: value.getUTCMinutes() };
}

/** Matches Java LocalDateTime.atZone: overlap chooses the earlier offset; gaps shift forward by the gap. */
export function civilInstant(date: string, minute: number, zone: string): Date {
  const target = addCivilMinutes(date, minute);
  const nominal = Date.UTC(target.year, target.month - 1, target.day, target.hour, target.minute);
  const offsets = new Set<number>();
  for (const hours of [-36, -24, -12, 0, 12, 24, 36]) {
    const sample = nominal + hours * 3_600_000;
    const wall = partsAt(sample, zone);
    offsets.add(wallValue(wall) - Math.floor(sample / 60_000) * 60_000);
  }
  const wanted = { ...target, second: 0 };
  const candidates = [...offsets].map((offset) => nominal - offset)
    .map((epoch) => ({ epoch, wall: partsAt(epoch, zone) }));
  const exact = candidates.filter((candidate) => sameWall(candidate.wall, wanted)).sort((a, b) => a.epoch - b.epoch);
  if (exact.length) return new Date(exact[0].epoch);
  const after = candidates.filter((candidate) => wallValue(candidate.wall) >= nominal)
    .sort((a, b) => (wallValue(a.wall) - nominal) - (wallValue(b.wall) - nominal) || a.epoch - b.epoch);
  if (after.length) return new Date(after[0].epoch);
  throw new RangeError(`No se pudo resolver la hora local ${date} ${minute} en ${zone}.`);
}

export function validateTimeZone(zone: string) {
  try { formatter(zone).format(0); return zone; }
  catch { throw new RangeError(`Zona horaria IANA no válida: ${zone}`); }
}

export function civilDateAt(instant: Date, zone: string) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(instant).map((part) => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function weekdayBit(date: string) {
  const [year, month, day] = date.split('-').map(Number);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return 1 << ((weekday + 6) % 7);
}
