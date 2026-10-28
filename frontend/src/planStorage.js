import { grades, noCreditGrades, validSemesterEvidence } from './eligibility.js'

export const PLANS_KEY = 'packplan.multi-term-plans.v1'
const gradeValues = new Set(grades.map(([grade]) => grade))

function copyPlan(plan) {
  return {
    completedCourses: plan.completedCourses.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned })),
    terms: plan.terms.map(term => ({ label: term.label.trim(), creditCap: term.creditCap,
      proposedCourses: [...term.proposedCourses], carryPassed: term.carryPassed, assumedGrade: term.assumedGrade,
      sectionSelections: (term.sectionSelections || []).map(section => ({ ...section, meetingDays: [...section.meetingDays], notes: [...section.notes] })) })),
  }
}
function validSection(section, proposedCourses) {
  const optionalTime = value => value === null || (typeof value === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(value))
  return section && proposedCourses.includes(section.courseId) && typeof section.courseCode === 'string' && section.courseCode.trim()
    && typeof section.section === 'string' && typeof section.component === 'string' && typeof section.classNumber === 'string'
    && typeof section.availability === 'string' && Array.isArray(section.meetingDays) && section.meetingDays.every(day => typeof day === 'string')
    && optionalTime(section.startTime) && optionalTime(section.endTime) && typeof section.meetingText === 'string'
    && typeof section.location === 'string' && typeof section.instructor === 'string' && typeof section.dates === 'string'
    && Array.isArray(section.notes) && section.notes.every(note => typeof note === 'string')
    && typeof section.retrievedAt === 'string' && Number.isFinite(Date.parse(section.retrievedAt))
}
export function validPlan(plan) {
  if (!plan || !Array.isArray(plan.completedCourses) || !Array.isArray(plan.terms) || plan.terms.length < 1 || plan.terms.length > 6) return false
  if (!plan.completedCourses.every(attempt => attempt && typeof attempt.courseId === 'string' && attempt.courseId.trim()
    && gradeValues.has(attempt.grade) && typeof attempt.creditEarned === 'boolean'
    && !(noCreditGrades.has(attempt.grade) && attempt.creditEarned))) return false
  const allCourses = []
  for (const term of plan.terms) {
    if (!term || typeof term.label !== 'string' || term.label.length > 60
      || !Number.isInteger(term.creditCap) || term.creditCap < 1 || term.creditCap > 2147483647
      || typeof term.carryPassed !== 'boolean' || !gradeValues.has(term.assumedGrade) || noCreditGrades.has(term.assumedGrade)
      || !Array.isArray(term.proposedCourses) || !validSemesterEvidence({ completedCourses: [], proposedCourses: term.proposedCourses, creditCap: term.creditCap })
      || (term.sectionSelections !== undefined && (!Array.isArray(term.sectionSelections) || !term.sectionSelections.every(section => validSection(section, term.proposedCourses))))) return false
    allCourses.push(...term.proposedCourses)
  }
  return new Set(allCourses).size === allCourses.length
}
function normalize(data) {
  if (!data || data.version !== 1 || !Array.isArray(data.slots) || data.slots.length !== 2) throw new Error('Invalid saved plans')
  return data.slots.map(slot => {
    if (slot === null) return null
    if (!slot || typeof slot.name !== 'string' || !slot.name.trim() || slot.name.length > 60
      || typeof slot.savedAt !== 'string' || !Number.isFinite(Date.parse(slot.savedAt)) || !validPlan(slot.plan)) throw new Error('Invalid saved plan')
    const plan = copyPlan({ ...slot.plan, terms: slot.plan.terms.map(term => ({ ...term, sectionSelections: term.sectionSelections || [] })) })
    return { name: slot.name.trim(), savedAt: slot.savedAt, plan }
  })
}
export function readPlans(storage = window.localStorage) {
  const raw = storage.getItem(PLANS_KEY)
  return raw === null ? [null, null] : normalize(JSON.parse(raw))
}
export function savePlan(index, name, plan, storage = window.localStorage) {
  if (![0, 1].includes(index) || typeof name !== 'string' || !name.trim() || name.length > 60 || !validPlan(plan)) throw new Error('Invalid plan')
  const slots = readPlans(storage)
  slots[index] = { name: name.trim(), savedAt: new Date().toISOString(), plan: copyPlan(plan) }
  storage.setItem(PLANS_KEY, JSON.stringify({ version: 1, slots }))
  return slots
}
export function deletePlan(index, storage = window.localStorage) {
  if (![0, 1].includes(index)) throw new Error('Invalid slot')
  const slots = readPlans(storage)
  slots[index] = null
  if (slots.every(slot => slot === null)) storage.removeItem(PLANS_KEY)
  else storage.setItem(PLANS_KEY, JSON.stringify({ version: 1, slots }))
  return slots
}
export function clearPlans(storage = window.localStorage) { storage.removeItem(PLANS_KEY) }
