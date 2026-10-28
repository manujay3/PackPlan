function linesFromItems(items) {
  const lines = []
  for (const item of items) {
    if (!item.str?.trim()) continue
    const y = item.transform[5]
    let line = lines.find(candidate => Math.abs(candidate.y - y) < 2)
    if (!line) {
      line = { y, items: [] }
      lines.push(line)
    }
    line.items.push({ x: item.transform[4], width: item.width, text: item.str.trim() })
  }
  return lines.sort((a, b) => b.y - a.y).map(line => {
    const ordered = line.items.sort((a, b) => a.x - b.x)
    let output = ''
    let previousEnd = null
    for (const item of ordered) {
      const gap = previousEnd === null ? 0 : item.x - previousEnd
      if (gap > 18) output += '\t'
      else if (gap > 2 && output && !output.endsWith(' ')) output += ' '
      output += item.text
      previousEnd = item.x + item.width
    }
    return output.trim()
  }).filter(Boolean)
}

export async function extractCourseHistoryPdf(file) {
  const pdfjs = await import('pdfjs-dist')
  pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()
  const document = await pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()), isEvalSupported: false }).promise
  try {
    if (document.numPages > 50) throw new Error('PDF has too many pages for local course-history import.')
    const pages = []
    for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber++) {
      const page = await document.getPage(pageNumber)
      const content = await page.getTextContent()
      pages.push(linesFromItems(content.items))
    }
    const lines = pages.flat()
    const widths = new Map()
    for (const line of lines) {
      const columns = line.split('\t').length
      if (columns > 1) widths.set(columns, (widths.get(columns) || 0) + 1)
    }
    const expectedColumns = [...widths].sort((left, right) => right[1] - left[1])[0]?.[0]
    return (expectedColumns ? lines.filter(line => line.split('\t').length === expectedColumns) : lines).join('\n')
  } finally {
    await document.destroy()
  }
}
