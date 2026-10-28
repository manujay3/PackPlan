import { test } from 'node:test'
import assert from 'node:assert/strict'
import { findTimeConflicts, sectionKey } from '../src/sectionSchedule.js'

const section = (courseCode, classNumber, meetingDays, startTime, endTime, dates = '01/11/27 - 04/27/27') => ({
  courseId: courseCode, courseCode, section: '001', component: 'Lec', classNumber, availability: 'Open 20/30',
  meetingDays, startTime, endTime, meetingText: `${meetingDays.join('/')} ${startTime}-${endTime}`,
  location: 'Campus', instructor: 'Instructor', dates, notes: [], retrievedAt: '2026-10-27T12:00:00Z',
})

test('identifies only overlapping meetings on shared days and overlapping dates', () => {
  const terms = [{ label: 'Spring 2027', sectionSelections: [
    section('CSC 116', '1001', ['Monday', 'Wednesday'], '10:40', '12:30'),
    section('MA 141', '2001', ['Monday'], '12:00', '13:15'),
    section('ST 101', '3001', ['Tuesday'], '11:00', '12:00'),
    section('E 115', '4001', ['Wednesday'], '11:00', '12:00', '11/01/26 - 12/20/26'),
  ] }]
  const conflicts = findTimeConflicts(terms)
  assert.equal(conflicts.length, 1)
  assert.equal(conflicts[0].left.courseCode, 'CSC 116')
  assert.equal(conflicts[0].right.courseCode, 'MA 141')
})

test('does not infer conflicts for async meetings or non-overlapping times', () => {
  const terms = [{ label: 'Spring 2027', sectionSelections: [
    section('CSC 116', '1001', ['Monday'], '10:00', '11:00'),
    section('MA 141', '2001', ['Monday'], '11:00', '12:00'),
    { ...section('ENG 101', '3001', [], null, null), meetingText: 'Online - Asynchronous' },
  ] }]
  assert.deepEqual(findTimeConflicts(terms), [])
})

test('creates a stable component and class-number key', () => {
  assert.equal(sectionKey({ component: 'Lec', classNumber: '1632', section: '002' }), 'Lec:1632')
})
