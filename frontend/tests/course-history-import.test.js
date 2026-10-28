import { test } from 'node:test'
import assert from 'node:assert/strict'
import { courseCodeFrom, findColumn, normalizeCreditEarned, normalizeGrade, parseDelimited } from '../src/courseHistoryImport.js'

test('parses quoted CSV fields and detects course, grade, and earned-credit columns', () => {
  const parsed = parseDelimited('Course,Grade,Earned Credit\n"CSC 116, Java",C,Yes\n"MA 141",A-,No')
  assert.deepEqual(parsed.headers, ['Course', 'Grade', 'Earned Credit'])
  assert.deepEqual(parsed.rows[0], ['CSC 116, Java', 'C', 'Yes'])
  assert.equal(findColumn(parsed.headers, /grade|mark/), 1)
  assert.equal(findColumn(parsed.headers, /credit\s*earned|earned\s*credit/), 2)
})
test('parses tab-separated copied rows and canonicalizes course codes and common grade labels', () => {
  const parsed = parseDelimited('Course\tFinal Grade\tTerm\nCSC 116 Introduction to Java\tB+\tFall 2025')
  assert.equal(parsed.delimiter, '\t')
  assert.equal(courseCodeFrom(parsed.rows[0][0]), 'CSC 116')
  assert.equal(normalizeGrade(parsed.rows[0][1]), 'B_PLUS')
  assert.equal(normalizeGrade('S'), 'SATISFACTORY')
  assert.equal(normalizeGrade('IN'), 'INCOMPLETE')
  assert.equal(normalizeGrade('LA'), 'INCOMPLETE')
  assert.equal(normalizeGrade('CR'), 'CREDIT')
  assert.equal(normalizeGrade('AU'), '')
  assert.equal(normalizeGrade('NR'), '')
  assert.equal(normalizeGrade('not posted'), '')
})
test('finds split subject and catalog number fields in transcript-style column names', () => {
  const headers = ['Subject', 'Catalog Nbr', 'Term', 'Grade', 'Units']
  assert.equal(findColumn(headers, /course|class|subject/), 0)
  assert.equal(findColumn(headers, /catalog\s*(number|no|nbr|num)|course\s*(number|no|nbr|num)|\b(number|nbr)\b/), 1)
  assert.equal(findColumn(headers, /grade|mark|outcome/), 3)
})
test('only imports explicit earned-credit yes/no values and rejects malformed row shapes', () => {
  assert.equal(normalizeCreditEarned('Earned'), true)
  assert.equal(normalizeCreditEarned('no credit'), false)
  assert.equal(normalizeCreditEarned('3.000'), null)
  assert.throws(() => parseDelimited('Course,Grade\nCSC 116,C\nCSC 216'))
  assert.throws(() => parseDelimited('Course,Grade'))
})
