import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const key = 'packplan.semester-alternatives.v1'
const catalog = JSON.parse(readFileSync(new URL('../../backend/src/main/resources/catalog/courses.json', import.meta.url)))
const courses = catalog.courses.map(course => ({ ...course, catalogYear: catalog.catalogYear }))
const evidence = cap => ({ creditCap: cap, proposedCourses: ['csc-216-2026-2027', 'csc-217-2026-2027'], completedCourses: [{ courseId: 'csc-116-2026-2027', grade: 'C', creditEarned: true }] })
const record = (name, cap) => ({ name, savedAt: '2026-10-27T00:00:00Z', evidence: evidence(cap) })
const response = input => ({ status: input.creditCap < 4 ? 'BLOCKED' : 'SATISFIED',
  creditLoad: { creditCap: input.creditCap, knownCredits: 4, totalCredits: 4, unresolvedCourses: [], reason: 'Current catalog credits.', status: input.creditCap < 4 ? 'BLOCKED' : 'SATISFIED' },
  courses: input.proposedCourses.map(courseId => ({ courseId, status: 'SATISFIED', reviewStatus: 'REVIEWED', reason: 'Stored rule check.',
    condition: { type: 'course', courseId: 'csc-116-2026-2027', status: 'SATISFIED', reason: 'Prior credit meets threshold.', children: [] } })) })
async function setup(page) {
  await page.route('**/api/catalog/courses', route => route.fulfill({ json: courses }))
  await page.goto('/semester')
  await expect(page.getByLabel('Your semester credit cap')).toBeVisible()
}
async function seed(page, slots) {
  await page.addInitScript(({ key, slots }) => localStorage.setItem(key, JSON.stringify({ version: 1, slots })), { key, slots })
}
async function fill(page) {
  await page.getByLabel('Your semester credit cap').fill('4')
  await page.getByRole('checkbox', { name: /CSC 216/ }).check()
  await page.getByRole('checkbox', { name: /CSC 217/ }).check()
  await page.getByRole('button', { name: '+ Add completed attempt' }).click()
  const row = page.locator('.attempt-row')
  await row.getByLabel('Course', { exact: true }).selectOption('csc-116-2026-2027')
  await row.getByLabel('Grade or outcome').selectOption('C')
  await row.getByLabel('Earned credit?').selectOption('true')
}

test('saves two snapshots across reload, loads inputs, compares fresh, and deletes independently', async ({ page }, testInfo) => {
  const requests = []
  await page.route('**/api/semesters/eligibility', route => {
    const input = route.request().postDataJSON(); requests.push(input)
    return route.fulfill({ json: response(input) })
  })
  await setup(page)
  await fill(page)
  await page.getByLabel('Name for alternative A').fill('Lecture and lab')
  await page.getByRole('button', { name: 'Save current as A', exact: true }).click()
  await expect(page.getByText('Saved Lecture and lab in this browser.', { exact: true })).toBeVisible()
  await page.getByLabel('Your semester credit cap').fill('3')
  await page.getByLabel('Name for alternative B').fill('Lower cap')
  await page.getByRole('button', { name: 'Save current as B', exact: true }).click()
  expect(requests).toHaveLength(0)
  await page.reload()
  await expect(page.getByLabel('Your semester credit cap')).toHaveValue('')
  await expect(page.locator('.comparison-result')).toHaveCount(0)
  await page.getByRole('button', { name: 'Load A', exact: true }).click()
  await expect(page.getByLabel('Your semester credit cap')).toHaveValue('4')
  await expect(page.locator('.attempt-row').getByLabel('Grade or outcome')).toHaveValue('C')
  await page.getByRole('button', { name: 'Compare saved alternatives', exact: true }).click()
  await expect(page.locator('.comparison-result')).toHaveCount(2)
  await expect(page.locator('.alternative-slot').nth(0).getByRole('heading', { name: 'Stored semester rules satisfied' })).toBeVisible()
  await expect(page.locator('.alternative-slot').nth(1).getByRole('heading', { name: 'Semester has blockers' })).toBeVisible()
  expect(requests).toHaveLength(2)
  expect(requests).toEqual(expect.arrayContaining([evidence(4), evidence(3)]))
  await expect(page.getByRole('heading', { name: 'Save and compare semesters' })).toBeFocused()
  await expect(page.getByLabel('Your semester credit cap')).toHaveValue('4')
  await page.locator('.alternatives-panel').screenshot({ path: testInfo.outputPath('alternatives.png') })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Delete A', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Load A', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Load B', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Compare saved alternatives', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Delete B', exact: true }).click()
  expect(await page.evaluate(key => localStorage.getItem(key), key)).toBeNull()
})

test('one failed comparison stays an error and retry requests both alternatives again', async ({ page }) => {
  await seed(page, [record('A', 4), record('B', 3)])
  let fail = true
  let calls = 0
  await page.route('**/api/semesters/eligibility', route => {
    calls++
    const input = route.request().postDataJSON()
    return input.creditCap === 3 && fail ? route.fulfill({ status: 503 }) : route.fulfill({ json: response(input) })
  })
  await setup(page)
  await page.getByRole('button', { name: 'Compare saved alternatives', exact: true }).click()
  await expect(page.locator('.alternative-slot').nth(0).locator('.comparison-result')).toBeVisible()
  await expect(page.locator('.alternative-slot').nth(1).getByRole('alert')).toContainText('Could not check this alternative')
  fail = false
  await page.getByRole('button', { name: 'Compare saved alternatives', exact: true }).click()
  await expect(page.locator('.comparison-result')).toHaveCount(2)
  await expect(page.locator('.alternative-slot').nth(1).getByRole('alert')).toHaveCount(0)
  expect(calls).toBe(4)
})

test('incompatible saves can be cleared and quota failures do not claim a save', async ({ page }) => {
  await page.addInitScript(key => localStorage.setItem(key, '{broken'), key)
  await setup(page)
  await expect(page.locator('.alternatives-panel').getByRole('alert')).toContainText('could not be read')
  await page.getByRole('button', { name: 'Clear saved alternatives' }).click()
  await fill(page)
  await page.getByRole('button', { name: 'Save current as A', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Load A', exact: true })).toBeVisible()
  const original = await page.evaluate(key => localStorage.getItem(key), key)
  await page.evaluate(() => { Storage.prototype.setItem = () => { throw new DOMException('Quota exceeded', 'QuotaExceededError') } })
  await page.getByLabel('Your semester credit cap').fill('3')
  await page.getByRole('button', { name: 'Replace A', exact: true }).click()
  await expect(page.locator('.alternatives-panel').getByRole('alert')).toContainText('Could not save')
  expect(await page.evaluate(key => localStorage.getItem(key), key)).toBe(original)
  await expect(page.locator('.alternatives-panel').getByText(/Saved Alternative A in this browser/)).toHaveCount(0)
})

test('loading retains unavailable courses and does not present a partial credit total as complete', async ({ page }) => {
  const saved = record('Older catalog', 4)
  saved.evidence.proposedCourses = ['removed-2025-2026']
  saved.evidence.completedCourses[0].courseId = 'old-prerequisite-2025-2026'
  await seed(page, [saved, null])
  await setup(page)
  await page.getByRole('button', { name: 'Load A', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: /removed-2025-2026/ })).toBeChecked()
  await expect(page.locator('.attempt-row').getByLabel('Course', { exact: true })).toHaveValue('old-prerequisite-2025-2026')
  await expect(page.getByText(/1 selected · Credit total unavailable/)).toBeVisible()
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key)).slots[0].evidence, key)).toEqual(saved.evidence)
})

test('another tab invalidates an in-flight comparison without replacing the current draft', async ({ page, context }) => {
  await seed(page, [record('A', 4), record('B', 3)])
  let release
  const hold = new Promise(resolve => { release = resolve })
  let calls = 0
  await page.route('**/api/semesters/eligibility', async route => {
    calls++
    await hold
    await route.fulfill({ json: response(route.request().postDataJSON()) }).catch(() => {})
  })
  await setup(page)
  await page.getByLabel('Your semester credit cap').fill('9')
  await page.getByRole('button', { name: 'Compare saved alternatives', exact: true }).click()
  await expect.poll(() => calls).toBe(2)
  const other = await context.newPage()
  await setup(other)
  await other.evaluate(key => {
    const data = JSON.parse(localStorage.getItem(key))
    data.slots[1].name = 'Changed elsewhere'
    localStorage.setItem(key, JSON.stringify(data))
  }, key)
  await expect(page.getByText(/Saved alternatives changed in another tab/)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Alternative B · Changed elsewhere' })).toBeVisible()
  await expect(page.getByLabel('Your semester credit cap')).toHaveValue('9')
  release()
  await expect(page.locator('.comparison-result')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Compare saved alternatives', exact: true })).toBeEnabled()
  await other.close()
})
