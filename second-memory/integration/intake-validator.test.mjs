import test from 'node:test';
import assert from 'node:assert/strict';
import { code } from './intake-validator.mjs';

const sample = () => ({ segment_id: 'speech_123_abc.pending.pcm', session_id: '20261101_100000', recorded_at: 1793516400000, text: 'Тест: позвонить завтра. Не реальное поручение.' });

test('retries retain stable identity and hash, preserving original Russian text', async () => {
  const body = sample();
  const first = await code({body});
  const repeated = await code({body: {...body}});
  assert.deepEqual(first, repeated);
  assert.equal(first.records[0].text, body.text);
  assert.equal(first.records[0].recorded_at, '2026-11-01T07:00:00.000Z');
  assert.equal(first.records[0].status, 'Получено');
});

test('different contents under the same identifier have a different hash', async () => {
  const body = sample();
  const a = await code({body});
  const b = await code({body: {...body, text: body.text + ' Изменение.'}});
  assert.notEqual(a.content_sha256, b.content_sha256);
});

test('reject malformed or oversized input before writing any record', async () => {
  const body = sample();
  for (const bad of [null, [], {}, {...body, segment_id: '../secret'}, {...body, session_id: ''}, {...body, text: ' '.repeat(3)}, {...body, text: 'я'.repeat(24001)}, {...body, recorded_at: '1793516400000'}, {...body, recorded_at: Number.NaN}, {...body, recorded_at: -1}, {...body, recorded_at: 1e20}]) {
    await assert.rejects(code({body: bad}));
  }
});

test('does not accept client-supplied processing status or analysis', async () => {
  const result = await code({body: {...sample(), status:'Разобрано', analysis:'Выдуманный разбор', source:'owner-authenticated'}});
  assert.equal(result.records[0].status, 'Получено');
  assert.equal(result.records[0].analysis, '');
  assert.equal(result.records[0].source, 'android-intake-v1');
});
