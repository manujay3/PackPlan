<script setup>
import { computed, ref, watch } from 'vue'
import { copiedHtmlTable } from '../clipboardTable.js'
import { extractCourseHistoryPdf } from '../pdfCourseHistory.js'
import { extractDegreeAuditCourses } from '../degreeAuditImport.js'

const props = defineProps({ courses: { type: Array, required: true }, terms: { type: Array, required: true } })
const emit = defineEmits(['add'])
const text = ref('')
const rows = ref([])
const included = ref([])
const targetTermId = ref(props.terms[0]?.id ?? '')
const error = ref('')
const message = ref('')
const reading = ref(false)
const selectedCount = computed(() => rows.value.filter((row, index) => included.value[index] && !isPlanned(row.code)).length)
watch(() => props.terms, terms => {
  if (!terms.some(term => term.id === targetTermId.value)) targetTermId.value = terms[0]?.id ?? ''
}, { deep: true })
function courseId(code) { return props.courses.find(course => course.code.toUpperCase() === code)?.id || code }
function isPlanned(code) { return props.terms.some(term => term.proposedCourses.includes(courseId(code))) }
function preview() {
  error.value = ''; message.value = ''
  rows.value = extractDegreeAuditCourses(text.value)
  included.value = rows.value.map(() => false)
  if (!rows.value.length) error.value = 'No course codes were found. Paste degree-audit text or a copied table containing codes such as CSC 116.'
  else message.value = `Found ${rows.value.length} distinct course code${rows.value.length === 1 ? '' : 's'}. Review each row and select only courses you want to plan.`
}
function pasteTable(event) {
  const table = copiedHtmlTable(event.clipboardData?.getData('text/html'))
  if (!table) return
  event.preventDefault()
  text.value = table
  error.value = ''
  message.value = 'Copied table converted locally. Preview the course codes before adding them.'
}
async function selectFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 10_000_000) { error.value = 'Choose a file smaller than 10 MB.'; return }
  error.value = ''; message.value = ''; reading.value = true
  try {
    if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) {
      text.value = await extractCourseHistoryPdf(file)
      message.value = text.value ? 'PDF text extracted locally. Preview and review the course codes.' : 'No selectable text was found. Scanned PDFs need OCR first.'
    } else text.value = await file.text()
  } catch { error.value = 'Could not read this file. Try copying the audit table from MyPack or use a text-based PDF.' }
  finally { reading.value = false }
}
function addSelected() {
  const codes = rows.value.filter((row, index) => included.value[index] && !isPlanned(row.code)).map(row => row.code)
  if (!codes.length || !props.terms.some(term => term.id === targetTermId.value)) return
  emit('add', { termId: targetTermId.value, courseCodes: codes })
  message.value = `Added ${codes.length} course${codes.length === 1 ? '' : 's'} to ${props.terms.find(term => term.id === targetTermId.value)?.label || 'the selected term'}. Their requirements and credits may need review.`
  rows.value = []; included.value = []
}
</script>

<template>
  <details class="degree-audit-import">
    <summary>Add course options from a MyPack Degree Audit</summary>
    <p class="field-help">Copy the relevant requirement rows with course codes, paste them below, then preview. You can also upload text or a text-based PDF. PackPlan finds course-code-shaped text only; it does not interpret whether an audit requirement is complete. Select the options you want to plan. Text and files stay in this browser.</p>
    <label for="degree-audit-text">Paste copied audit text or table</label>
    <textarea id="degree-audit-text" v-model="text" rows="5" placeholder="Requirement options: CSC 116 or CSC 216" @paste="pasteTable" />
    <label for="degree-audit-file">Or choose a text, CSV/TSV, or PDF file</label>
    <input id="degree-audit-file" type="file" accept=".txt,.csv,.tsv,.pdf,text/plain,text/csv,text/tab-separated-values,application/pdf" @change="selectFile" />
    <button type="button" class="secondary-button" :disabled="reading || !text.trim()" @click="preview">{{ reading ? 'Reading file…' : 'Preview audit course codes' }}</button>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="message" role="status" aria-live="polite">{{ message }}</p>
    <div v-if="rows.length" class="course-import-table-wrap">
      <table class="course-import-table"><thead><tr><th scope="col">Plan</th><th scope="col">Course</th><th scope="col">Copied audit row</th></tr></thead>
        <tbody><tr v-for="(row, index) in rows" :key="row.code"><td><input v-model="included[index]" type="checkbox" :disabled="isPlanned(row.code)" :aria-label="`Plan ${row.code}`" /></td><td>{{ row.code }}<span v-if="isPlanned(row.code)" class="field-help"> · already in the plan</span><span v-else-if="!courses.some(course => course.id === courseId(row.code))" class="field-help"> · not in current catalog</span></td><td>{{ row.context }}</td></tr></tbody>
      </table>
    </div>
    <label v-if="rows.length" for="degree-audit-target">Add selected courses to</label>
    <select v-if="rows.length" id="degree-audit-target" v-model="targetTermId"><option v-for="term in terms" :key="term.id" :value="term.id">{{ term.label || 'Untitled term' }}</option></select>
    <button v-if="rows.length" type="button" :disabled="!selectedCount || !targetTermId" @click="addSelected">Add {{ selectedCount }} selected course{{ selectedCount === 1 ? '' : 's' }} to plan</button>
  </details>
</template>
