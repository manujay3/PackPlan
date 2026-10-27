<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import ConditionTree from './ConditionTree.vue'
import { grades } from '../eligibility.js'
import { PLANS_KEY, readPlans, savePlan, deletePlan, clearPlans } from '../planStorage.js'
import { checkPlanAgainstCurrentRules } from '../planChecker.js'

const props = defineProps({ getPlan: { type: Function, required: true }, courseNames: { type: Object, required: true } })
const emit = defineEmits(['load'])
const slots = ref([null, null])
const names = ref(['Plan A', 'Plan B'])
const storageError = ref('')
const message = ref('')
const comparing = ref(false)
const results = ref([null, null])
const failures = ref(['', ''])
const checkedAt = ref('')
const title = ref(null)
let comparisonRequest
const statusLabels = { SATISFIED: 'Stored rules satisfied', BLOCKED: 'Plan has blockers', MANUAL_REVIEW: 'Manual review needed' }
function gradeName(value) { return grades.find(([grade]) => grade === value)?.[1] || value }
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
    slots.value = readPlans()
    names.value = slots.value.map((slot, index) => slot?.name || `Plan ${index === 0 ? 'A' : 'B'}`)
    storageError.value = ''
  } catch {
    slots.value = [null, null]
    storageError.value = 'Saved plans could not be read. Browser storage may be unavailable or the saved data may be incompatible.'
  }
}
function save(index) {
  message.value = ''
  if (!names.value[index].trim()) { message.value = 'Enter a name for the plan.'; return }
  const plan = props.getPlan()
  if (!plan) { message.value = 'Complete both terms and any prior attempts before saving.'; return }
  try {
    slots.value = savePlan(index, names.value[index], plan)
    clearComparison()
    storageError.value = ''
    message.value = `Saved ${slots.value[index].name} in this browser.`
  } catch { storageError.value = 'Could not save this plan. Browser storage may be full, unavailable, or contain incompatible data. Earlier saves are unchanged.' }
}
function remove(index) {
  try {
    slots.value = deletePlan(index)
    clearComparison()
    storageError.value = ''
    message.value = `Deleted plan ${index === 0 ? 'A' : 'B'} from this browser.`
  } catch { storageError.value = 'Could not delete the plan. Browser storage is unavailable.' }
}
function clearSaved() {
  try { clearPlans(); sync(); message.value = 'Saved plans removed from this browser.' }
  catch { storageError.value = 'Could not clear saved plans. Browser storage is unavailable.' }
}
function load(index) {
  sync()
  const slot = slots.value[index]
  if (!slot) return
  emit('load', slot.plan)
  message.value = `Loaded ${slot.name}. Check the plan again for current results.`
}
async function compare() {
  sync()
  if (!slots.value.every(Boolean)) return
  const snapshots = slots.value.map(slot => slot.plan)
  const controller = new AbortController()
  comparisonRequest = controller
  comparing.value = true
  let timedOut = false
  const timeout = setTimeout(() => { timedOut = true; controller.abort() }, 30000)
  try {
    const checks = await Promise.allSettled(snapshots.map(plan => checkPlanAgainstCurrentRules(plan, { signal: controller.signal })))
    if (comparisonRequest !== controller) return
    results.value = checks.map(check => check.status === 'fulfilled' ? check.value : null)
    failures.value = checks.map(check => check.status === 'rejected'
      ? timedOut ? 'The comparison took too long. Saved plans are unchanged; retry the comparison.' : 'Could not check this plan. Retry the comparison; its saved inputs are unchanged.' : '')
    checkedAt.value = new Date().toLocaleTimeString()
    await nextTick()
    if (comparisonRequest === controller) {
      title.value?.focus({ preventScroll: true })
      title.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
    }
  } finally {
    clearTimeout(timeout)
    if (comparisonRequest === controller) { comparing.value = false; comparisonRequest = null }
  }
}
function storageChanged(event) {
  if (event.key === PLANS_KEY || event.key === null) { sync(); message.value = 'Saved plans changed in another tab. Current entries are unchanged.' }
}
onMounted(() => { sync(); window.addEventListener('storage', storageChanged) })
onUnmounted(() => { clearComparison(); window.removeEventListener('storage', storageChanged) })
</script>

<template>
  <section class="card alternatives-panel" aria-labelledby="plans-title">
    <p class="eyebrow">Two saved plans</p>
    <h2 id="plans-title" ref="title" tabindex="-1">Save and compare schedules</h2>
    <p class="scope-note">Saves include term order, course choices, caps, prior grades, and assumed passing outcomes. They stay in this browser. Comparisons check each term against current rules; results are not saved.</p>
    <p v-if="storageError" class="form-error" role="alert">{{ storageError }}</p>
    <button v-if="storageError" class="secondary-button" type="button" @click="clearSaved">Clear saved plans</button>
    <p role="status" aria-live="polite">{{ message }}</p>
    <div class="alternatives-grid">
      <article v-for="(slot, index) in slots" :key="index" class="alternative-slot" :aria-labelledby="`plan-${index}-title`">
        <h3 :id="`plan-${index}-title`">Plan {{ index === 0 ? 'A' : 'B' }}{{ slot ? ` · ${slot.name}` : '' }}</h3>
        <label :for="`plan-name-${index}`">Name for plan {{ index === 0 ? 'A' : 'B' }}</label>
        <input :id="`plan-name-${index}`" v-model="names[index]" class="credit-cap-input" maxlength="60" />
        <div class="alternative-actions">
          <button type="button" class="secondary-button" @click="save(index)">{{ slot ? 'Replace' : 'Save current as' }} {{ index === 0 ? 'A' : 'B' }}</button>
          <button v-if="slot" type="button" class="secondary-button" @click="load(index)">Load {{ index === 0 ? 'A' : 'B' }}</button>
          <button v-if="slot" type="button" class="text-button" @click="remove(index)">Delete {{ index === 0 ? 'A' : 'B' }}</button>
        </div>
        <template v-if="slot">
          <p class="field-help">Saved {{ new Date(slot.savedAt).toLocaleString() }} · {{ slot.plan.terms.length }} terms</p>
          <div v-for="(term, termIndex) in slot.plan.terms" :key="termIndex" class="saved-plan-term">
            <h4>{{ term.label || `Term ${termIndex + 1}` }} · cap {{ term.creditCap }}</h4>
            <ul><li v-for="id in term.proposedCourses" :key="id">{{ courseNames[id] || id }}</li></ul>
            <p class="field-help">{{ term.carryPassed ? `Later terms assume ${gradeName(term.assumedGrade)} with earned credit.` : 'No pass assumption carried forward.' }}</p>
            <section v-if="results[index]?.[termIndex]" class="comparison-result">
              <h4 class="result-summary" :class="results[index][termIndex].status.toLowerCase()">{{ statusLabels[results[index][termIndex].status] }}</h4>
              <p>{{ results[index][termIndex].creditLoad.totalCredits === null ? `Known subtotal: ${results[index][termIndex].creditLoad.knownCredits}` : results[index][termIndex].creditLoad.totalCredits }} credits · Cap: {{ results[index][termIndex].creditLoad.creditCap }}</p>
              <details><summary>Course checks</summary><div v-for="course in results[index][termIndex].courses" :key="course.courseId"><h4>{{ courseNames[course.courseId] || course.courseId }} · {{ statusLabels[course.status] }}</h4><p v-if="!course.condition">{{ course.reason }}</p><ul v-else class="condition-list root-conditions"><ConditionTree :condition="course.condition" :course-names="courseNames" /></ul></div></details>
            </section>
          </div>
          <p v-if="failures[index]" class="form-error" role="alert">{{ failures[index] }}</p>
        </template>
        <p v-else class="field-help">No plan saved in this slot.</p>
      </article>
    </div>
    <button type="button" :disabled="comparing || !slots.every(Boolean)" @click="compare">{{ comparing ? 'Comparing plans…' : 'Compare saved plans' }}</button>
    <p v-if="checkedAt" class="field-help" role="status">Checked at {{ checkedAt }} against current stored rules. Recheck after catalog changes. No result is a registration or graduation approval.</p>
  </section>
</template>
