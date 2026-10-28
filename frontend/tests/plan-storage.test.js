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
test('persists selected section choices and accepts older snapshots without them', () => {
  const db = storage()
  const spring = plan()
  spring.terms = [{ ...spring.terms[1], sectionSelections: [{ courseId: 'csc-216-2026-2027', courseCode: 'CSC 216', section: '002', component: 'Lec', classNumber: '1632', availability: 'Open 12/40', meetingDays: ['Monday'], startTime: '10:40', endTime: '12:30', meetingText: 'M 10:40 AM - 12:30 PM', location: '200 Lampe Drive', instructor: 'Instructor', dates: '01/11/27 - 04/27/27', notes: ['A note'], retrievedAt: '2026-10-27T12:00:00Z' }] }]
  savePlan(0, 'Spring sections', spring, db)
  const selected = readPlans(db)[0].plan.terms[0].sectionSelections[0]
  assert.equal(selected.classNumber, '1632')
  assert.deepEqual(selected.meetingDays, ['Monday'])
  selected.meetingDays.push('Friday')
  assert.deepEqual(readPlans(db)[0].plan.terms[0].sectionSelections[0].meetingDays, ['Monday'])

  const legacy = plan()
  savePlan(1, 'Old plan', legacy, db)
  assert.deepEqual(readPlans(db)[1].plan.terms[0].sectionSelections, [])
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
