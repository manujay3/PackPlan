const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/

export function sectionKey(section) { return `${section.component}:${section.classNumber || section.section}` }

function minutes(value) {
  const match = TIME.exec(value || '')
  return match ? Number(match[1]) * 60 + Number(match[2]) : null
}
function dateRange(value) {
  const match = /^(\d{2})\/(\d{2})\/(\d{2})\s*-\s*(\d{2})\/(\d{2})\/(\d{2})$/.exec(value || '')
  if (!match) return null
  const [, sm, sd, sy, em, ed, ey] = match
  const start = new Date(2000 + Number(sy), Number(sm) - 1, Number(sd)).getTime()
  const end = new Date(2000 + Number(ey), Number(em) - 1, Number(ed)).getTime()
  return Number.isFinite(start) && Number.isFinite(end) && end >= start ? [start, end] : null
}

export function findTimeConflicts(terms) {
  const conflicts = []
  for (const term of terms) {
    const sections = term.sectionSelections || []
    for (let leftIndex = 0; leftIndex < sections.length; leftIndex++) {
      for (let rightIndex = leftIndex + 1; rightIndex < sections.length; rightIndex++) {
        const left = sections[leftIndex], right = sections[rightIndex]
        const leftStart = minutes(left.startTime), leftEnd = minutes(left.endTime)
        const rightStart = minutes(right.startTime), rightEnd = minutes(right.endTime)
        if (leftStart === null || leftEnd === null || rightStart === null || rightEnd === null) continue
        if (!left.meetingDays.some(day => right.meetingDays.includes(day))) continue
        const leftDates = dateRange(left.dates), rightDates = dateRange(right.dates)
        if (leftDates && rightDates && (leftDates[1] < rightDates[0] || rightDates[1] < leftDates[0])) continue
        if (leftStart < rightEnd && rightStart < leftEnd) conflicts.push({ term: term.label, left, right })
      }
    }
  }
  return conflicts
}
