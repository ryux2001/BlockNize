const datePattern = /^(\d{4})-(\d{2})-(\d{2})$/;

export function todayInTimeZone(timeZone: string, now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const fields = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${fields.year}-${fields.month}-${fields.day}`;
}

export function addCivilDays(date: string, offset: number) {
  const match = datePattern.exec(date);
  if (!match) throw new Error('La fecha debe usar el formato YYYY-MM-DD.');
  const [, year, month, day] = match;
  const value = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day) + offset));
  return value.toISOString().slice(0, 10);
}

export function mondayOfWeek(date: string) {
  const weekday = new Date(`${date}T00:00:00Z`).getUTCDay();
  return addCivilDays(date, -((weekday + 6) % 7));
}

export function formatCivilDate(date: string, timeZone: string, options: Intl.DateTimeFormatOptions = {}) {
  const match = datePattern.exec(date);
  if (!match) return date;
  const [, year, month, day] = match;
  const instant = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), 12));
  return new Intl.DateTimeFormat('es-ES', { timeZone, ...options }).format(instant);
}

export function formatMinute(value: number) {
  if (value === 1440) return '24:00';
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
}

export function minuteFromTime(value: string) {
  const [hour, minute] = value.split(':').map(Number);
  return hour * 60 + minute;
}

export function formatTimeRange(startMinute: number, endMinute: number) {
  return `${formatMinute(startMinute)}–${formatMinute(endMinute)}`;
}
