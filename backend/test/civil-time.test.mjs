import assert from 'node:assert/strict';
import test from 'node:test';
import { civilDateAt, civilInstant, validateTimeZone, weekdayBit } from '../dist/src/agenda/civil-time.js';

test('resolves ordinary civil times in a fixed-offset zone', () => {
  assert.equal(civilInstant('2026-01-15', 0, 'Asia/Kolkata').toISOString(), '2026-01-14T18:30:00.000Z');
  assert.equal(civilInstant('2026-01-15', 570, 'Asia/Kolkata').toISOString(), '2026-01-15T04:00:00.000Z');
});

test('uses the earlier offset for an ambiguous local time', () => {
  assert.equal(civilInstant('2026-10-25', 150, 'Europe/Madrid').toISOString(), '2026-10-25T00:30:00.000Z');
});

test('moves a nonexistent local time forward by the DST gap', () => {
  assert.equal(civilInstant('2026-03-29', 150, 'Europe/Madrid').toISOString(), '2026-03-29T01:30:00.000Z');
});

test('resolves a non-hour DST gap by its actual 30-minute transition', () => {
  assert.equal(civilInstant('2026-10-04', 135, 'Australia/Lord_Howe').toISOString(), '2026-10-03T15:45:00.000Z');
});

test('treats minute 1440 as the next civil midnight across DST changes', () => {
  assert.equal(civilInstant('2026-03-29', 0, 'Europe/Madrid').toISOString(), '2026-03-28T23:00:00.000Z');
  assert.equal(civilInstant('2026-03-29', 1440, 'Europe/Madrid').toISOString(), '2026-03-29T22:00:00.000Z');
  assert.equal(civilInstant('2026-10-25', 1440, 'Europe/Madrid').toISOString(), '2026-10-25T23:00:00.000Z');
});

test('converts instants back to the account civil date', () => {
  assert.equal(civilDateAt(new Date('2026-03-28T23:30:00.000Z'), 'Europe/Madrid'), '2026-03-29');
});

test('validates IANA zones and rejects unknown zones', () => {
  assert.equal(validateTimeZone('Europe/Madrid'), 'Europe/Madrid');
  assert.throws(() => validateTimeZone('Mars/Olympus_Mons'), RangeError);
});

test('maps weekdays to Monday-first mask bits', () => {
  assert.equal(weekdayBit('2026-11-09'), 1);
  assert.equal(weekdayBit('2026-11-15'), 64);
});
