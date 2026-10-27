import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const catalog = JSON.parse(readFileSync(new URL('../../backend/src/main/resources/catalog/courses.json', import.meta.url)))
const courses = catalog.courses.map(course => ({ ...course, catalogYear: catalog.catalogYear }))
const endpoint = '**/api/semesters/eligibility'
function courseResult(id, status = 'SATISFIED') {
  return { courseId: id, status, reviewStatus: status === 'MANUAL_REVIEW' ? null : 'REVIEWED',
    reason: 'Result applies only to stored rules.', condition: status === 'MANUAL_REVIEW' ? null
      : { type: 'course', courseId: 'csc-116-2026-2027', status, reason: 'Prior credit check.', children: [] } }
}
async function setup(page) {
  await page.route('**/api/catalog/courses', route => route.fulfill({ json: courses }))
  await page.goto('/semester')
}
async function pair(page) {
  await page.getByRole('checkbox', { name: /CSC 216/ }).check()
  await page.getByRole('checkbox', { name: /CSC 217/ }).check()
}

test('validates a proposed pair, shows each course, and clears results when a course is removed', async ({ page }, testInfo) => {
  let body
  let blocked = false
  await page.route(endpoint, route => {
    body = route.request().postDataJSON()
    return route.fulfill({ json: { status: blocked ? 'BLOCKED' : 'SATISFIED', courses: body.proposedCourses.map(id => courseResult(id, blocked ? 'BLOCKED' : 'SATISFIED')) } })
  })
  await setup(page)
  await page.getByRole('button', { name: 'Check semester requirements', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Select at least one')
  expect(body).toBeUndefined()
  await page.getByRole('button', { name: '+ Add completed attempt' }).click()
  const attempt = page.locator('.attempt-row')
  await attempt.getByLabel('Course', { exact: true }).selectOption('csc-116-2026-2027')
  await attempt.getByLabel('Grade or outcome').selectOption('C')
  await attempt.getByLabel('Earned credit?').selectOption('true')
  await pair(page)
  await expect(page.getByText(/2 selected · 4 credits/)).toBeVisible()
  await page.getByRole('button', { name: 'Check semester requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Stored semester rules satisfied' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Semester requirements', exact: true })).toBeFocused()
  expect(body).toEqual({ completedCourses: [{ courseId: 'csc-116-2026-2027', grade: 'C', creditEarned: true }], proposedCourses: ['csc-216-2026-2027', 'csc-217-2026-2027'] })
  await expect(page.locator('.semester-course-result')).toHaveCount(2)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('semester.png'), fullPage: true })
  await page.getByRole('checkbox', { name: /CSC 217/ }).uncheck()
  await expect(page.getByRole('heading', { name: 'Stored semester rules satisfied' })).toHaveCount(0)
  blocked = true
  await page.getByRole('button', { name: 'Check semester requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Semester has blockers' })).toBeVisible()
  await expect(page.locator('.semester-course-result')).toHaveCount(1)
})

test('missing course rules keep the whole semester under review', async ({ page }) => {
  await page.route(endpoint, route => route.fulfill({ json: { status: 'MANUAL_REVIEW', courses: [courseResult('ma-141-2026-2027', 'MANUAL_REVIEW')] } }))
  await setup(page)
  await page.getByRole('checkbox', { name: /MA 141/ }).check()
  await page.getByRole('button', { name: 'Check semester requirements', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Semester needs review' })).toBeVisible()
  await expect(page.getByText('No stored rule available', { exact: true })).toBeVisible()
})

test('rejects incomplete or inconsistent semester responses and preserves selections for retry', async ({ page }) => {
  let calls = 0
  await page.route(endpoint, route => route.fulfill({ json: ++calls === 1
    ? { status: 'SATISFIED', courses: [] }
    : { status: 'SATISFIED', courses: [courseResult('csc-216-2026-2027', 'BLOCKED'), courseResult('csc-217-2026-2027')] } }))
  await setup(page)
  await pair(page)
  for (let i = 0; i < 2; i++) {
    await page.getByRole('button', { name: 'Check semester requirements', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('Could not check this semester')
    await expect(page.getByRole('heading', { name: 'Stored semester rules satisfied' })).toHaveCount(0)
  }
  await expect(page.getByRole('checkbox', { name: /CSC 216/ })).toBeChecked()
})

test('course and semester screens do not carry evidence between modes', async ({ page }) => {
  await setup(page)
  await pair(page)
  await page.getByRole('link', { name: 'Check a course', exact: true }).click()
  await expect(page.getByLabel('Target course')).toBeVisible()
  await page.getByRole('link', { name: 'Check a semester', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: /CSC 216/ })).not.toBeChecked()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Check a semester together.' })).toBeVisible()
})
