import { expect, test } from '@playwright/test';
import { addCivilDays, formatCivilDate, formatMinute, minuteFromTime, mondayOfWeek, todayInTimeZone } from '../src/lib/dates';

test('civil dates roll over month and year without local-time drift', () => {
  expect(addCivilDays('2026-12-31', 1)).toBe('2027-01-01');
  expect(addCivilDays('2026-03-01', -1)).toBe('2026-02-28');
});

test('week navigation starts on Monday, including when the selected date is Sunday', () => {
  expect(mondayOfWeek('2026-11-22')).toBe('2026-11-16');
  expect(mondayOfWeek('2026-11-19')).toBe('2026-11-16');
});

test('today is calculated in the account timezone', () => {
  expect(todayInTimeZone('America/Los_Angeles', new Date('2026-01-01T01:00:00.000Z'))).toBe('2025-12-31');
});

test('civil dates render in the requested timezone', () => {
  const result = formatCivilDate('2026-03-05', 'Europe/Madrid', { day: 'numeric', month: 'long' });
  expect(result).toContain('5');
  expect(result).toContain('marzo');
});

test('minute fields preserve 24:00 as the end of day', () => {
  expect(minuteFromTime('23:59')).toBe(1439);
  expect(formatMinute(1440)).toBe('24:00');
  expect(formatMinute(9 * 60 + 5)).toBe('09:05');
});
