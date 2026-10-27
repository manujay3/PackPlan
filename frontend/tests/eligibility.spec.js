import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const catalog = JSON.parse(readFileSync(new URL('../../backend/src/main/resources/catalog/courses.json', import.meta.url)))
const courses = catalog.courses.map(course => ({ ...course, catalogYear: catalog.catalogYear, sourceUrl: course.source?.sourceUrl || catalog.sourceUrl }))
const endpoint = '**/api/catalog/courses/*/eligibility'
function result(status = 'SATISFIED') {
  return {
    courseId: 'csc-216-2026-2027', status, reviewStatus: 'REVIEWED', reason: 'Result applies only to the stored enrollment rule.',
    condition: { type: 'allOf', courseId: null, status, reason: 'Every condition is required.', children: [
      { type: 'course', courseId: 'csc-116-2026-2027', status, reason: 'Recorded grade C; required C or better.', children: [] },
      { type: 'course', courseId: 'csc-217-2026-2027', status: 'SATISFIED', reason: 'Concurrent enrollment meets timing.', children: [] },
    ] },
  }
}
async function mockCatalog(page) {
  await page.route('**/api/catalog/courses', route => route.fulfill({ json: courses }))
}
async function fillEvidence(page) {
  await page.getByRole('button', { name: '+ Add completed attempt' }).click()
  const attempt = page.locator('.attempt-row').last()
  await attempt.getByLabel('Course', { exact: true }).selectOption('csc-116-2026-2027')
  await attempt.getByLabel('Grade or outcome').selectOption('C')
  await attempt.getByLabel('Earned credit?').selectOption('true')
  await page.getByRole('checkbox', { name: /CSC 217/ }).check()
}

test('submits explicit evidence, renders results, clears edits, and supports repeated attempts', async ({ page }, testInfo) => {
  await mockCatalog(page)
  let evidence
  await page.route(endpoint, route => { evidence = route.request().postDataJSON(); return route.fulfill({ json: result() }) })
  await page.goto('/eligibility')
  await expect(page.getByLabel('Target course')).toHaveValue('csc-216-2026-2027')
  await fillEvidence(page)
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Stored rule satisfied' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'CSC 216 requirements' })).toBeFocused()
  await expect(page.getByRole('heading', { name: 'Stored rule satisfied' })).toBeInViewport()
  expect(evidence).toEqual({ completedCourses: [{ courseId: 'csc-116-2026-2027', grade: 'C', creditEarned: true }], concurrentCourses: ['csc-217-2026-2027'] })
  await expect(page.locator('.root-conditions')).toContainText('CSC 116')
  await expect(page.locator('.root-conditions')).toContainText('CSC 217')
  await page.screenshot({ path: testInfo.outputPath('eligibility.png'), fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.attempt-row').getByLabel('Grade or outcome').selectOption('C_MINUS')
  await expect(page.getByRole('heading', { name: 'Stored rule satisfied' })).toHaveCount(0)
  await page.getByRole('button', { name: '+ Add completed attempt' }).click()
  await expect(page.locator('.attempt-row')).toHaveCount(2)
  await page.getByRole('button', { name: 'Remove attempt 2' }).click()
  await page.getByLabel('Target course').selectOption('csc-217-2026-2027')
  await expect(page.getByRole('checkbox', { name: /CSC 217/ })).toHaveCount(0)
  await expect(page.locator('.result-placeholder')).toBeVisible()
})

test('requires evidence fields and prevents claiming earned credit for failed attempts', async ({ page }) => {
  await mockCatalog(page)
  let submissions = 0
  await page.route(endpoint, route => { submissions++; return route.fulfill({ json: result('BLOCKED') }) })
  await page.goto('/eligibility')
  await page.getByRole('button', { name: '+ Add completed attempt' }).click()
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  expect(submissions).toBe(0)
  const attempt = page.locator('.attempt-row')
  await attempt.getByLabel('Course', { exact: true }).selectOption('csc-116-2026-2027')
  await attempt.getByLabel('Grade or outcome').selectOption('F')
  await expect(attempt.getByLabel('Earned credit?')).toHaveValue('false')
  await expect(attempt.getByLabel('Earned credit?').locator('option[value=true]')).toBeDisabled()
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Requirements not met' })).toBeVisible()
})

test('preserves evidence through a server error and retries successfully', async ({ page }) => {
  await mockCatalog(page)
  let calls = 0
  await page.route(endpoint, route => ++calls === 1 ? route.fulfill({ status: 503 }) : route.fulfill({ json: result() }))
  await page.goto('/eligibility')
  await fillEvidence(page)
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Your entries are still here')
  await expect(page.locator('.attempt-row').getByLabel('Grade or outcome')).toHaveValue('C')
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Stored rule satisfied' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('handles rejected evidence and a timed-out request without losing entries', async ({ page }) => {
  await mockCatalog(page)
  await page.clock.install()
  let calls = 0
  let pending
  await page.route(endpoint, route => {
    calls++
    if (calls === 1) return route.fulfill({ status: 400 })
    if (calls === 2) { pending = route; return }
    return route.fulfill({ json: result() })
  })
  await page.goto('/eligibility')
  await fillEvidence(page)
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Some evidence was not accepted')
  const requested = page.waitForRequest('**/api/catalog/courses/*/eligibility')
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await requested
  await page.clock.fastForward(8001)
  await expect(page.getByRole('alert')).toContainText('The check took too long')
  await pending.abort().catch(() => {})
  await expect(page.locator('.attempt-row').getByLabel('Grade or outcome')).toHaveValue('C')
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Stored rule satisfied' })).toBeVisible()
})

test('shows loading, catalog failure, an empty catalog, and recovery', async ({ page }) => {
  let release
  const hold = new Promise(resolve => { release = resolve })
  let calls = 0
  await page.route('**/api/catalog/courses', async route => {
    calls++
    if (calls === 1) { await hold; return route.fulfill({ status: 503 }) }
    return route.fulfill({ json: calls === 2 ? [] : courses })
  })
  await page.goto('/eligibility')
  await expect(page.getByRole('heading', { name: 'Loading courses…' })).toBeVisible()
  release()
  await expect(page.getByRole('heading', { name: 'Course list unavailable' })).toBeVisible()
  await page.getByRole('button', { name: 'Reload courses' }).click()
  await expect(page.getByRole('heading', { name: 'No courses available yet' })).toBeVisible()
  await page.getByRole('button', { name: 'Reload courses' }).click()
  await expect(page.getByLabel('Target course')).toBeVisible()
})

test('missing rules show manual review and malformed results never show success', async ({ page }) => {
  await mockCatalog(page)
  let calls = 0
  await page.route(endpoint, route => route.fulfill({ json: ++calls === 1
    ? { courseId: 'csc-216-2026-2027', status: 'MANUAL_REVIEW', reviewStatus: null, condition: null, reason: 'No stored rule is available.' }
    : { status: 'SATISFIED' } }))
  await page.goto('/eligibility')
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Manual review needed' })).toBeVisible()
  await expect(page.getByText('No stored rule available', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Could not check')
  await expect(page.getByRole('heading', { name: 'Stored rule satisfied' })).toHaveCount(0)
})

test('an edited request cannot overwrite a newer result', async ({ page }) => {
  await mockCatalog(page)
  let release
  let finished
  const hold = new Promise(resolve => { release = resolve })
  const firstFinished = new Promise(resolve => { finished = resolve })
  let calls = 0
  await page.route(endpoint, async route => {
    if (++calls === 1) {
      await hold
      await route.fulfill({ json: result() }).catch(() => {})
      finished()
    } else await route.fulfill({ json: result('BLOCKED') })
  })
  await page.goto('/eligibility')
  await fillEvidence(page)
  const requested = page.waitForRequest('**/api/catalog/courses/*/eligibility')
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await requested
  await expect(page.getByRole('button', { name: 'Checking requirements…' })).toBeDisabled()
  await page.locator('.attempt-row').getByLabel('Grade or outcome').selectOption('C_MINUS')
  await page.getByRole('button', { name: 'Check course requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Requirements not met' })).toBeVisible()
  release()
  await firstFinished
  await expect(page.getByRole('heading', { name: 'Stored rule satisfied' })).toHaveCount(0)
})

test('leaving the page clears evidence and the route supports direct refresh', async ({ page }) => {
  await mockCatalog(page)
  await page.goto('/eligibility')
  await fillEvidence(page)
  await page.getByRole('link', { name: 'About the project' }).click()
  await page.getByRole('link', { name: 'Check a course', exact: true }).click()
  await expect(page.locator('.attempt-row')).toHaveCount(0)
  await expect(page.getByRole('checkbox', { name: /CSC 217/ })).not.toBeChecked()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Check your next course.' })).toBeVisible()
})
