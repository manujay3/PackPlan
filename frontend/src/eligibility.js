export const grades = [
  ['A_PLUS', 'A+'], ['A', 'A'], ['A_MINUS', 'A−'], ['B_PLUS', 'B+'], ['B', 'B'], ['B_MINUS', 'B−'],
  ['C_PLUS', 'C+'], ['C', 'C'], ['C_MINUS', 'C−'], ['D_PLUS', 'D+'], ['D', 'D'], ['D_MINUS', 'D−'], ['F', 'F'],
  ['SATISFACTORY', 'Satisfactory'], ['UNSATISFACTORY', 'Unsatisfactory'], ['CREDIT', 'Credit'],
  ['TRANSFER', 'Transfer'], ['WITHDRAWN', 'Withdrawn'], ['INCOMPLETE', 'Incomplete'], ['UNKNOWN', 'Unknown'],
]
export const noCreditGrades = new Set(['F', 'UNSATISFACTORY', 'WITHDRAWN', 'INCOMPLETE'])

const statuses = ['SATISFIED', 'BLOCKED', 'MANUAL_REVIEW']
const reviewLabels = { REVIEWED: true, NEEDS_REVIEW: true, UNREVIEWED: true }

function validCondition(condition) {
  return condition && ['allOf', 'anyOf', 'course', 'unsupported'].includes(condition.type)
    && statuses.includes(condition.status) && typeof condition.reason === 'string'
    && Array.isArray(condition.children) && condition.children.every(validCondition)
    && (condition.type !== 'course' || typeof condition.courseId === 'string')
}
export function validResult(data, id) {
  return data && data.courseId === id && statuses.includes(data.status) && typeof data.reason === 'string'
    && (data.reviewStatus === null || Object.hasOwn(reviewLabels, data.reviewStatus))
    && (data.condition === null ? data.status === 'MANUAL_REVIEW' && data.reviewStatus === null : validCondition(data.condition))
    && (data.status !== 'SATISFIED' || data.reviewStatus === 'REVIEWED')
}
function validCreditLoad(load, cap, proposed) {
  if (!load || load.creditCap !== cap || !statuses.includes(load.status) || typeof load.reason !== 'string'
    || !Number.isSafeInteger(load.knownCredits) || load.knownCredits < 0 || !Array.isArray(load.unresolvedCourses)
    || new Set(load.unresolvedCourses).size !== load.unresolvedCourses.length
    || !load.unresolvedCourses.every(id => proposed.includes(id))) return false
  const unknown = load.unresolvedCourses.length > 0
  return (unknown ? load.totalCredits === null : load.totalCredits === load.knownCredits)
    && load.status === (load.knownCredits > cap ? 'BLOCKED' : unknown ? 'MANUAL_REVIEW' : 'SATISFIED')
}
export function validSemesterResult(data, evidence) {
  return data && statuses.includes(data.status) && Array.isArray(data.courses)
    && data.courses.length === evidence.proposedCourses.length
    && data.courses.every((course, index) => validResult(course, evidence.proposedCourses[index]))
    && validCreditLoad(data.creditLoad, evidence.creditCap, evidence.proposedCourses)
    && data.status === (data.creditLoad.status === 'BLOCKED' || data.courses.some(course => course.status === 'BLOCKED') ? 'BLOCKED'
      : data.creditLoad.status === 'MANUAL_REVIEW' || data.courses.some(course => course.status === 'MANUAL_REVIEW') ? 'MANUAL_REVIEW' : 'SATISFIED')
}

export function validSemesterEvidence(evidence) {
  return evidence && Number.isInteger(evidence.creditCap) && evidence.creditCap > 0 && evidence.creditCap <= 2147483647
    && Array.isArray(evidence.proposedCourses) && evidence.proposedCourses.length > 0
    && evidence.proposedCourses.every(validId) && new Set(evidence.proposedCourses).size === evidence.proposedCourses.length
    && Array.isArray(evidence.completedCourses) && evidence.completedCourses.every(attempt => attempt && validId(attempt.courseId)
      && grades.some(([grade]) => grade === attempt.grade) && typeof attempt.creditEarned === 'boolean'
      && !(attempt.creditEarned && noCreditGrades.has(attempt.grade)))
}
function validId(id) { return typeof id === 'string' && id.trim().length > 0 }
