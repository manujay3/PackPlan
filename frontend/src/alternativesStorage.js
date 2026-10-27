import { validSemesterEvidence } from './eligibility.js'

export const ALTERNATIVES_KEY = 'packplan.semester-alternatives.v1'

function evidenceCopy(evidence) {
  return {
    creditCap: evidence.creditCap,
    proposedCourses: [...evidence.proposedCourses],
    completedCourses: evidence.completedCourses.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned })),
  }
}
export function readAlternatives(storage = window.localStorage) {
  const raw = storage.getItem(ALTERNATIVES_KEY)
  if (raw === null) return [null, null]
  const data = JSON.parse(raw)
  if (!data || data.version !== 1 || !Array.isArray(data.slots) || data.slots.length !== 2) throw new Error('Invalid saved alternatives')
  return data.slots.map(slot => {
    if (slot === null) return null
    if (!slot || typeof slot.name !== 'string' || !slot.name.trim() || slot.name.length > 60
      || typeof slot.savedAt !== 'string' || !Number.isFinite(Date.parse(slot.savedAt))
      || !validSemesterEvidence(slot.evidence)) throw new Error('Invalid saved alternative')
    return { name: slot.name.trim(), savedAt: slot.savedAt, evidence: evidenceCopy(slot.evidence) }
  })
}
export function saveAlternative(index, name, evidence, storage = window.localStorage) {
  if (![0, 1].includes(index) || typeof name !== 'string' || !name.trim() || name.length > 60
    || !validSemesterEvidence(evidence)) throw new Error('Invalid alternative')
  const slots = readAlternatives(storage)
  slots[index] = { name: name.trim(), savedAt: new Date().toISOString(), evidence: evidenceCopy(evidence) }
  storage.setItem(ALTERNATIVES_KEY, JSON.stringify({ version: 1, slots }))
  return slots
}
export function deleteAlternative(index, storage = window.localStorage) {
  if (![0, 1].includes(index)) throw new Error('Invalid slot')
  const slots = readAlternatives(storage)
  slots[index] = null
  if (slots.every(slot => slot === null)) storage.removeItem(ALTERNATIVES_KEY)
  else storage.setItem(ALTERNATIVES_KEY, JSON.stringify({ version: 1, slots }))
  return slots
}
export function clearAlternatives(storage = window.localStorage) { storage.removeItem(ALTERNATIVES_KEY) }
