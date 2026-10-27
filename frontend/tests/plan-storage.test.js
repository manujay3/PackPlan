import { test } from 'node:test'
import assert from 'node:assert/strict'
import { clearPlans, deletePlan, PLANS_KEY, readPlans, savePlan } from '../src/planStorage.js'

const plan = () => ({ completedCourses: [{ courseId: 'old-course', grade: 'B', creditEarned: true }], terms: [
  { label: 'Fall 2026', creditCap: 16, proposedCourses: ['csc-116-2026-2027'], carryPassed: true, assumedGrade: 'C' },
  { label: 'Spring 2027', creditCap: 15, proposedCourses: ['csc-216-2026-2027'], carryPassed: false, assumedGrade: 'C' },
] })
function storage() {
  const data = new Map()
  return { data, getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }
}
test('saves a deep snapshot of term sequence and prior evidence', () => {
  const db = storage()
  const input = plan()
  savePlan(0, '  Fall sequence ', input, db)
  input.terms[0].proposedCourses.pop()
  const saved = readPlans(db)[0]
  assert.equal(saved.name, 'Fall sequence')
  assert.equal(saved.plan.terms[0].proposedCourses[0], 'csc-116-2026-2027')
  assert.deepEqual(saved.plan.completedCourses, [{ courseId: 'old-course', grade: 'B', creditEarned: true }])
})
test('replaces only the selected slot and retains the other saved plan', () => {
  const db = storage()
  savePlan(0, 'A', plan(), db)
  savePlan(1, 'B', plan(), db)
  savePlan(0, 'A2', plan(), db)
  assert.deepEqual(readPlans(db).map(slot => slot.name), ['A2', 'B'])
})
test('rejects duplicate planned courses and impossible outcomes', () => {
  const db = storage()
  const duplicate = plan()
  duplicate.terms[1].proposedCourses = ['csc-116-2026-2027']
  assert.throws(() => savePlan(0, 'bad', duplicate, db))
  const impossible = plan()
  impossible.completedCourses[0] = { courseId: 'old-course', grade: 'F', creditEarned: true }
  assert.throws(() => savePlan(0, 'bad', impossible, db))
})
test('accepts a single-term Spring plan for the immediate scheduling workflow', () => {
  const db = storage()
  const spring = plan()
  spring.terms = [spring.terms[1]]
  savePlan(0, 'Spring 2027', spring, db)
  assert.equal(readPlans(db)[0].plan.terms.length, 1)
  assert.equal(readPlans(db)[0].plan.terms[0].label, 'Spring 2027')
})
test('malformed data remains untouched and deletes only its own storage key', () => {
  const db = storage()
  db.setItem(PLANS_KEY, '{bad')
  assert.throws(() => readPlans(db))
  assert.throws(() => savePlan(0, 'A', plan(), db))
  assert.equal(db.getItem(PLANS_KEY), '{bad')
  clearPlans(db)
  db.setItem('unrelated', 'preserve')
  savePlan(0, 'A', plan(), db)
  deletePlan(0, db)
  assert.equal(db.getItem(PLANS_KEY), null)
  assert.equal(db.getItem('unrelated'), 'preserve')
})
