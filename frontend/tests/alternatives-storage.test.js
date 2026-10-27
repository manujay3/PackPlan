import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ALTERNATIVES_KEY, readAlternatives, saveAlternative, deleteAlternative, clearAlternatives } from '../src/alternativesStorage.js'

const evidence = () => ({ completedCourses: [{ courseId: 'csc-116-2026-2027', grade: 'C', creditEarned: true }], proposedCourses: ['csc-216-2026-2027', 'csc-217-2026-2027'], creditCap: 4 })
function storage() {
  const data = new Map()
  return { data, getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }
}
test('stores snapshots of evidence only, never cached eligibility results', () => {
  const db = storage()
  const input = { ...evidence(), status: 'SATISFIED', result: { status: 'SATISFIED' } }
  saveAlternative(0, '  Pair  ', input, db)
  input.proposedCourses.pop()
  const saved = readAlternatives(db)[0]
  assert.equal(saved.name, 'Pair')
  assert.equal(saved.evidence.proposedCourses.length, 2)
  assert.equal(saved.status, undefined)
  assert.equal(saved.evidence.result, undefined)
  assert.equal(db.getItem(ALTERNATIVES_KEY).includes('SATISFIED'), false)
})
test('stores term labels and user supplied offering assumptions, while reading older saves', () => {
  const db = storage()
  saveAlternative(0, 'Fall plan', evidence(), db, { termLabel: 'Fall 2026', assumedOfferedCourses: ['csc-216-2026-2027'] })
  const saved = readAlternatives(db)[0]
  assert.equal(saved.termLabel, 'Fall 2026')
  assert.deepEqual(saved.assumedOfferedCourses, ['csc-216-2026-2027'])
  assert.deepEqual(JSON.parse(db.getItem(ALTERNATIVES_KEY)).slots[0].evidence, evidence())

  db.setItem(ALTERNATIVES_KEY, JSON.stringify({ version: 1, slots: [{ name: 'Old', savedAt: new Date().toISOString(), evidence: evidence() }, null] }))
  assert.equal(readAlternatives(db)[0].termLabel, '')
  assert.deepEqual(readAlternatives(db)[0].assumedOfferedCourses, [])
})
test('rejects offering assumptions for courses outside the saved term', () => {
  const db = storage()
  assert.throws(() => saveAlternative(0, 'A', evidence(), db, { termLabel: 'Fall', assumedOfferedCourses: ['not-selected'] }))
  assert.throws(() => saveAlternative(0, 'A', evidence(), db, { termLabel: 'x'.repeat(61), assumedOfferedCourses: [] }))
})
test('reads the latest other slot before saving and replaces only the chosen alternative', () => {
  const db = storage()
  saveAlternative(0, 'A', evidence(), db)
  saveAlternative(1, 'Other tab B', { ...evidence(), creditCap: 3 }, db)
  saveAlternative(0, 'Replaced A', evidence(), db)
  assert.deepEqual(readAlternatives(db).map(slot => slot.name), ['Replaced A', 'Other tab B'])
})
test('failed writes preserve earlier saves and report failure', () => {
  const db = storage()
  saveAlternative(0, 'A', evidence(), db)
  const before = db.getItem(ALTERNATIVES_KEY)
  db.setItem = () => { throw new Error('Quota exceeded') }
  assert.throws(() => saveAlternative(0, 'Replacement', evidence(), db))
  assert.equal(db.getItem(ALTERNATIVES_KEY), before)
})
test('malformed and incompatible data is not silently overwritten', () => {
  for (const raw of ['{bad', 'null', JSON.stringify({ version: 9, slots: [null, null] }), JSON.stringify({ version: 1, slots: [] })]) {
    const db = storage()
    db.setItem(ALTERNATIVES_KEY, raw)
    assert.throws(() => readAlternatives(db))
    assert.throws(() => saveAlternative(0, 'A', evidence(), db))
    assert.equal(db.getItem(ALTERNATIVES_KEY), raw)
  }
})
test('rejects invalid evidence and preserves exact unavailable course identities', () => {
  const db = storage()
  for (const invalid of [{ ...evidence(), creditCap: 0 }, { ...evidence(), creditCap: 1.5 },
    { ...evidence(), proposedCourses: ['a', 'a'] }, { ...evidence(), completedCourses: [{ courseId: 'a', grade: 'F', creditEarned: true }] }]) {
    assert.throws(() => saveAlternative(0, 'A', invalid, db))
  }
  saveAlternative(0, 'Historical', { ...evidence(), proposedCourses: ['missing-2025-2026'] }, db)
  assert.deepEqual(readAlternatives(db)[0].evidence.proposedCourses, ['missing-2025-2026'])
})
test('deletion and clearing remove only this feature key', () => {
  const db = storage()
  db.setItem('unrelated', 'keep')
  saveAlternative(0, 'A', evidence(), db)
  saveAlternative(1, 'B', evidence(), db)
  deleteAlternative(0, db)
  assert.equal(readAlternatives(db)[0], null)
  assert.equal(readAlternatives(db)[1].name, 'B')
  deleteAlternative(1, db)
  assert.equal(db.getItem(ALTERNATIVES_KEY), null)
  db.setItem(ALTERNATIVES_KEY, '{bad')
  clearAlternatives(db)
  assert.equal(db.getItem('unrelated'), 'keep')
})
test('storage read failures are surfaced rather than treated as an empty successful save', () => {
  const db = { getItem() { throw new Error('Blocked') } }
  assert.throws(() => readAlternatives(db))
  assert.throws(() => saveAlternative(0, 'A', evidence(), db))
})
