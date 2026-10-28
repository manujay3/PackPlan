export function parseDelimited(text) {
  if (typeof text !== 'string' || !text.trim()) throw new Error('The file is empty.')
  const firstLine = text.split(/\r?\n/, 1)[0]
  const delimiter = firstLine.includes('\t') && firstLine.split('\t').length > firstLine.split(',').length ? '\t' : ','
  const rows = []
  let row = []
  let cell = ''
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const char = text[i]
    if (quoted && char === '"' && text[i + 1] === '"') { cell += '"'; i++ }
    else if (char === '"') quoted = !quoted
    else if (!quoted && char === delimiter) { row.push(cell.trim()); cell = '' }
    else if (!quoted && (char === '\n' || char === '\r')) {
      if (char === '\r' && text[i + 1] === '\n') i++
      row.push(cell.trim()); cell = ''
      if (row.some(value => value)) rows.push(row)
      row = []
    } else cell += char
  }
  if (quoted) throw new Error('The file has an unclosed quoted field.')
  row.push(cell.trim())
  if (row.some(value => value)) rows.push(row)
  if (rows.length < 2) throw new Error('Add a header row and at least one course row.')
  const width = rows[0].length
  if (width < 2 || rows.some(item => item.length !== width)) throw new Error('Every row must have the same number of columns as the header.')
  return { headers: rows[0], rows: rows.slice(1), delimiter }
}

export function findColumn(headers, pattern) {
  return headers.findIndex(header => pattern.test(header.trim().toLowerCase()))
}

export function courseCodeFrom(value) {
  const match = String(value || '').toUpperCase().match(/\b([A-Z]{1,4})\s*[- ]?\s*(\d{3,4}[A-Z]?)\b/)
  return match ? `${match[1]} ${match[2]}` : ''
}

export function normalizeGrade(value) {
  const raw = String(value || '').trim().toUpperCase().replace(/[−–]/g, '-').replace(/\s+/g, '')
  const aliases = { 'A+': 'A_PLUS', 'A-': 'A_MINUS', 'B+': 'B_PLUS', 'B-': 'B_MINUS', 'C+': 'C_PLUS', 'C-': 'C_MINUS', 'D+': 'D_PLUS', 'D-': 'D_MINUS',
    A_PLUS: 'A_PLUS', A_MINUS: 'A_MINUS', B_PLUS: 'B_PLUS', B_MINUS: 'B_MINUS', C_PLUS: 'C_PLUS', C_MINUS: 'C_MINUS', D_PLUS: 'D_PLUS', D_MINUS: 'D_MINUS',
    S: 'SATISFACTORY', U: 'UNSATISFACTORY', W: 'WITHDRAWN', I: 'INCOMPLETE', IN: 'INCOMPLETE', LA: 'INCOMPLETE', CR: 'CREDIT', TR: 'TRANSFER' }
  return aliases[raw] || (['A', 'B', 'C', 'D', 'F', 'SATISFACTORY', 'UNSATISFACTORY', 'CREDIT', 'TRANSFER', 'WITHDRAWN', 'INCOMPLETE', 'UNKNOWN'].includes(raw) ? raw : '')
}

export function normalizeCreditEarned(value) {
  const raw = String(value || '').trim().toLowerCase()
  if (['yes', 'y', 'true', 'earned', 'credit earned'].includes(raw)) return true
  if (['no', 'n', 'false', 'not earned', 'no credit'].includes(raw)) return false
  return null
}
