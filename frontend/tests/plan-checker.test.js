import { test } from 'node:test'
import assert from 'node:assert/strict'
import { checkPlanAgainstCurrentRules } from '../src/planChecker.js'

const plan = { completedCourses: [], terms: [
  { label: 'Fall', creditCap: 16, proposedCourses: ['csc-116-2026-2027'], carryPassed: true, assumedGrade: 'B' },
  { label: 'Spring', creditCap: 16, proposedCourses: ['csc-216-2026-2027'], carryPassed: false, assumedGrade: 'C' },
  { label: 'Summer', creditCap: 6, proposedCourses: ['csc-217-2026-2027'], carryPassed: false, assumedGrade: 'C' },
] }
function response(evidence) {
  return { status: 'SATISFIED', courses: evidence.proposedCourses.map(courseId => ({ courseId, status: 'SATISFIED', reviewStatus: 'REVIEWED', reason: 'ok', condition: { type: 'course', courseId, status: 'SATISFIED', reason: 'ok', children: [] } })),
    creditLoad: { status: 'SATISFIED', creditCap: evidence.creditCap, knownCredits: evidence.proposedCourses.length, totalCredits: evidence.proposedCourses.length, unresolvedCourses: [], reason: 'ok' } }
}
test('checks terms in order and carries only explicitly assumed passing outcomes', async () => {
  const requests = []
  const results = await checkPlanAgainstCurrentRules(plan, { fetcher: async (_url, options) => {
    const evidence = JSON.parse(options.body)
    requests.push(evidence)
    return { ok: true, json: async () => response(evidence) }
  } })
  assert.equal(results.length, 3)
  assert.deepEqual(requests[0].completedCourses, [])
  assert.deepEqual(requests[1].completedCourses, [{ courseId: 'csc-116-2026-2027', grade: 'B', creditEarned: true }])
  assert.deepEqual(requests[2].completedCourses, [{ courseId: 'csc-116-2026-2027', grade: 'B', creditEarned: true }])
})
test('stops at an unavailable term check and rejects malformed results', async () => {
  await assert.rejects(checkPlanAgainstCurrentRules(plan, { fetcher: async () => ({ ok: false }) }), /could not be checked/)
  await assert.rejects(checkPlanAgainstCurrentRules(plan, { fetcher: async () => ({ ok: true, json: async () => ({}) }) }), /unexpected result/)
})
