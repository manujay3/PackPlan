import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const key = 'packplan.multi-term-plans.v1'
const catalog = JSON.parse(readFileSync(new URL('../../backend/src/main/resources/catalog/courses.json', import.meta.url)))
const courses = catalog.courses.map(course => ({ ...course, catalogYear: catalog.catalogYear }))
function semesterResult(evidence) {
  const knownCredits = evidence.proposedCourses.reduce((sum, id) => sum + (courses.find(course => course.id === id)?.credits || 0), 0)
  const loadStatus = knownCredits > evidence.creditCap ? 'BLOCKED' : 'SATISFIED'
  return { status: loadStatus, courses: evidence.proposedCourses.map(courseId => ({ courseId, status: 'SATISFIED', reviewStatus: 'REVIEWED', reason: 'Stored rule check.',
    condition: { type: 'course', courseId, status: 'SATISFIED', reason: 'Stored rule check.', children: [] } })),
  creditLoad: { status: loadStatus, creditCap: evidence.creditCap, knownCredits, totalCredits: knownCredits, unresolvedCourses: [], reason: 'Current catalog credits.' } }
}
async function openPlanner(page) {
  await page.route('**/api/catalog/courses', route => route.fulfill({ json: courses }))
  await page.goto('/planner')
  await expect(page.locator('.planner-term')).toHaveCount(1)
}

test('saves, reloads, and compares two multi-term scenarios using fresh sequential checks', async ({ page }) => {
  const requests = []
  await page.route('**/api/semesters/eligibility', route => {
    const evidence = route.request().postDataJSON()
    requests.push(evidence)
    return route.fulfill({ json: semesterResult(evidence) })
  })
  await openPlanner(page)
  await page.getByRole('button', { name: '+ Add a term' }).click()
  const terms = page.locator('.planner-term')
  await terms.nth(0).getByLabel('Term label').fill('Fall 2026')
  await terms.nth(1).getByLabel('Term label').fill('Spring 2027')
  await terms.nth(0).getByRole('checkbox', { name: /CSC 116/ }).check()
  await terms.nth(0).getByLabel('Assume every course in this term is completed with credit').check()
  await terms.nth(1).getByRole('checkbox', { name: /CSC 216/ }).check()
  await page.getByLabel('Name for plan A').fill('CS sequence')
  await page.getByRole('button', { name: 'Save current as A', exact: true }).click()
  await expect(page.getByText('Saved CS sequence in this browser.', { exact: true })).toBeVisible()

  await terms.nth(0).getByRole('checkbox', { name: /CSC 116/ }).uncheck()
  await terms.nth(0).getByRole('checkbox', { name: /E 115/ }).check()
  await terms.nth(1).getByRole('checkbox', { name: /CSC 216/ }).uncheck()
  await terms.nth(1).getByRole('checkbox', { name: /CSC 116/ }).check()
  await page.getByLabel('Name for plan B').fill('E 115 first')
  await page.getByRole('button', { name: 'Save current as B', exact: true }).click()
  expect(requests).toHaveLength(0)
  const stored = await page.evaluate(key => JSON.parse(localStorage.getItem(key)), key)
  expect(stored.slots[0].plan.terms.map(term => term.proposedCourses)).toEqual([['csc-116-2026-2027'], ['csc-216-2026-2027']])
  expect(stored.slots[1].plan.terms.map(term => term.proposedCourses)).toEqual([['e-115-2026-2027'], ['csc-116-2026-2027']])
  expect(stored.slots[0].results).toBeUndefined()

  await page.reload()
  await expect(page.locator('.comparison-result')).toHaveCount(0)
  await page.getByRole('button', { name: 'Load A', exact: true }).click()
  await expect(page.locator('.planner-term').nth(0).getByLabel('Term label')).toHaveValue('Fall 2026')
  await expect(page.locator('.planner-term').nth(1).getByRole('checkbox', { name: /CSC 216/ })).toBeChecked()
  await page.getByRole('button', { name: 'Compare saved plans', exact: true }).click()
  await expect(page.locator('.comparison-result')).toHaveCount(4)
  expect(requests).toHaveLength(4)
  expect(requests.filter(evidence => evidence.proposedCourses.includes('csc-216-2026-2027'))[0].completedCourses).toEqual([
    { courseId: 'csc-116-2026-2027', grade: 'C', creditEarned: true },
  ])
  expect(requests.filter(evidence => evidence.proposedCourses.includes('csc-116-2026-2027'))).toHaveLength(2)
  await page.getByRole('button', { name: 'Delete A', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Load B', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Delete B', exact: true }).click()
  expect(await page.evaluate(key => localStorage.getItem(key), key)).toBeNull()
})
