<script setup>
import { computed, ref } from 'vue'
import { courseCodeFrom, findColumn, normalizeCreditEarned, normalizeGrade, parseDelimited } from '../courseHistoryImport.js'
import { extractCourseHistoryPdf } from '../pdfCourseHistory.js'
import { copiedHtmlTable } from '../clipboardTable.js'
import { grades, noCreditGrades } from '../eligibility.js'

const props = defineProps({ courses: { type: Array, required: true } })
const emit = defineEmits(['import'])
const headers = ref([])
const rows = ref([])
const included = ref([])
const panelOpen = ref(false)
const manualGrades = ref([])
const courseColumn = ref(-1)
const numberColumn = ref(-1)
const gradeColumn = ref(-1)
const creditColumn = ref(-1)
const error = ref('')
const message = ref('')
const pastedText = ref('')
const readingPdf = ref(false)
const candidates = computed(() => rows.value.map((row, index) => {
  const rawCourse = [row[courseColumn.value] || '', numberColumn.value < 0 ? '' : row[numberColumn.value] || ''].filter(Boolean).join(' ')
  const code = courseCodeFrom(rawCourse)
  const match = props.courses.find(course => course.code.toUpperCase() === code)
  const grade = normalizeGrade(row[gradeColumn.value]) || manualGrades.value[index] || ''
  const creditEarned = creditColumn.value < 0 ? null : normalizeCreditEarned(row[creditColumn.value])
  return { key: index, rawCourse, code, courseId: match?.id || code || rawCourse, grade, creditEarned,
    included: typeof included.value[index] === 'boolean' ? included.value[index] : Boolean(code) }
}))
const importableCount = computed(() => candidates.value.filter(row => row.included && row.courseId && row.grade
  && !(noCreditGrades.has(row.grade) && row.creditEarned === true)).length)
function pasteCourseHistory(event) {
  const table = copiedHtmlTable(event.clipboardData?.getData('text/html'))
  if (!table) return
  event.preventDefault()
  pastedText.value = table
  error.value = ''
  message.value = 'Copied course-history table converted locally. Review or edit the text, then preview the rows.'
}
function loadText(text) {
  error.value = ''
  message.value = ''
  try {
    const parsed = parseDelimited(text)
    panelOpen.value = true
    headers.value = parsed.headers
    rows.value = parsed.rows
    included.value = []
    manualGrades.value = []
    courseColumn.value = findColumn(parsed.headers, /course|class|subject/) >= 0 ? findColumn(parsed.headers, /course|class|subject/) : 0
    numberColumn.value = findColumn(parsed.headers, /catalog\s*(number|no|nbr|num)|course\s*(number|no|nbr|num)|\b(number|nbr)\b/)
    gradeColumn.value = findColumn(parsed.headers, /grade|mark|outcome/)
    creditColumn.value = findColumn(parsed.headers, /credit\s*earned|earned\s*credit/)
  } catch (failure) {
    headers.value = []; rows.value = []; error.value = failure.message || 'Could not read this file.'
  }
}
async function selectFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 10_000_000) { error.value = 'Choose a file smaller than 10 MB.'; return }
  if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) {
    error.value = ''; message.value = ''; readingPdf.value = true; panelOpen.value = true
    try {
      pastedText.value = await extractCourseHistoryPdf(file)
      message.value = pastedText.value ? 'PDF text extracted in this browser. Review or edit the text, then preview the rows.' : 'No selectable text was found. This PDF may be scanned; copy its table from MyPack or use OCR before importing.'
    } catch {
      error.value = 'Could not extract text from this PDF. Try copying the transcript table from MyPack or use a CSV/TSV file.'
    } finally { readingPdf.value = false }
    return
  }
  try { loadText(await file.text()) } catch { error.value = 'Could not read this file.' }
}
function applyImport() {
  const selected = candidates.value.filter(row => row.included && row.courseId && row.grade
    && !(noCreditGrades.has(row.grade) && row.creditEarned === true))
  if (!selected.length) { error.value = 'Select at least one row with a course and recognized grade.'; return }
  emit('import', selected.map(row => ({ courseId: row.courseId, grade: row.grade, creditEarned: row.creditEarned })))
  message.value = `Added ${selected.length} course attempt${selected.length === 1 ? '' : 's'}. Review each course and confirm earned credit before checking the plan.`
  headers.value = []; rows.value = []
  pastedText.value = ''
}
</script>

<template>
  <details class="course-import" :open="panelOpen" @toggle="panelOpen = $event.currentTarget.open">
    <summary>Import completed courses from MyPack or another academic record</summary>
    <p class="field-help">In MyPack, open Academic Records → View Unofficial Transcript, then copy the course-history table with its header row and paste it here. You can also choose a CSV/TSV file or a text-based transcript PDF. PDF text is extracted locally; review or edit it before previewing rows. Scanned PDFs need OCR first. Transcript units do not prove that credit was earned; confirm that separately for each attempt. Text and files stay in this browser and are not uploaded.</p>
    <label for="course-history-paste">Paste copied course-history table</label>
    <textarea id="course-history-paste" v-model="pastedText" rows="5" placeholder="Course&#9;Grade&#9;Credits&#10;CSC 116&#9;A&#9;3" @paste="pasteCourseHistory" />
    <button type="button" class="secondary-button" :disabled="!pastedText.trim() || readingPdf" @click="loadText(pastedText)">{{ readingPdf ? 'Extracting PDF text…' : 'Preview pasted rows' }}</button>
    <label for="course-history-file">Course history file</label>
    <input id="course-history-file" type="file" accept=".csv,.tsv,.txt,.pdf,text/csv,text/tab-separated-values,text/plain,application/pdf" @change="selectFile" />
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="message" role="status" aria-live="polite">{{ message }}</p>
    <div v-if="headers.length" class="course-import-mapping">
      <div><label for="import-course-column">Course column</label><select id="import-course-column" v-model.number="courseColumn"><option v-for="(header, index) in headers" :key="index" :value="index">{{ header }}</option></select></div>
      <div><label for="import-number-column">Course number column (optional)</label><select id="import-number-column" v-model.number="numberColumn"><option :value="-1">Same column as course</option><option v-for="(header, index) in headers" :key="index" :value="index">{{ header }}</option></select></div>
      <div><label for="import-grade-column">Grade column</label><select id="import-grade-column" v-model.number="gradeColumn"><option disabled :value="-1">Choose a column</option><option v-for="(header, index) in headers" :key="index" :value="index">{{ header }}</option></select></div>
      <div><label for="import-credit-column">Earned-credit column (optional)</label><select id="import-credit-column" v-model.number="creditColumn"><option :value="-1">Ask me for each row</option><option v-for="(header, index) in headers" :key="index" :value="index">{{ header }}</option></select></div>
      <div class="course-import-table-wrap">
        <table class="course-import-table"><thead><tr><th scope="col">Import</th><th scope="col">Course code</th><th scope="col">Grade</th><th scope="col">Earned credit</th></tr></thead>
          <tbody><tr v-for="candidate in candidates" :key="candidate.key"><td><input type="checkbox" :checked="candidate.included" :disabled="!candidate.code || !candidate.grade || (noCreditGrades.has(candidate.grade) && candidate.creditEarned === true)" :aria-label="`Import row ${candidate.key + 1}`" @change="included[candidate.key] = $event.target.checked" /></td><td>{{ candidate.code || 'Could not identify a course code' }}<span v-if="candidate.code && !courses.some(course => course.id === candidate.courseId)" class="field-help"> · not in current catalog</span></td><td><span v-if="candidate.grade">{{ candidate.grade }}</span><select v-else v-model="manualGrades[candidate.key]" :aria-label="`Grade for import row ${candidate.key + 1}`"><option disabled value="">Choose grade</option><option v-for="[grade, label] in grades" :key="grade" :value="grade">{{ label }}</option></select></td><td>{{ candidate.creditEarned === null ? 'Confirm after import' : candidate.creditEarned ? 'Yes' : 'No' }}</td></tr></tbody>
        </table>
      </div>
      <button type="button" :disabled="!importableCount" @click="applyImport">Add {{ importableCount }} reviewed {{ importableCount === 1 ? 'row' : 'rows' }} to prior attempts</button>
      <p class="field-help">Rows without a recognized code or grade are excluded. A blank or unrecognized earned-credit value remains blank and must be confirmed in the planner.</p>
    </div>
  </details>
</template>
