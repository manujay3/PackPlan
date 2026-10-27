<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import ConditionTree from '../components/ConditionTree.vue'

const courses = ref([])
const catalogState = ref('loading')
const targetId = ref('')
const completed = ref([])
const concurrent = ref([])
const checking = ref(false)
const result = ref(null)
const error = ref('')
const resultsTitle = ref(null)
let nextRow = 0
let catalogRequest
let evaluationRequest
const grades = [
  ['A_PLUS', 'A+'], ['A', 'A'], ['A_MINUS', 'A−'], ['B_PLUS', 'B+'], ['B', 'B'], ['B_MINUS', 'B−'],
  ['C_PLUS', 'C+'], ['C', 'C'], ['C_MINUS', 'C−'], ['D_PLUS', 'D+'], ['D', 'D'], ['D_MINUS', 'D−'], ['F', 'F'],
  ['SATISFACTORY', 'Satisfactory'], ['UNSATISFACTORY', 'Unsatisfactory'], ['CREDIT', 'Credit'],
  ['TRANSFER', 'Transfer'], ['WITHDRAWN', 'Withdrawn'], ['INCOMPLETE', 'Incomplete'], ['UNKNOWN', 'Unknown'],
]
const noCreditGrades = new Set(['F', 'UNSATISFACTORY', 'WITHDRAWN', 'INCOMPLETE'])
const statuses = ['SATISFIED', 'BLOCKED', 'MANUAL_REVIEW']
const statusLabels = { SATISFIED: 'Stored rule satisfied', BLOCKED: 'Requirements not met', MANUAL_REVIEW: 'Manual review needed' }
const reviewLabels = { REVIEWED: 'Reviewed rule', NEEDS_REVIEW: 'Rule needs review', UNREVIEWED: 'Unreviewed rule' }
const target = computed(() => courses.value.find(course => course.id === targetId.value))
const resultReason = computed(() => result.value?.reason.replaceAll('NEEDS_REVIEW', 'needs review').replaceAll('UNREVIEWED', 'unreviewed'))
const courseNames = computed(() => Object.fromEntries(courses.value.map(course => [course.id, course.code])))
const sameTermChoices = computed(() => courses.value.filter(course => course.id !== targetId.value))

function clearEvaluation() {
  evaluationRequest?.abort()
  evaluationRequest = null
  checking.value = false
  result.value = null
  error.value = ''
}
watch([targetId, completed, concurrent], clearEvaluation, { deep: true, flush: 'sync' })
watch(targetId, id => { concurrent.value = concurrent.value.filter(courseId => courseId !== id) }, { flush: 'sync' })

async function loadCatalog() {
  catalogRequest?.abort()
  const request = new AbortController()
  catalogRequest = request
  catalogState.value = 'loading'
  const timeout = setTimeout(() => request.abort(), 8000)
  try {
    const response = await fetch('/api/catalog/courses', { signal: request.signal, cache: 'no-store' })
    if (!response.ok) throw new Error('Course list unavailable')
    const data = await response.json()
    if (!Array.isArray(data) || !data.every(course => course && ['id', 'code', 'title', 'catalogYear'].every(key => typeof course[key] === 'string' && course[key]))) {
      throw new Error('Unexpected course list')
    }
    if (catalogRequest !== request) return
    courses.value = data
    targetId.value = data.find(course => course.id === 'csc-216-2026-2027')?.id || data[0]?.id || ''
    catalogState.value = data.length ? 'ready' : 'empty'
  } catch {
    if (catalogRequest === request) catalogState.value = 'error'
  } finally {
    clearTimeout(timeout)
  }
}

function addAttempt() {
  completed.value.push({ key: nextRow++, courseId: '', grade: '', creditEarned: '' })
}
function changeGrade(attempt) {
  if (noCreditGrades.has(attempt.grade)) attempt.creditEarned = 'false'
}
function validCondition(condition) {
  return condition && ['allOf', 'anyOf', 'course', 'unsupported'].includes(condition.type)
    && statuses.includes(condition.status) && typeof condition.reason === 'string'
    && Array.isArray(condition.children) && condition.children.every(validCondition)
    && (condition.type !== 'course' || typeof condition.courseId === 'string')
}
function validResult(data, id) {
  return data && data.courseId === id && statuses.includes(data.status) && typeof data.reason === 'string'
    && (data.reviewStatus === null || Object.hasOwn(reviewLabels, data.reviewStatus))
    && (data.condition === null ? data.status === 'MANUAL_REVIEW' && data.reviewStatus === null : validCondition(data.condition))
    && (data.status !== 'SATISFIED' || data.reviewStatus === 'REVIEWED')
}
async function evaluate() {
  if (checking.value || !targetId.value) return
  clearEvaluation()
  const id = targetId.value
  const request = new AbortController()
  evaluationRequest = request
  checking.value = true
  let timedOut = false
  const timeout = setTimeout(() => { timedOut = true; request.abort() }, 8000)
  try {
    const response = await fetch(`/api/catalog/courses/${encodeURIComponent(id)}/eligibility`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: request.signal, cache: 'no-store',
      body: JSON.stringify({
        completedCourses: completed.value.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned: creditEarned === 'true' })),
        concurrentCourses: concurrent.value,
      }),
    })
    if (!response.ok) throw new Error(response.status === 400 ? 'invalid' : 'unavailable')
    const data = await response.json()
    if (!validResult(data, id)) throw new Error('Unexpected result')
    if (evaluationRequest === request) {
      result.value = data
      await nextTick()
      if (evaluationRequest === request) {
        resultsTitle.value?.focus({ preventScroll: true })
        resultsTitle.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
      }
    }
  } catch (failure) {
    if (evaluationRequest === request) error.value = timedOut
      ? 'The check took too long. Your entries are still here. Try again.'
      : failure.message === 'invalid' ? 'Some evidence was not accepted. Check grades and earned credit, then try again.'
        : 'Could not check this course. Your entries are still here. Try again.'
  } finally {
    clearTimeout(timeout)
    if (evaluationRequest === request) { checking.value = false; evaluationRequest = null }
  }
}
function catalogLink(url) {
  try { return new URL(url).protocol === 'https:' ? url : null } catch { return null }
}
onMounted(loadCatalog)
onUnmounted(() => { catalogRequest?.abort(); clearEvaluation() })
</script>

<template>
  <section class="hero eligibility-hero">
    <p class="eyebrow">Course eligibility · Sample catalog</p>
    <h1>Check your next course.</h1>
    <p class="intro">See how your completed courses and same-term enrollment meet a course’s stored requirements.</p>
    <p class="scope-note">This checks a small catalog sample. Results do not verify a full schedule, registration availability, or graduation eligibility.</p>
  </section>

  <section v-if="catalogState !== 'ready'" class="card" aria-live="polite">
    <h2>{{ catalogState === 'loading' ? 'Loading courses…' : catalogState === 'empty' ? 'No courses available yet' : 'Course list unavailable' }}</h2>
    <p v-if="catalogState === 'empty'">Try again after catalog data is available.</p>
    <p v-if="catalogState === 'error'">We couldn’t load the course list. Check your connection and try again.</p>
    <button v-if="catalogState !== 'loading'" type="button" @click="loadCatalog">Reload courses</button>
  </section>

  <div v-else class="eligibility-layout">
    <form class="card evidence-form" @submit.prevent="evaluate">
      <p class="eyebrow">Your evidence</p>
      <h2>Choose a course to check</h2>
      <label for="target-course">Target course</label>
      <select id="target-course" v-model="targetId" required>
        <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.code }} · {{ course.title }} ({{ course.catalogYear }})</option>
      </select>
      <p v-if="target" class="field-help">{{ target.credits }} credit{{ target.credits === 1 ? '' : 's' }} · {{ target.catalogYear }} catalog <a v-if="catalogLink(target.sourceUrl)" :href="catalogLink(target.sourceUrl)" target="_blank" rel="noopener noreferrer">View catalog ↗</a></p>

      <fieldset>
        <legend>Completed before the target term</legend>
        <p class="field-help">Record each attempt and whether it earned credit. Add repeated attempts separately. Courses still in progress do not count as completed.</p>
        <p v-if="!completed.length" class="empty-evidence">No completed attempts added.</p>
        <div v-for="(attempt, index) in completed" :key="attempt.key" class="attempt-row">
          <div class="attempt-title"><strong>Attempt {{ index + 1 }}</strong><button type="button" class="text-button" :aria-label="`Remove attempt ${index + 1}`" @click="completed.splice(index, 1)">Remove</button></div>
          <label :for="`course-${attempt.key}`">Course</label>
          <select :id="`course-${attempt.key}`" v-model="attempt.courseId" required>
            <option disabled value="">Choose a completed course</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.code }} · {{ course.title }} ({{ course.catalogYear }})</option>
          </select>
          <div class="attempt-fields">
            <div><label :for="`grade-${attempt.key}`">Grade or outcome</label><select :id="`grade-${attempt.key}`" v-model="attempt.grade" required @change="changeGrade(attempt)"><option disabled value="">Choose an outcome</option><option v-for="[value, label] in grades" :key="value" :value="value">{{ label }}</option></select></div>
            <div><label :for="`credit-${attempt.key}`">Earned credit?</label><select :id="`credit-${attempt.key}`" v-model="attempt.creditEarned" required><option disabled value="">Choose yes or no</option><option value="true" :disabled="noCreditGrades.has(attempt.grade)">Yes</option><option value="false">No</option></select></div>
          </div>
        </div>
        <button type="button" class="secondary-button" @click="addAttempt">+ Add completed attempt</button>
      </fieldset>

      <fieldset>
        <legend>Enrollment in the same target term</legend>
        <p class="field-help">Select courses you would take alongside the target course. This does not assume they have been passed or check their own requirements.</p>
        <div class="concurrent-options">
          <label v-for="course in sameTermChoices" :key="course.id" class="checkbox-label"><input v-model="concurrent" type="checkbox" :value="course.id" /><span><strong>{{ course.code }}</strong> <span class="field-help">{{ course.title }} · {{ course.catalogYear }}</span></span></label>
        </div>
      </fieldset>
      <p class="field-help">Entries stay on this page only. Reloading or leaving clears them; nothing is saved to your profile.</p>
      <button type="submit" :disabled="checking">{{ checking ? 'Checking requirements…' : 'Check course requirements' }}</button>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>

    <section class="card results-panel" aria-labelledby="results-title" :aria-busy="checking">
      <p class="eyebrow">The result</p>
      <h2 id="results-title" ref="resultsTitle" tabindex="-1">{{ target?.code }} requirements</h2>
      <div role="status" aria-live="polite" aria-atomic="true">
        <p v-if="checking">Checking the stored rule against your evidence…</p>
        <div v-else-if="result" class="result-summary" :class="result.status.toLowerCase()">
          <h3>{{ statusLabels[result.status] }}</h3>
          <p>{{ resultReason }}</p>
          <p class="field-help">{{ result.reviewStatus ? reviewLabels[result.reviewStatus] : 'No stored rule available' }}</p>
        </div>
        <p v-else class="result-placeholder">Add your evidence, then check the course. Changing any entry clears the previous result.</p>
      </div>
      <ul v-if="result?.condition" class="condition-list root-conditions"><ConditionTree :condition="result.condition" :course-names="courseNames" /></ul>
      <p v-if="result" class="scope-note">A satisfied rule applies only to this course’s stored conditions. Other course rules and manual checks may still be unresolved.</p>
    </section>
  </div>
</template>
