<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import ConditionTree from '../components/ConditionTree.vue'
import { grades, noCreditGrades, validSemesterResult } from '../eligibility.js'

const courses = ref([])
const catalogState = ref('loading')
const terms = ref([{ id: 1, label: '', creditCap: 16, proposedCourses: [], carryPassed: false, assumedGrade: 'C' },
  { id: 2, label: '', creditCap: 16, proposedCourses: [], carryPassed: false, assumedGrade: 'C' }])
const completedCourses = ref([])
const results = ref([])
const checking = ref(false)
const error = ref('')
let nextTermId = 3
let nextAttemptId = 0
let request
const courseNames = computed(() => Object.fromEntries(courses.value.map(course => [course.id, `${course.code} · ${course.catalogYear}`])))
const eligibleChoices = term => courses.value.filter(course => !terms.value.some(other => other.id !== term.id && other.proposedCourses.includes(course.id)))
const statusLabels = { SATISFIED: 'Stored rules satisfied', BLOCKED: 'Plan has blockers', MANUAL_REVIEW: 'Manual review needed' }

function clearResults() {
  request?.abort()
  request = null
  checking.value = false
  results.value = []
  error.value = ''
}
watch([terms, completedCourses], clearResults, { deep: true, flush: 'sync' })
async function loadCatalog() {
  catalogState.value = 'loading'
  try {
    const response = await fetch('/api/catalog/courses', { cache: 'no-store' })
    if (!response.ok) throw new Error('unavailable')
    const data = await response.json()
    if (!Array.isArray(data) || !data.every(course => course && ['id', 'code', 'title', 'catalogYear'].every(key => typeof course[key] === 'string' && course[key]))) throw new Error('invalid')
    courses.value = data
    catalogState.value = data.length ? 'ready' : 'empty'
  } catch { catalogState.value = 'error' }
}
function addTerm() {
  if (terms.value.length >= 6) return
  terms.value.push({ id: nextTermId++, label: '', creditCap: 16, proposedCourses: [], carryPassed: false, assumedGrade: 'C' })
}
function removeTerm(index) {
  if (terms.value.length <= 2) return
  terms.value.splice(index, 1)
}
function addAttempt() { completedCourses.value.push({ key: nextAttemptId++, courseId: '', grade: '', creditEarned: '' }) }
function gradeChanged(attempt) { if (noCreditGrades.has(attempt.grade)) attempt.creditEarned = 'false' }
function attemptPayload() {
  return completedCourses.value.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned: creditEarned === 'true' }))
}
function validatePlan() {
  if (!terms.value.every(term => Number.isInteger(Number(term.creditCap)) && Number(term.creditCap) > 0 && Number(term.creditCap) <= 2147483647
    && term.proposedCourses.length > 0 && new Set(term.proposedCourses).size === term.proposedCourses.length)) return 'Add at least one course to every term and enter a positive whole-number credit cap.'
  if (completedCourses.value.some(attempt => !attempt.courseId || !attempt.grade || !['true', 'false'].includes(attempt.creditEarned))) return 'Complete or remove each prior course attempt.'
  if (completedCourses.value.some(attempt => noCreditGrades.has(attempt.grade) && attempt.creditEarned === 'true')) return 'Failed or non-credit outcomes cannot earn credit.'
  return ''
}
async function checkPlan() {
  clearResults()
  const invalid = validatePlan()
  if (invalid) { error.value = invalid; return }
  const controller = new AbortController()
  request = controller
  checking.value = true
  let timedOut = false
  const timeout = setTimeout(() => { timedOut = true; controller.abort() }, 30000)
  const output = []
  const priorEvidence = attemptPayload()
  const assumedFromEarlierTerms = []
  try {
    for (const term of terms.value) {
      const evidence = {
        completedCourses: [...priorEvidence, ...assumedFromEarlierTerms],
        proposedCourses: [...term.proposedCourses],
        creditCap: Number(term.creditCap),
      }
      const response = await fetch('/api/semesters/eligibility', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store', signal: controller.signal,
        body: JSON.stringify(evidence),
      })
      if (!response.ok) throw new Error('One or more term checks could not be completed. Your draft is still here; try again.')
      const result = await response.json()
      if (!validSemesterResult(result, evidence)) throw new Error('The server returned an unexpected result. Your draft is still here; try again.')
      output.push(result)
      if (term.carryPassed) assumedFromEarlierTerms.push(...term.proposedCourses.map(courseId => ({ courseId, grade: term.assumedGrade, creditEarned: true })))
    }
    if (request === controller) results.value = output
  } catch (failure) {
    if (request === controller) error.value = timedOut ? 'The plan check took too long. Your draft is still here; try again.'
      : failure.name !== 'AbortError' ? failure.message || 'Could not check the plan. Your draft is still here; try again.' : ''
  } finally {
    clearTimeout(timeout)
    if (request === controller) { checking.value = false; request = null }
  }
}
onMounted(loadCatalog)
onUnmounted(clearResults)
</script>

<template>
  <section class="hero eligibility-hero">
    <p class="eyebrow">Multi-term plan · Sample catalog</p>
    <h1>Put courses in sequence.</h1>
    <p class="intro">Arrange courses across two or more terms and check each term against the stored sample rules and the assumptions carried forward.</p>
    <p class="scope-note">Term order follows the order shown here. Labels, course offerings, and future passing outcomes are planning assumptions; none are verified. This sample cannot confirm graduation eligibility or registration.</p>
  </section>

  <section v-if="catalogState !== 'ready'" class="card" aria-live="polite">
    <h2>{{ catalogState === 'loading' ? 'Loading courses…' : catalogState === 'empty' ? 'No courses available yet' : 'Course list unavailable' }}</h2>
    <p v-if="catalogState === 'error'">We couldn’t load the course list. Check your connection and try again.</p>
    <button v-if="catalogState === 'error'" type="button" @click="loadCatalog">Reload courses</button>
  </section>

  <template v-else>
    <section class="card planner-inputs">
      <h2>Completed before the first term</h2>
      <p class="field-help">Record previous attempts so they can satisfy prerequisites. They are sent with each term check and are not saved after you leave.</p>
      <article v-for="(attempt, index) in completedCourses" :key="attempt.key" class="attempt-row">
        <div class="attempt-title"><strong>Attempt {{ index + 1 }}</strong><button type="button" class="text-button" :aria-label="`Remove prior attempt ${index + 1}`" @click="completedCourses.splice(index, 1)">Remove</button></div>
        <label :for="`planner-course-${attempt.key}`">Course</label>
        <select :id="`planner-course-${attempt.key}`" v-model="attempt.courseId"><option disabled value="">Choose a completed course</option><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.code }} · {{ course.title }} ({{ course.catalogYear }})</option></select>
        <div class="attempt-fields">
          <div><label :for="`planner-grade-${attempt.key}`">Grade or outcome</label><select :id="`planner-grade-${attempt.key}`" v-model="attempt.grade" @change="gradeChanged(attempt)"><option disabled value="">Choose an outcome</option><option v-for="[grade, label] in grades" :key="grade" :value="grade">{{ label }}</option></select></div>
          <div><label :for="`planner-credit-${attempt.key}`">Earned credit?</label><select :id="`planner-credit-${attempt.key}`" v-model="attempt.creditEarned"><option disabled value="">Choose yes or no</option><option value="true" :disabled="noCreditGrades.has(attempt.grade)">Yes</option><option value="false">No</option></select></div>
        </div>
      </article>
      <button type="button" class="secondary-button" @click="addAttempt">+ Add prior attempt</button>
    </section>

    <section class="planner-terms" aria-label="Terms in planned order">
      <article v-for="(term, index) in terms" :key="term.id" class="card planner-term">
        <div class="planner-term-heading"><div><p class="eyebrow">Term {{ index + 1 }}</p><h2>{{ term.label || `Term ${index + 1}` }}</h2></div><button v-if="terms.length > 2" type="button" class="text-button" :aria-label="`Remove term ${index + 1}`" @click="removeTerm(index)">Remove term</button></div>
        <label :for="`term-name-${term.id}`">Term label</label>
        <input :id="`term-name-${term.id}`" v-model="term.label" class="credit-cap-input" maxlength="60" placeholder="For example, Fall 2026" />
        <label :for="`term-cap-${term.id}`">Credit cap</label>
        <input :id="`term-cap-${term.id}`" v-model="term.creditCap" class="credit-cap-input" type="number" min="1" step="1" required />
        <fieldset>
          <legend>Courses in this term</legend>
          <p class="field-help">A course can appear in only one planned term. Availability is not checked.</p>
          <div class="concurrent-options">
            <label v-for="course in eligibleChoices(term)" :key="course.id" class="checkbox-label"><input v-model="term.proposedCourses" type="checkbox" :value="course.id" /><span><strong>{{ course.code }}</strong> <span class="field-help">{{ course.title }} · {{ course.credits }} credits · {{ course.catalogYear }}</span></span></label>
            <label v-for="id in term.proposedCourses.filter(id => !courses.some(course => course.id === id))" :key="id" class="checkbox-label"><input v-model="term.proposedCourses" type="checkbox" :value="id" /><span>{{ id }} · missing from current catalog</span></label>
          </div>
        </fieldset>
        <fieldset class="carry-assumption">
          <legend>Assumption for later terms</legend>
          <label class="checkbox-label"><input v-model="term.carryPassed" type="checkbox" /><span>Assume every course in this term is completed with credit</span></label>
          <div v-if="term.carryPassed" class="assumption-grade"><label :for="`assume-grade-${term.id}`">Assumed grade for each course</label><select :id="`assume-grade-${term.id}`" v-model="term.assumedGrade"><option v-for="[grade, label] in grades.filter(([value]) => !noCreditGrades.has(value))" :key="grade" :value="grade">{{ label }}</option></select><p class="field-help">This scenario input is carried into later prerequisite checks even if this term’s rule result needs review or has blockers.</p></div>
        </fieldset>
        <section v-if="results[index]" class="planner-result" :aria-labelledby="`term-result-${term.id}`">
          <h3 :id="`term-result-${term.id}`" class="result-summary" :class="results[index].status.toLowerCase()">{{ statusLabels[results[index].status] }}</h3>
          <p>{{ results[index].creditLoad.totalCredits === null ? `Known subtotal: ${results[index].creditLoad.knownCredits}` : results[index].creditLoad.totalCredits }} credits · Cap: {{ results[index].creditLoad.creditCap }}</p>
          <p class="field-help">{{ results[index].creditLoad.reason }}</p>
          <div v-for="course in results[index].courses" :key="course.courseId" class="semester-course-result"><h4>{{ courseNames[course.courseId] || course.courseId }} · {{ statusLabels[course.status] }}</h4><p v-if="!course.condition">{{ course.reason }}</p><ul v-else class="condition-list root-conditions"><ConditionTree :condition="course.condition" :course-names="courseNames" /></ul></div>
        </section>
      </article>
    </section>
    <div class="planner-actions"><button type="button" class="secondary-button" :disabled="terms.length >= 6" @click="addTerm">+ Add a term</button><button type="button" :disabled="checking" @click="checkPlan">{{ checking ? 'Checking terms in order…' : 'Check multi-term plan' }}</button></div>
    <p v-if="checking" role="status" aria-live="polite">Checks each term in order against current stored rules. Later terms use only your completed attempts and the explicit assumptions from earlier terms.</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="results.length" class="scope-note" role="status">Checked each term separately against current stored rules. Results are not stored. No result is a registration or graduation approval.</p>
  </template>
</template>
