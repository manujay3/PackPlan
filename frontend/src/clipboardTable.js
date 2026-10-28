const courseCodePattern = /\b[A-Z]{1,5}\s*[- ]?\s*\d{3,4}[A-Z]?\b/i

export function copiedHtmlTable(html) {
  if (!html || typeof DOMParser === 'undefined') return ''
  const parsed = new DOMParser().parseFromString(html, 'text/html')
  const tables = [...parsed.querySelectorAll('table')].map(table => {
    const rows = [...table.querySelectorAll('tr')].map(row => [...row.children]
      .filter(cell => ['TH', 'TD'].includes(cell.tagName))
      .map(cell => cell.textContent.replace(/\s+/g, ' ').trim()))
      .filter(row => row.some(cell => cell))
    const courseRows = rows.slice(1).filter(row => courseCodePattern.test(row.join(' '))).length
    return { rows, score: courseRows * 10 + rows.length }
  }).filter(table => table.rows.length > 1)
  const selected = tables.sort((left, right) => right.score - left.score)[0]
  if (!selected) return ''
  return selected.rows.map(row => row.map(cell => cell.replace(/[\t\r\n]/g, ' ')).join('\t')).join('\n')
}
