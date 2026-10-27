import { validSemesterResult } from './eligibility.js'
import { validPlan } from './planStorage.js'

export async function checkPlanAgainstCurrentRules(plan, { signal, fetcher = fetch } = {}) {
  if (!validPlan(plan)) throw new Error('The saved plan is invalid.')
  const results = []
  const assumedFromEarlierTerms = []
  for (const term of plan.terms) {
    const evidence = {
      completedCourses: [...plan.completedCourses, ...assumedFromEarlierTerms],
      proposedCourses: [...term.proposedCourses],
      creditCap: term.creditCap,
    }
    const response = await fetcher('/api/semesters/eligibility', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store', signal,
      body: JSON.stringify(evidence),
    })
    if (!response.ok) throw new Error('A term could not be checked against current rules.')
    const result = await response.json()
    if (!validSemesterResult(result, evidence)) throw new Error('The server returned an unexpected result.')
    results.push(result)
    if (term.carryPassed) assumedFromEarlierTerms.push(...term.proposedCourses.map(courseId => ({ courseId, grade: term.assumedGrade, creditEarned: true })))
  }
  return results
}
