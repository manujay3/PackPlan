export function extractDegreeAuditCourses(text) {
  if (typeof text !== 'string' || !text.trim()) return []
  const candidates = new Map()
  for (const rawLine of text.split(/\r?\n/)) {
    const context = rawLine.replace(/\s+/g, ' ').trim()
    if (!context) continue
    const pattern = /\b([A-Z]{1,5})\s*[- ]?\s*(\d{3,4}[A-Z]?)\b/gi
    for (const match of context.matchAll(pattern)) {
      const code = `${match[1].toUpperCase()} ${match[2].toUpperCase()}`
      if (!candidates.has(code)) candidates.set(code, context)
    }
  }
  return [...candidates].map(([code, context]) => ({ code, context }))
}
