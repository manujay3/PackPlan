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

function makeTranscriptPdf() {
  const stream = 'BT /F1 10 Tf 72 720 Td (Course Description) Tj 160 0 Td (Term) Tj 70 0 Td (Grade) Tj 70 0 Td (Units) Tj ET BT /F1 10 Tf 72 700 Td (CSC 116 Intro to Computing) Tj 160 0 Td (Fall 2025) Tj 70 0 Td (A-) Tj 70 0 Td (3.000) Tj ET'
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`,
  ]
  let pdf = '%PDF-1.4\n'
  const offsets = [0]
  for (let index = 0; index < objects.length; index++) {
    offsets.push(Buffer.byteLength(pdf, 'binary'))
    pdf += `${index + 1} 0 obj\n${objects[index]}\nendobj\n`
  }
  const xrefOffset = Buffer.byteLength(pdf, 'binary')
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`
  return Buffer.from(pdf, 'binary')
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

test('imports a course-history CSV locally and leaves ambiguous earned credit for manual confirmation', async ({ page }) => {
  await openPlanner(page)
  await page.getByLabel('Course history file').setInputFiles({ name: 'history.csv', mimeType: 'text/csv',
    buffer: Buffer.from('Course,Grade,Credits\n"CSC 116, Java",C,3\nCSC 999,Fall 2025,3') })
  await expect(page.locator('.course-import-table tbody tr')).toHaveCount(2)
  await expect(page.getByRole('button', { name: 'Add 1 reviewed row to prior attempts' })).toBeVisible()
  await page.getByRole('button', { name: 'Add 1 reviewed row to prior attempts' }).click()
  const attempt = page.locator('.attempt-row')
  await expect(attempt.getByLabel('Course')).toHaveValue('csc-116-2026-2027')
  await expect(attempt.getByLabel('Grade or outcome')).toHaveValue('C')
  await expect(attempt.getByLabel('Earned credit?')).toHaveValue('')
  await attempt.getByLabel('Earned credit?').selectOption('true')
  await expect(attempt.getByLabel('Earned credit?')).toHaveValue('true')
})

test('maps NC State IN, LA, and CR transcript grades while keeping earned credit separate', async ({ page }) => {
  await openPlanner(page)
  await page.locator('.course-import summary').click()
  await page.getByLabel('Paste copied course-history table').fill('Subject\tCatalog Nbr\tGrade\tUnits\nCSC\t116\tIN\t3.000\nCSC\t216\tCR\t3.000\nCSC\t217\tLA\t1.000')
  await page.getByRole('button', { name: 'Preview pasted rows' }).click()
  await expect(page.locator('.course-import-table tbody tr')).toHaveCount(3)
  await page.getByRole('button', { name: 'Add 3 reviewed rows to prior attempts' }).click()
  const attempts = page.locator('.attempt-row')
  await expect(attempts.nth(0).getByLabel('Grade or outcome')).toHaveValue('INCOMPLETE')
  await expect(attempts.nth(0).getByLabel('Earned credit?')).toHaveValue('')
  await expect(attempts.nth(1).getByLabel('Grade or outcome')).toHaveValue('CREDIT')
  await expect(attempts.nth(1).getByLabel('Earned credit?')).toHaveValue('')
  await expect(attempts.nth(2).getByLabel('Grade or outcome')).toHaveValue('INCOMPLETE')
})

test('reviews course codes from a MyPack Degree Audit before adding selected choices to a term', async ({ page }) => {
  await openPlanner(page)
  await page.locator('.degree-audit-import summary').click()
  await page.getByLabel('Paste copied audit text or table').fill('Requirement\tCourse options\nMajor core\tCSC 216 or CSC 217\nElective\tCSC 326')
  await page.getByRole('button', { name: 'Preview audit course codes' }).click()
  await expect(page.locator('.degree-audit-import tbody tr')).toHaveCount(3)
  await expect(page.getByRole('button', { name: 'Add 0 selected courses to plan' })).toBeDisabled()
  await page.getByRole('checkbox', { name: 'Plan CSC 216' }).check()
  await page.getByRole('checkbox', { name: 'Plan CSC 326' }).check()
  await page.getByRole('button', { name: 'Add 2 selected courses to plan' }).click()
  const spring = page.locator('.planner-term').first()
  await expect(spring.getByRole('checkbox', { name: /CSC 216/ })).toBeChecked()
  await expect(spring.getByText('CSC 326 · not in current catalog; credit total needs review')).toBeVisible()
})

test('converts a copied Degree Audit HTML table and retains its requirement context', async ({ page }) => {
  await openPlanner(page)
  await page.locator('.degree-audit-import summary').click()
  await page.getByLabel('Paste copied audit text or table').evaluate(element => {
    const clipboard = new DataTransfer()
    clipboard.setData('text/html', '<table><tr><th>Requirement</th><th>Course options</th></tr><tr><td>Major core</td><td>CSC 216 or CSC 217</td></tr></table>')
    clipboard.setData('text/plain', 'Major core CSC 216 or CSC 217')
    element.dispatchEvent(new ClipboardEvent('paste', { clipboardData: clipboard, bubbles: true, cancelable: true }))
  })
  await expect(page.getByLabel('Paste copied audit text or table')).toHaveValue('Requirement\tCourse options\nMajor core\tCSC 216 or CSC 217')
  await page.getByRole('button', { name: 'Preview audit course codes' }).click()
  const rows = page.locator('.degree-audit-import tbody tr')
  await expect(rows).toHaveCount(2)
  await expect(rows.first()).toContainText('Major core CSC 216 or CSC 217')
  await page.getByRole('checkbox', { name: 'Plan CSC 217' }).check()
  await page.getByRole('button', { name: 'Add 1 selected course to plan' }).click()
  await expect(page.locator('.planner-term').first().getByRole('checkbox', { name: /CSC 217/ })).toBeChecked()
})

test('saves and restores an incomplete planning draft with unresolved transcript credit', async ({ page }) => {
  await openPlanner(page)
  await page.locator('.course-import summary').click()
  await page.getByLabel('Paste copied course-history table').fill('Course\tGrade\nCSC 116\tA')
  await page.getByRole('button', { name: 'Preview pasted rows' }).click()
  await page.getByRole('button', { name: 'Add 1 reviewed row to prior attempts' }).click()
  await page.getByRole('button', { name: 'Save progress' }).click()
  await expect(page.locator('.draft-actions [role="status"]')).toContainText('Progress saved in this browser')
  await page.reload()
  await expect(page.getByRole('button', { name: 'Load saved progress' })).toBeEnabled()
  await page.getByRole('button', { name: 'Load saved progress' }).click()
  const attempt = page.locator('.attempt-row')
  await expect(attempt.getByLabel('Course')).toHaveValue('csc-116-2026-2027')
  await expect(attempt.getByLabel('Grade or outcome')).toHaveValue('A')
  await expect(attempt.getByLabel('Earned credit?')).toHaveValue('')
})

test('previews copied transcript-style history with term and unit columns without creating an intermediate file', async ({ page }) => {
  await openPlanner(page)
  await page.locator('.course-import summary').click()
  await page.getByLabel('Paste copied course-history table').fill('Subject\tCatalog Nbr\tTerm\tGrade\tUnits\nCSC\t116\tFall 2025\tA-\t3.000\nMA\t141\tFall 2025\tB\t3.000')
  await page.getByRole('button', { name: 'Preview pasted rows' }).click()
  await expect(page.locator('.course-import-table tbody tr')).toHaveCount(2)
  await expect(page.locator('.course-import-table tbody tr').nth(0)).toContainText('CSC 116')
  await expect(page.locator('.course-import-table tbody tr').nth(0)).toContainText('A_MINUS')
  await page.getByRole('button', { name: 'Add 2 reviewed rows to prior attempts' }).click()
  await expect(page.locator('.attempt-row')).toHaveCount(2)
  await expect(page.locator('.attempt-row').nth(0).getByLabel('Earned credit?')).toHaveValue('')
})

test('converts a copied MyPack HTML table to editable tabular text before preview', async ({ page }) => {
  await openPlanner(page)
  await page.locator('.course-import summary').click()
  await page.getByLabel('Paste copied course-history table').evaluate(element => {
    const clipboard = new DataTransfer()
    clipboard.setData('text/html', '<table><tr><th>Subject</th><th>Catalog Nbr</th><th>Term</th><th>Grade</th><th>Units</th></tr><tr><td>CSC</td><td>116</td><td>Fall 2025</td><td>A-</td><td>3.000</td></tr></table>')
    clipboard.setData('text/plain', 'copied fallback text')
    element.dispatchEvent(new ClipboardEvent('paste', { clipboardData: clipboard, bubbles: true, cancelable: true }))
  })
  const pasted = page.getByLabel('Paste copied course-history table')
  await expect(pasted).toHaveValue('Subject\tCatalog Nbr\tTerm\tGrade\tUnits\nCSC\t116\tFall 2025\tA-\t3.000')
  await expect(page.locator('.course-import [role="status"]')).toContainText('converted locally')
  await page.getByRole('button', { name: 'Preview pasted rows' }).click()
  await expect(page.locator('.course-import-table tbody tr')).toHaveCount(1)
  await expect(page.locator('.course-import-table tbody tr').first()).toContainText('CSC 116')
})

test('extracts selectable text from a MyPack transcript PDF locally and previews it for review', async ({ page }) => {
  await openPlanner(page)
  await page.locator('.course-import summary').click()
  await page.getByLabel('Course history file').setInputFiles({ name: 'transcript.pdf', mimeType: 'application/pdf', buffer: makeTranscriptPdf() })
  await expect(page.locator('.course-import [role="status"]')).toContainText('PDF text extracted in this browser')
  await expect(page.getByLabel('Paste copied course-history table')).toHaveValue(/CSC 116/)
  await page.getByRole('button', { name: 'Preview pasted rows' }).click()
  await expect(page.locator('.course-import-table tbody tr')).toHaveCount(1)
  await expect(page.locator('.course-import-table tbody tr').first()).toContainText('CSC 116')
  await expect(page.locator('.course-import-table tbody tr').first()).toContainText('A')
  await expect(page.getByRole('button', { name: 'Add 1 reviewed row to prior attempts' })).toBeEnabled()
})

test('loads official-style Spring sections, flags overlapping meetings, and saves the section choices', async ({ page }) => {
  await page.route('**/api/schedule/sections*', route => {
    const url = new URL(route.request().url())
    const code = url.searchParams.get('courseCode')
    const first = code === 'CSC 116'
    return route.fulfill({ json: { term: '2271', courseCode: code, title: code, credits: '3', description: '',
      sourceUrl: 'https://webappprd.acs.ncsu.edu/php/coursecat/index.php?term=2271', retrievedAt: '2026-10-27T12:00:00Z',
      sections: [{ section: '001', component: 'Lec', classNumber: first ? '1001' : '2001', availability: 'Open 20/30',
        meetingDays: ['Monday'], startTime: first ? '10:00' : '10:30', endTime: first ? '11:00' : '11:30',
        meetingText: first ? 'M 10:00 AM - 11:00 AM' : 'M 10:30 AM - 11:30 AM', location: 'Campus', instructor: 'Instructor',
        dates: '01/11/27 - 04/27/27', notes: [] }] } })
  })
  await openPlanner(page)
  const term = page.locator('.planner-term')
  await term.getByRole('checkbox', { name: /CSC 116/ }).check()
  await term.getByRole('checkbox', { name: /CSC 216/ }).check()
  await term.getByLabel('Add another course by code').fill('CSC 326')
  await term.getByRole('button', { name: 'Add course' }).click()
  const sectionCourses = term.locator('.section-course')
  await sectionCourses.nth(0).getByRole('button', { name: 'Find sections' }).click()
  await expect(sectionCourses.nth(0).getByLabel('Lec section')).toBeVisible()
  await expect(term.getByText(/Known catalog credits: 6/)).toBeVisible()
  await sectionCourses.nth(1).getByRole('button', { name: 'Find sections' }).click()
  await expect(sectionCourses.nth(1).getByLabel('Lec section')).toBeVisible()
  await sectionCourses.nth(2).getByRole('button', { name: 'Find sections' }).click()
  await expect(sectionCourses.nth(2).getByLabel('Lec section')).toBeVisible()
  await expect(term.getByText(/Known catalog credits: 9/)).toBeVisible()
  await sectionCourses.nth(0).getByLabel('Lec section').selectOption('Lec:1001')
  await sectionCourses.nth(1).getByLabel('Lec section').selectOption('Lec:2001')
  await expect(page.getByRole('alert', { name: 'Schedule time conflicts' })).toContainText('CSC 116 Lec 001 conflicts with CSC 216 Lec 001')
  await page.getByRole('button', { name: 'Save current as A', exact: true }).click()
  const saved = await page.evaluate(key => JSON.parse(localStorage.getItem(key)), key)
  expect(saved.slots[0].plan.terms[0].sectionSelections.map(section => section.classNumber)).toEqual(['1001', '2001'])
})

test('keeps the sample planner and local saves available when the catalog service is down', async ({ page }) => {
  await page.route('**/api/catalog/courses', route => route.fulfill({ status: 503 }))
  await page.route('**/api/semesters/eligibility', route => route.fulfill({ status: 503 }))
  await page.goto('/planner')
  await expect(page.getByText(/bundled eight-course sample/)).toBeVisible()
  const term = page.locator('.planner-term')
  await term.getByLabel('Add another course by code').fill('CSC 326')
  await term.getByRole('button', { name: 'Add course' }).click()
  await expect(term.getByText('CSC 326 · not in current catalog; credit total needs review')).toBeVisible()
  await term.getByRole('checkbox', { name: /CSC 116/ }).check()
  await page.getByRole('button', { name: 'Save current as A', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Load A', exact: true })).toBeVisible()
  const saved = await page.evaluate(key => JSON.parse(localStorage.getItem(key)), key)
  expect(saved.slots[0].plan.terms[0].proposedCourses).toEqual(['CSC 326', 'csc-116-2026-2027'])
  await term.getByLabel('Credit cap').fill('15')
  await page.getByRole('button', { name: 'Save current as B', exact: true }).click()
  await page.getByRole('button', { name: 'Compare saved plans', exact: true }).click()
  await expect(page.locator('.alternative-slot').getByRole('alert')).toHaveCount(2)
  await expect(page.locator('.comparison-result')).toHaveCount(0)
})
