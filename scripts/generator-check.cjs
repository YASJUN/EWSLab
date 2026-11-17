'use strict';
// Execute the real single-file generator without DOM/audio devices; emit deterministic WAVs.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'generator', 'ews_tsunami_player.html');
const html = fs.readFileSync(source, 'utf8');
const pure = html.slice(html.indexOf('const SAMPLE_RATE ='), html.indexOf('let currentData ='));
const synthesis = html.slice(html.indexOf('function synthesizePCM('), html.indexOf('function audioOptions('));
assert.ok(pure.startsWith('const SAMPLE_RATE =') && synthesis.startsWith('function synthesizePCM('), 'Generator entry points changed');
const context = vm.createContext({ Date, Math, Number, String, Object, Array, Error, RangeError,
  Float32Array, ArrayBuffer, DataView });
vm.runInContext(pure + '\n' + synthesis, context, { timeout: 2000 });
const output = path.join(root, 'verification', 'local', 'generator-fixtures');
fs.mkdirSync(output, { recursive: true });
const fixtures = [];
for (const areaKey of ['all', 'kanto', 'chukyo', 'kinki', 'tottori_shimane', 'okayama_kagawa']) {
  for (const [label, date] of [['ordinary', '2024-01-01T16:12:00+09:00'],
    ['early', '2024-01-01T00:05:00+09:00'], ['late', '2024-12-31T23:55:00+09:00']]) {
    fixtures.push({ name: `II-${areaKey}-${label}`, type: 'II', areaKey, date, repeats: 10 });
  }
}
for (const type of ['I', 'F']) {
  for (const [label, date] of [['ordinary', '2024-01-01T16:12:00+09:00'],
    ['early', '2024-01-01T00:05:00+09:00'], ['late', '2024-12-31T23:55:00+09:00']]) {
    fixtures.push({ name: `${type}-all-${label}`, type, areaKey: 'all', date, repeats: type === 'F' ? 4 : 10 });
  }
}
for (const boost640 of [1.45, 1.75]) fixtures.push({ name: `II-boost-${boost640}`, type: 'II',
  areaKey: 'all', date: '2024-01-01T16:12:00+09:00', repeats: 10, boost640 });

const rows = [];
for (const fixture of fixtures) {
  context.options = fixture;
  const transmission = vm.runInContext('buildTransmission(options)', context);
  const wav = Buffer.from(vm.runInContext('encodeWav(synthesizePCM(buildTransmission(options).bits, { boost640: options.boost640 || 1 }))', context));
  if (fixture.name === 'II-all-ordinary') {
    assert.deepEqual(Array.from(transmission.words, word => word.hex), ['0xF192', '0x8D34', '0xF192', '0x5044', '0xF192', '0x6214']);
  }
  const slotCount = fixture.type === 'F' ? 192 * fixture.repeats : 4 + 96 * fixture.repeats;
  assert.equal(transmission.bits.length, slotCount);
  assert.equal(wav.toString('ascii', 0, 4), 'RIFF');
  assert.equal(wav.readUInt16LE(20), 1);
  assert.equal(wav.readUInt16LE(34), 16);
  assert.equal(wav.readUInt32LE(24), 44100);
  const filename = fixture.name + '.wav';
  fs.writeFileSync(path.join(output, filename), wav);
  transmission.blocks.forEach((block, index) => {
    const p = block.dateParts;
    const expected = new Date(new Date(fixture.date).getTime() + 9 * 3600000);
    const minute = expected.getUTCMinutes();
    if (index % 2 === 1) expected.setUTCHours(expected.getUTCHours() + (minute < 10 ? -1 : minute >= 50 ? 1 : 0));
    assert.deepEqual([p.year, p.month, p.day, p.hour],
      [expected.getUTCFullYear(), expected.getUTCMonth() + 1, expected.getUTCDate(), expected.getUTCHours()]);
    const area = block.words[1].bits.slice(2, 14);
    rows.push([filename, index, fixture.type === 'II' ? 'SECOND' : fixture.type === 'I' ? 'FIRST' : 'END',
      area, p.year % 10, p.month, p.day, p.hour, p.monthDayFlag, p.yearHourFlag, block.bits].join('\t'));
  });
}
fs.writeFileSync(path.join(output, 'expected.tsv'), rows.join('\n') + '\n');
console.log(`Generator checks passed: ${fixtures.length} WAV files, ${rows.length} expected bodies.\n${output}`);
