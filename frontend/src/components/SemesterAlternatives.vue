<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import ConditionTree from './ConditionTree.vue'
import { grades, validSemesterResult } from '../eligibility.js'
import { ALTERNATIVES_KEY, readAlternatives, saveAlternative, deleteAlternative, clearAlternatives } from '../alternativesStorage.js'

const props = defineProps({ getEvidence: { type: Function, required: true }, courseNames: { type: Object, required: true } })
const emit = defineEmits(['load'])
const slots = ref([null, null])
const names = ref(['Alternative A', 'Alternative B'])
const storageError = ref('')
const message = ref('')
const results = ref([null, null])
const failures = ref(['', ''])
const comparing = ref(false)
const checkedAt = ref('')
const comparisonTitle = ref(null)
let comparisonRequest
const labels = { SATISFIED: 'Stored semester rules satisfied', BLOCKED: 'Semester has blockers', MANUAL_REVIEW: 'Semester needs review' }
const courseLabels = { SATISFIED: 'Stored rule satisfied', BLOCKED: 'Requirements not met', MANUAL_REVIEW: 'Manual review needed' }
const differences = computed(() => slots.value.map((slot, index) => slot?.evidence.proposedCourses.filter(id => !slots.value[1 - index]?.evidence.proposedCourses.includes(id)) || []))
function clearComparison() {
  comparisonRequest?.abort()
  comparisonRequest = null
  comparing.value = false
  results.value = [null, null]
  failures.value = ['', '']
  checkedAt.value = ''
}
function sync() {
  clearComparison()
  try {
    slots.value = readAlternatives()
    names.value = slots.value.map((slot, index) => slot?.name || `Alternative ${index === 0 ? 'A' : 'B'}`)
    storageError.value = ''
  } catch {
    slots.value = [null, null]
    storageError.value = 'Saved alternatives could not be read. Browser storage may be unavailable or the saved data may be incompatible. Current entries are unchanged.'
  }
}
function save(index) {
  message.value = ''
  if (!names.value[index].trim()) { message.value = 'Enter a name for the alternative.'; return }
  const evidence = props.getEvidence()
  if (!evidence) { message.value = 'Complete the current semester form before saving.'; return }
  try {
    const updated = saveAlternative(index, names.value[index], evidence)
    clearComparison()
    slots.value = updated
    storageError.value = ''
    message.value = `Saved ${updated[index].name} in this browser.`
  } catch {
    storageError.value = 'Could not save this alternative. Browser storage may be full, unavailable, or contain incompatible data. Current entries and earlier saves are unchanged.'
  }
}
function remove(index) {
  try {
    slots.value = deleteAlternative(index)
    clearComparison()
    storageError.value = ''
    message.value = `Deleted alternative ${index === 0 ? 'A' : 'B'} from this browser.`
  } catch { storageError.value = 'Could not delete the alternative. Browser storage is unavailable.' }
}
function clearSaved() {
  try { clearAlternatives(); sync(); message.value = 'Saved alternatives removed from this browser.' }
  catch { storageError.value = 'Could not clear saved alternatives. Browser storage is unavailable.' }
}
function load(index) {
  // Re-read so a newer save in another tab is not overwritten by an older in-memory copy.
  sync()
  const slot = slots.value[index]
  if (!slot) return
  emit('load', slot.evidence)
  message.value = `Loaded ${slot.name}. Check the semester again for current results.`
}
async function compare() {
  sync()
  if (!slots.value.every(Boolean)) return
  const snapshots = slots.value.map(slot => slot.evidence)
  const request = new AbortController()
  comparisonRequest = request
  comparing.value = true
  const timeout = setTimeout(() => request.abort(), 8000)
  try {
    const checks = await Promise.allSettled(snapshots.map(async evidence => {
      const response = await fetch('/api/semesters/eligibility', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store', signal: request.signal,
        body: JSON.stringify(evidence),
      })
      if (!response.ok) throw new Error('Check unavailable')
      const data = await response.json()
      if (!validSemesterResult(data, evidence)) throw new Error('Unexpected response')
      return data
    }))
    if (comparisonRequest !== request) return
    results.value = checks.map(check => check.status === 'fulfilled' ? check.value : null)
    failures.value = checks.map(check => check.status === 'rejected' ? 'Could not check this alternative. Retry the comparison; its saved evidence is unchanged.' : '')
    checkedAt.value = new Date().toLocaleTimeString()
    await nextTick()
    if (comparisonRequest === request) {
      comparisonTitle.value?.focus({ preventScroll: true })
      comparisonTitle.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
    }
  } finally {
    clearTimeout(timeout)
    if (comparisonRequest === request) { comparing.value = false; comparisonRequest = null }
  }
}
function storageChanged(event) {
  if (event.key === ALTERNATIVES_KEY || event.key === null) { sync(); message.value = 'Saved alternatives changed in another tab. Current entries are unchanged.' }
}
function gradeName(value) { return grades.find(([grade]) => grade === value)?.[1] || value }
onMounted(() => { sync(); window.addEventListener('storage', storageChanged) })
onUnmounted(() => { clearComparison(); window.removeEventListener('storage', storageChanged) })
</script>

<template>
  <section class="card alternatives-panel" aria-labelledby="alternatives-title">
    <p class="eyebrow">Two alternatives</p>
    <h2 id="alternatives-title" ref="comparisonTitle" tabindex="-1">Save and compare semesters</h2>
    <p class="scope-note">Saving stores your courses, cap, and completed attempts—including grades—in this browser. Load replaces the current form. Delete removes a save. Eligibility results are checked fresh and are never saved.</p>
    <p v-if="storageError" class="form-error" role="alert">{{ storageError }}</p>
    <button v-if="storageError" class="secondary-button" type="button" @click="clearSaved">Clear saved alternatives</button>
    <p role="status" aria-live="polite">{{ message }}</p>
    <div class="alternatives-grid">
      <article v-for="(slot, index) in slots" :key="index" class="alternative-slot" :aria-labelledby="`alternative-${index}-title`">
        <h3 :id="`alternative-${index}-title`">Alternative {{ index === 0 ? 'A' : 'B' }}{{ slot ? ` · ${slot.name}` : '' }}</h3>
        <label :for="`alternative-${index}-name`">Name for alternative {{ index === 0 ? 'A' : 'B' }}</label>
        <input :id="`alternative-${index}-name`" v-model="names[index]" class="credit-cap-input" maxlength="60" />
        <div class="alternative-actions">
          <button type="button" class="secondary-button" @click="save(index)">{{ slot ? 'Replace' : 'Save current as' }} {{ index === 0 ? 'A' : 'B' }}</button>
          <button v-if="slot" type="button" class="secondary-button" @click="load(index)">Load {{ index === 0 ? 'A' : 'B' }}</button>
          <button v-if="slot" type="button" class="text-button" @click="remove(index)">Delete {{ index === 0 ? 'A' : 'B' }}</button>
        </div>
        <template v-if="slot">
          <p class="field-help">Saved {{ new Date(slot.savedAt).toLocaleString() }} · Credit cap: {{ slot.evidence.creditCap }}</p>
          <h4>Proposed courses</h4>
          <ul><li v-for="id in slot.evidence.proposedCourses" :key="id">{{ courseNames[id] || `${id} · not in the current catalog` }}</li></ul>
          <h4>Prior attempts</h4>
          <p v-if="!slot.evidence.completedCourses.length" class="field-help">None recorded.</p>
          <ul v-else><li v-for="(attempt, n) in slot.evidence.completedCourses" :key="n">{{ courseNames[attempt.courseId] || attempt.courseId }} · {{ gradeName(attempt.grade) }} · {{ attempt.creditEarned ? 'Earned credit' : 'No earned credit' }}</li></ul>
          <p v-if="slots.every(Boolean)" class="field-help">Only in {{ index === 0 ? 'A' : 'B' }}: {{ differences[index].map(id => courseNames[id] || id).join(', ') || 'No unique courses' }}</p>
          <p v-if="comparing" role="status">Checking current rules…</p>
          <p v-if="failures[index]" class="form-error" role="alert">{{ failures[index] }}</p>
          <div v-if="results[index]" class="comparison-result">
            <h4 class="result-summary" :class="results[index].status.toLowerCase()">{{ labels[results[index].status] }}</h4>
            <p>{{ results[index].creditLoad.totalCredits === null ? `Known subtotal: ${results[index].creditLoad.knownCredits}` : results[index].creditLoad.totalCredits }} credits · Cap: {{ results[index].creditLoad.creditCap }}</p>
            <p class="field-help">{{ results[index].creditLoad.reason }}</p>
            <p v-if="results[index].creditLoad.unresolvedCourses.length" class="field-help">Full total unknown. Unresolved: {{ results[index].creditLoad.unresolvedCourses.map(id => courseNames[id] || id).join(', ') }}</p>
            <details><summary>Course checks for {{ index === 0 ? 'A' : 'B' }}</summary><div v-for="course in results[index].courses" :key="course.courseId"><h4>{{ courseNames[course.courseId] || course.courseId }} · {{ courseLabels[course.status] }}</h4><p v-if="!course.condition">{{ course.reason }}</p><ul v-else class="condition-list root-conditions"><ConditionTree :condition="course.condition" :course-names="courseNames" /></ul></div></details>
          </div>
        </template>
        <p v-else class="field-help">No alternative saved in this slot.</p>
      </article>
    </div>
    <button type="button" :disabled="comparing || !slots.every(Boolean)" @click="compare">{{ comparing ? 'Comparing current rules…' : 'Compare saved alternatives' }}</button>
    <p v-if="checkedAt" class="field-help" role="status">Comparison checked at {{ checkedAt }} against current stored rules. Recheck after catalog changes. No result is a registration or graduation approval.</p>
  </section>
</template>
