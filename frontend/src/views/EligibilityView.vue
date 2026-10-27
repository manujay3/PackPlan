<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import ConditionTree from '../components/ConditionTree.vue'
import SemesterAlternatives from '../components/SemesterAlternatives.vue'
import { grades, noCreditGrades, validResult, validSemesterResult, validSemesterEvidence } from '../eligibility.js'

const props = defineProps({ semester: Boolean })
const courses = ref([])
const catalogState = ref('loading')
const targetId = ref('')
const completed = ref([])
const concurrent = ref([])
const creditCap = ref('')
const checking = ref(false)
const result = ref(null)
const error = ref('')
const resultsTitle = ref(null)
const evidenceForm = ref(null)
let nextRow = 0
let catalogRequest
let evaluationRequest
const statusLabels = { SATISFIED: 'Stored rule satisfied', BLOCKED: 'Requirements not met', MANUAL_REVIEW: 'Manual review needed' }
const semesterLabels = { SATISFIED: 'Stored semester rules satisfied', BLOCKED: 'Semester has blockers', MANUAL_REVIEW: 'Semester needs review' }
const reviewLabels = { REVIEWED: 'Reviewed rule', NEEDS_REVIEW: 'Rule needs review', UNREVIEWED: 'Unreviewed rule' }
const target = computed(() => courses.value.find(course => course.id === targetId.value))
const resultReason = computed(() => result.value?.reason?.replaceAll('NEEDS_REVIEW', 'needs review').replaceAll('UNREVIEWED', 'unreviewed'))
const courseNames = computed(() => Object.fromEntries(courses.value.map(course => [course.id, course.code])))
const savedCourseNames = computed(() => Object.fromEntries(courses.value.map(course => [course.id, `${course.code} · ${course.catalogYear}`])))
const sameTermChoices = computed(() => courses.value.filter(course => props.semester || course.id !== targetId.value))
const missingProposed = computed(() => concurrent.value.filter(id => !courseNames.value[id]))
const selectedCredits = computed(() => {
  const selected = courses.value.filter(course => concurrent.value.includes(course.id))
  return selected.length === concurrent.value.length && selected.every(course => Number.isSafeInteger(course.credits) && course.credits >= 0 && course.reviewStatus === 'REVIEWED')
    ? selected.reduce((sum, course) => sum + course.credits, 0) : null
})

function clearEvaluation() {
  evaluationRequest?.abort()
  evaluationRequest = null
  checking.value = false
  result.value = null
  error.value = ''
}
watch([targetId, completed, concurrent, creditCap], clearEvaluation, { deep: true, flush: 'sync' })
watch(targetId, id => { if (!props.semester) concurrent.value = concurrent.value.filter(courseId => courseId !== id) }, { flush: 'sync' })

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
function getSemesterEvidence() {
  if (!evidenceForm.value?.reportValidity()) return null
  const evidence = {
    completedCourses: completed.value.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned: creditEarned === 'true' })),
    proposedCourses: [...concurrent.value], creditCap: Number(creditCap.value),
  }
  if (!validSemesterEvidence(evidence)) {
    error.value = 'Choose at least one proposed course, complete every attempt, and enter a positive whole-number cap.'
    return null
  }
  return evidence
}
function loadAlternative(evidence) {
  clearEvaluation()
  completed.value = evidence.completedCourses.map(attempt => ({ ...attempt, creditEarned: String(attempt.creditEarned), key: nextRow++ }))
  concurrent.value = [...evidence.proposedCourses]
  creditCap.value = evidence.creditCap
}
async function evaluate() {
  if (checking.value || !targetId.value) return
  clearEvaluation()
  if (props.semester && !concurrent.value.length) {
    error.value = 'Select at least one proposed course.'
    return
  }
  const id = targetId.value
  const proposed = [...concurrent.value]
  const cap = Number(creditCap.value)
  const request = new AbortController()
  evaluationRequest = request
  checking.value = true
  let timedOut = false
  const timeout = setTimeout(() => { timedOut = true; request.abort() }, 8000)
  try {
    const response = await fetch(props.semester ? '/api/semesters/eligibility' : `/api/catalog/courses/${encodeURIComponent(id)}/eligibility`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: request.signal, cache: 'no-store',
      body: JSON.stringify({
        completedCourses: completed.value.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned: creditEarned === 'true' })),
        ...(props.semester ? { proposedCourses: proposed, creditCap: cap } : { concurrentCourses: proposed }),
      }),
    })
    if (!response.ok) throw new Error(response.status === 400 ? 'invalid' : 'unavailable')
    const data = await response.json()
    const valid = props.semester ? validSemesterResult(data, { proposedCourses: proposed, creditCap: cap }) : validResult(data, id)
    if (!valid) throw new Error('Unexpected result')
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
        : `Could not check this ${props.semester ? 'semester' : 'course'}. Your entries are still here. Try again.`
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
    <p class="eyebrow">{{ semester ? 'Semester check' : 'Course eligibility' }} · Sample catalog</p>
    <h1>{{ semester ? 'Check a semester together.' : 'Check your next course.' }}</h1>
    <p class="intro">{{ semester ? 'Choose courses for one target term. Check each stored rule against prior credit and the courses you would take together.' : 'See how your completed courses and same-term enrollment meet a course’s stored requirements.' }}</p>
    <p class="scope-note">This checks a small catalog sample. Results do not verify a full schedule, registration availability, or graduation eligibility.</p>
    <a v-if="semester" href="#alternatives-title">Save or compare alternatives ↓</a>
  </section>

  <section v-if="catalogState !== 'ready'" class="card" aria-live="polite">
    <h2>{{ catalogState === 'loading' ? 'Loading courses…' : catalogState === 'empty' ? 'No courses available yet' : 'Course list unavailable' }}</h2>
    <p v-if="catalogState === 'empty'">Try again after catalog data is available.</p>
    <p v-if="catalogState === 'error'">We couldn’t load the course list. Check your connection and try again.</p>
    <button v-if="catalogState !== 'loading'" type="button" @click="loadCatalog">Reload courses</button>
  </section>

  <div v-else class="eligibility-layout">
    <form ref="evidenceForm" class="card evidence-form" @submit.prevent="evaluate">
      <p class="eyebrow">Your evidence</p>
      <h2>{{ semester ? 'Build one proposed term' : 'Choose a course to check' }}</h2>
      <template v-if="!semester">
      <label for="target-course">Target course</label>
      <select id="target-course" v-model="targetId" required>
        <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.code }} · {{ course.title }} ({{ course.catalogYear }})</option>
      </select>
      <p v-if="target" class="field-help">{{ target.credits }} credit{{ target.credits === 1 ? '' : 's' }} · {{ target.catalogYear }} catalog <a v-if="catalogLink(target.sourceUrl)" :href="catalogLink(target.sourceUrl)" target="_blank" rel="noopener noreferrer">View catalog ↗</a></p>
      </template>
      <p v-else class="field-help">Add prior completed attempts, then select all courses you propose to take in the same term. Proposed courses are never treated as earned credit.</p>

      <fieldset>
        <legend>Completed before the target term</legend>
        <p class="field-help">Record each attempt and whether it earned credit. Add repeated attempts separately. Courses still in progress do not count as completed.</p>
        <p v-if="!completed.length" class="empty-evidence">No completed attempts added.</p>
        <div v-for="(attempt, index) in completed" :key="attempt.key" class="attempt-row">
          <div class="attempt-title"><strong>Attempt {{ index + 1 }}</strong><button type="button" class="text-button" :aria-label="`Remove attempt ${index + 1}`" @click="completed.splice(index, 1)">Remove</button></div>
          <label :for="`course-${attempt.key}`">Course</label>
          <select :id="`course-${attempt.key}`" v-model="attempt.courseId" required>
            <option disabled value="">Choose a completed course</option>
            <option v-if="attempt.courseId && !courseNames[attempt.courseId]" :value="attempt.courseId">{{ attempt.courseId }} · not in the current catalog</option>
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
        <legend>{{ semester ? 'Proposed courses in this term' : 'Enrollment in the same target term' }}</legend>
        <p class="field-help">{{ semester ? 'Every selected course is checked. A satisfied course result assumes the selected classmates are enrolled; a blocker in any course blocks the semester.' : 'Select courses you would take alongside the target course. This does not assume they have been passed or check their own requirements.' }}</p>
        <div class="concurrent-options">
          <label v-for="course in sameTermChoices" :key="course.id" class="checkbox-label"><input v-model="concurrent" type="checkbox" :value="course.id" /><span><strong>{{ course.code }}</strong> <span class="field-help">{{ course.title }} · {{ course.catalogYear }}</span></span></label>
          <label v-for="id in missingProposed" :key="id" class="checkbox-label"><input v-model="concurrent" type="checkbox" :value="id" /><span>{{ id }} <span class="field-help">Not in the current catalog; retained from a saved alternative. Uncheck to remove it.</span></span></label>
        </div>
        <template v-if="semester">
          <p class="field-help">{{ concurrent.length }} selected · {{ selectedCredits === null ? 'Credit total unavailable' : `${selectedCredits} credits (catalog preview)` }}. The server verifies the credit load when you check.</p>
          <label for="credit-cap">Your semester credit cap</label>
          <input id="credit-cap" v-model.number="creditCap" class="credit-cap-input" type="number" min="1" step="1" required placeholder="Enter a credit limit" aria-describedby="credit-cap-help" />
          <p id="credit-cap-help" class="field-help">Choose a positive whole number for your planning limit. Offerings and timetable conflicts are not checked.</p>
          <p v-if="selectedCredits !== null && creditCap !== '' && selectedCredits > creditCap" class="form-error">Your selection exceeds your credit cap. Check the semester to see the full result.</p>
        </template>
      </fieldset>
      <p class="field-help">{{ semester ? 'The current draft clears on leaving or reload. Use the saved alternatives section below to keep a copy in this browser.' : 'Entries stay on this page only. Reloading or leaving clears them; nothing is saved to your profile.' }}</p>
      <button type="submit" :disabled="checking">{{ checking ? 'Checking requirements…' : semester ? 'Check semester requirements' : 'Check course requirements' }}</button>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>

    <section class="card results-panel" aria-labelledby="results-title" :aria-busy="checking">
      <p class="eyebrow">The result</p>
      <h2 id="results-title" ref="resultsTitle" tabindex="-1">{{ semester ? 'Semester requirements' : `${target?.code} requirements` }}</h2>
      <div role="status" aria-live="polite" aria-atomic="true">
        <p v-if="checking">{{ semester ? 'Checking every selected course against your evidence…' : 'Checking the stored rule against your evidence…' }}</p>
        <div v-else-if="result" class="result-summary" :class="result.status.toLowerCase()">
          <h3>{{ semester ? semesterLabels[result.status] : statusLabels[result.status] }}</h3>
          <p>{{ semester ? 'All selected courses must meet their stored rules and your credit cap. Review the credit load and each course below.' : resultReason }}</p>
          <p v-if="!semester" class="field-help">{{ result.reviewStatus ? reviewLabels[result.reviewStatus] : 'No stored rule available' }}</p>
        </div>
        <p v-else class="result-placeholder">Add your evidence, then check {{ semester ? 'the semester' : 'the course' }}. Changing any entry clears the previous result.</p>
      </div>
      <ul v-if="result?.condition" class="condition-list root-conditions"><ConditionTree :condition="result.condition" :course-names="courseNames" /></ul>
      <section v-if="semester && result?.creditLoad" class="credit-load" aria-labelledby="credit-load-title">
        <h3 id="credit-load-title">Credit load <span class="result-badge" :class="result.creditLoad.status.toLowerCase()">{{ result.creditLoad.status === 'BLOCKED' ? 'Over cap' : result.creditLoad.status === 'MANUAL_REVIEW' ? 'Needs review' : 'Within cap' }}</span></h3>
        <p>{{ result.creditLoad.totalCredits === null ? `Known subtotal: ${result.creditLoad.knownCredits}` : result.creditLoad.totalCredits }} credits · Cap: {{ result.creditLoad.creditCap }}</p>
        <p class="field-help">{{ result.creditLoad.reason }}</p>
        <p v-if="result.creditLoad.unresolvedCourses.length" class="field-help">Unresolved credits: {{ result.creditLoad.unresolvedCourses.map(id => courseNames[id] || id).join(', ') }}. The full total is unknown.</p>
      </section>
      <article v-for="course in (result?.courses || [])" :key="course.courseId" class="semester-course-result">
        <h3>{{ courseNames[course.courseId] || course.courseId }} <span class="result-badge" :class="course.status.toLowerCase()">{{ statusLabels[course.status] }}</span></h3>
        <p class="field-help">{{ course.reviewStatus ? reviewLabels[course.reviewStatus] : 'No stored rule available' }}</p>
        <p v-if="!course.condition">{{ course.reason }}</p>
        <ul v-if="course.condition" class="condition-list root-conditions"><ConditionTree :condition="course.condition" :course-names="courseNames" /></ul>
      </article>
      <p v-if="result" class="scope-note">A satisfied rule applies only to this course’s stored conditions. Other course rules and manual checks may still be unresolved.</p>
    </section>
  </div>
  <SemesterAlternatives v-if="semester" :get-evidence="getSemesterEvidence" :course-names="savedCourseNames" @load="loadAlternative" />
</template>
