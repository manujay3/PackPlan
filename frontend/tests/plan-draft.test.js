import { test } from 'node:test'
import assert from 'node:assert/strict'
import { DRAFT_KEY, deleteDraft, readDraft, saveDraft } from '../src/planDraft.js'

function storage(initial = null) {
  let value = initial
  return { getItem: key => key === DRAFT_KEY ? value : null,
    setItem: (key, next) => { if (key === DRAFT_KEY) value = next },
    removeItem: key => { if (key === DRAFT_KEY) value = null },
    raw: () => value }
}

const emptyTerm = { label: 'Spring 2027', creditCap: 16, proposedCourses: [], carryPassed: false, assumedGrade: 'C', sectionSelections: [] }

test('saves and restores incomplete transcript attempts without pretending credit is confirmed', () => {
  const local = storage()
  const draft = { completedCourses: [{ courseId: 'csc-116-2026-2027', grade: 'A', creditEarned: '' }], terms: [emptyTerm] }
  const saved = saveDraft(draft, local)
  assert.equal(saved.draft.completedCourses[0].creditEarned, '')
  assert.equal(readDraft(local).draft.completedCourses[0].grade, 'A')
})

test('copies section data and does not retain references to live planner input', () => {
  const local = storage()
  const section = { courseId: 'CSC 116', courseCode: 'CSC 116', section: '001', component: 'Lec', classNumber: '1234',
    availability: 'Open', meetingDays: ['Monday'], startTime: null, endTime: null, meetingText: 'TBA', location: '', instructor: '',
    dates: '', notes: [], retrievedAt: '2026-10-27T12:00:00Z' }
  const draft = { completedCourses: [], terms: [{ ...emptyTerm, proposedCourses: ['CSC 116'], sectionSelections: [section] }] }
  saveDraft(draft, local)
  section.meetingDays.push('Tuesday')
  assert.deepEqual(readDraft(local).draft.terms[0].sectionSelections[0].meetingDays, ['Monday'])
})

test('rejects duplicate courses and malformed saved draft data without replacing it', () => {
  const local = storage('keep-me')
  assert.throws(() => saveDraft({ completedCourses: [], terms: [{ ...emptyTerm, proposedCourses: ['CSC 116'] }, { ...emptyTerm, proposedCourses: ['CSC 116'] }] }, local))
  assert.equal(local.raw(), 'keep-me')
  assert.throws(() => readDraft(local))
  assert.equal(local.raw(), 'keep-me')
})

test('deletes only the planning draft storage key', () => {
  const local = storage('draft')
  local.setItem('other', 'preserve')
  deleteDraft(local)
  assert.equal(local.raw(), null)
})
