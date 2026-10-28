import { test } from 'node:test'
import assert from 'node:assert/strict'
import { extractDegreeAuditCourses } from '../src/degreeAuditImport.js'

test('extracts distinct course options with their source rows for explicit review', () => {
  const result = extractDegreeAuditCourses('Major requirement: CSC 116 or CSC 216\n\nCompleted: MA 141\nMajor requirement: CSC 116 or CSC 216')
  assert.deepEqual(result, [
    { code: 'CSC 116', context: 'Major requirement: CSC 116 or CSC 216' },
    { code: 'CSC 216', context: 'Major requirement: CSC 116 or CSC 216' },
    { code: 'MA 141', context: 'Completed: MA 141' },
  ])
})

test('handles compact subject-number pairs and ignores prose with no course code', () => {
  assert.deepEqual(extractDegreeAuditCourses('Elective options: E115, COS 100.\nNo courses remain.'), [
    { code: 'E 115', context: 'Elective options: E115, COS 100.' },
    { code: 'COS 100', context: 'Elective options: E115, COS 100.' },
  ])
})

test('does not invent rows for empty audit content', () => {
  assert.deepEqual(extractDegreeAuditCourses(' \n\n'), [])
})
