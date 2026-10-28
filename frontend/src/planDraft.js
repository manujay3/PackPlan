import { grades, noCreditGrades } from './eligibility.js'

export const DRAFT_KEY = 'packplan.planner-draft.v1'
const gradeValues = new Set(grades.map(([grade]) => grade))

function copy(draft) {
  return {
    completedCourses: draft.completedCourses.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned })),
    terms: draft.terms.map(term => ({ label: term.label, creditCap: term.creditCap, proposedCourses: [...term.proposedCourses],
      carryPassed: term.carryPassed, assumedGrade: term.assumedGrade,
      sectionSelections: term.sectionSelections.map(section => ({ ...section, meetingDays: [...section.meetingDays], notes: [...section.notes] })) })),
  }
}

function validDraftSection(section, proposedCourses) {
  const optionalTime = value => value === null || (typeof value === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(value))
  return section && proposedCourses.includes(section.courseId) && typeof section.courseCode === 'string'
    && typeof section.component === 'string' && typeof section.section === 'string' && typeof section.classNumber === 'string'
    && typeof section.availability === 'string' && Array.isArray(section.meetingDays)
    && section.meetingDays.every(day => typeof day === 'string') && optionalTime(section.startTime)
    && optionalTime(section.endTime) && typeof section.meetingText === 'string' && typeof section.location === 'string'
    && typeof section.instructor === 'string' && typeof section.dates === 'string' && Array.isArray(section.notes)
    && section.notes.every(note => typeof note === 'string') && typeof section.retrievedAt === 'string'
    && Number.isFinite(Date.parse(section.retrievedAt))
}

function validDraft(draft) {
  if (!draft || !Array.isArray(draft.completedCourses) || draft.completedCourses.length > 500
    || !Array.isArray(draft.terms) || draft.terms.length < 1 || draft.terms.length > 6) return false
  if (!draft.completedCourses.every(attempt => attempt && typeof attempt.courseId === 'string'
    && (attempt.grade === '' || gradeValues.has(attempt.grade))
    && ['true', 'false', ''].includes(attempt.creditEarned)
    && !(noCreditGrades.has(attempt.grade) && attempt.creditEarned === 'true'))) return false
  const courses = []
  for (const term of draft.terms) {
    if (!term || typeof term.label !== 'string' || term.label.length > 60
      || !(term.creditCap === '' || (Number.isInteger(Number(term.creditCap)) && Number(term.creditCap) > 0 && Number(term.creditCap) <= 2147483647))
      || typeof term.carryPassed !== 'boolean' || !gradeValues.has(term.assumedGrade) || noCreditGrades.has(term.assumedGrade)
      || !Array.isArray(term.proposedCourses) || term.proposedCourses.length > 100
      || !term.proposedCourses.every(courseId => typeof courseId === 'string' && courseId.trim())
      || !Array.isArray(term.sectionSelections) || !term.sectionSelections.every(section => validDraftSection(section, term.proposedCourses))) return false
    courses.push(...term.proposedCourses)
  }
  return new Set(courses).size === courses.length
}

export function saveDraft(draft, storage = window.localStorage) {
  if (storage.getItem(DRAFT_KEY) !== null) readDraft(storage)
  if (!validDraft(draft)) throw new Error('Invalid planning draft')
  const saved = { version: 1, savedAt: new Date().toISOString(), draft: copy(draft) }
  storage.setItem(DRAFT_KEY, JSON.stringify(saved))
  return saved
}

export function readDraft(storage = window.localStorage) {
  const raw = storage.getItem(DRAFT_KEY)
  if (raw === null) return null
  const saved = JSON.parse(raw)
  if (!saved || saved.version !== 1 || typeof saved.savedAt !== 'string' || !Number.isFinite(Date.parse(saved.savedAt)) || !validDraft(saved.draft)) {
    throw new Error('Invalid saved draft')
  }
  return { version: 1, savedAt: saved.savedAt, draft: copy(saved.draft) }
}

export function deleteDraft(storage = window.localStorage) { storage.removeItem(DRAFT_KEY) }
