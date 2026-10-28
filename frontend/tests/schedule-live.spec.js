import { test, expect } from '@playwright/test'

test('loads and selects a live Spring 2027 section from NC State Class Search', async ({ page }) => {
  test.skip(process.env.PACKPLAN_LIVE_SCHEDULE !== 'true', 'Requires the backend with outbound access to NC State Class Search')
  await page.goto('/planner')
  await expect(page.locator('.planner-term')).toHaveCount(1)
  const term = page.locator('.planner-term')
  await term.getByRole('checkbox', { name: /CSC 116/ }).check()
  await term.getByRole('button', { name: 'Find sections' }).click()
  await expect(term.locator('.section-options')).toContainText('Official results ↗', { timeout: 15000 })
  const sectionChoice = term.getByLabel('Lec section')
  expect(await sectionChoice.locator('option').count()).toBeGreaterThan(1)
  await sectionChoice.selectOption({ index: 1 })
  expect(await sectionChoice.inputValue()).toMatch(/^Lec:/)
  await expect(term.locator('.section-options')).toContainText(/Monday|Tuesday|Wednesday|Thursday|Friday/)
})
