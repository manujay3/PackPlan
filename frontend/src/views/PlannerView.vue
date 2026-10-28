<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import ConditionTree from '../components/ConditionTree.vue'
import PlanAlternatives from '../components/PlanAlternatives.vue'
import CourseHistoryImport from '../components/CourseHistoryImport.vue'
import DegreeAuditImport from '../components/DegreeAuditImport.vue'
import { grades, noCreditGrades } from '../eligibility.js'
import { validPlan } from '../planStorage.js'
import { deleteDraft, readDraft, saveDraft } from '../planDraft.js'
import { checkPlanAgainstCurrentRules } from '../planChecker.js'
import { findTimeConflicts, sectionKey } from '../sectionSchedule.js'
import sampleCatalog from '../../../backend/src/main/resources/catalog/courses.json'

const courses = ref([])
const catalogState = ref('loading')
const terms = ref([{ id: 1, label: 'Spring 2027', creditCap: 16, proposedCourses: [], carryPassed: false, assumedGrade: 'C', sectionSelections: [] }])
const completedCourses = ref([])
const results = ref([])
const checking = ref(false)
const error = ref('')
const draftStatus = ref('')
const draftAvailable = ref(false)
const manualCourseCodes = ref({})
const sectionData = ref({})
const sectionLoading = ref({})
const sectionErrors = ref({})
let nextTermId = 3
let nextAttemptId = 0
let request
let catalogRequest
const sectionRequests = {}
const sampleCourses = sampleCatalog.courses.map(course => ({ ...course, catalogYear: sampleCatalog.catalogYear }))
const hasCatalog = computed(() => catalogState.value === 'ready' || catalogState.value === 'sample-fallback')
const courseNames = computed(() => Object.fromEntries(courses.value.map(course => [course.id, `${course.code} · ${course.catalogYear}`])))
const eligibleChoices = term => courses.value.filter(course => !terms.value.some(other => other.id !== term.id && other.proposedCourses.includes(course.id)))
const statusLabels = { SATISFIED: 'Stored rules satisfied', BLOCKED: 'Plan has blockers', MANUAL_REVIEW: 'Manual review needed' }
const timeConflicts = computed(() => findTimeConflicts(terms.value))
function courseCredits(courseId) {
  const catalogCourse = courses.value.find(course => course.id === courseId)
  if (catalogCourse && Number.isFinite(Number(catalogCourse.credits)) && Number(catalogCourse.credits) > 0) return Number(catalogCourse.credits)
  const listedCredits = sectionData.value[courseId]?.credits
  if (typeof listedCredits === 'string' && /^\d+(?:\.\d+)?$/.test(listedCredits.trim()) && Number(listedCredits) > 0) return Number(listedCredits)
  return null
}
function termCreditSummary(term) {
  const known = term.proposedCourses.map(courseId => ({ courseId, credits: courseCredits(courseId) }))
  return { total: known.reduce((sum, item) => sum + (item.credits || 0), 0),
    unknown: known.filter(item => item.credits === null).map(item => courses.value.find(course => course.id === item.courseId)?.code || item.courseId) }
}

function clearResults() {
  request?.abort()
  request = null
  checking.value = false
  results.value = []
  error.value = ''
}
watch([terms, completedCourses], () => {
  for (const term of terms.value) {
    const selections = term.sectionSelections || []
    const retained = selections.filter(section => term.proposedCourses.includes(section.courseId))
    if (retained.length !== selections.length) term.sectionSelections = retained
  }
  clearResults()
}, { deep: true, flush: 'sync' })
async function loadCatalog() {
  catalogRequest?.abort()
  const controller = new AbortController()
  catalogRequest = controller
  catalogState.value = 'loading'
  const timeout = setTimeout(() => controller.abort(), 8000)
  try {
    const response = await fetch('/api/catalog/courses', { cache: 'no-store', signal: controller.signal })
    if (!response.ok) throw new Error('unavailable')
    const data = await response.json()
    if (!Array.isArray(data) || !data.every(course => course && ['id', 'code', 'title', 'catalogYear'].every(key => typeof course[key] === 'string' && course[key]))) throw new Error('invalid')
    if (catalogRequest !== controller) return
    courses.value = data.length ? data : sampleCourses
    catalogState.value = data.length ? 'ready' : 'sample-fallback'
  } catch {
    if (catalogRequest === controller) {
      courses.value = sampleCourses
      catalogState.value = 'sample-fallback'
    }
  } finally {
    clearTimeout(timeout)
    if (catalogRequest === controller) catalogRequest = null
  }
}
function addTerm() {
  if (terms.value.length >= 6) return
  terms.value.push({ id: nextTermId++, label: '', creditCap: 16, proposedCourses: [], carryPassed: false, assumedGrade: 'C', sectionSelections: [] })
}
function removeTerm(index) {
  if (terms.value.length <= 1) return
  terms.value.splice(index, 1)
}
function addManualCourse(term) {
  const code = (manualCourseCodes.value[term.id] || '').trim().toUpperCase().replace(/\s+/g, ' ')
  if (!/^[A-Z]{1,5} \d{3,4}[A-Z]?$/.test(code)) { error.value = 'Enter a course code such as CSC 326.'; return }
  const match = courses.value.find(course => course.code.toUpperCase() === code)
  const id = match?.id || code
  if (terms.value.some(item => item.proposedCourses.includes(id))) { error.value = `${code} is already in the plan.`; return }
  term.proposedCourses.push(id)
  manualCourseCodes.value[term.id] = ''
}
function addAuditCourses({ termId, courseCodes }) {
  const term = terms.value.find(item => item.id === termId)
  if (!term || !Array.isArray(courseCodes)) return
  for (const rawCode of courseCodes) {
    const code = String(rawCode).trim().toUpperCase().replace(/\s+/g, ' ')
    if (!/^[A-Z]{1,5} \d{3,4}[A-Z]?$/.test(code)) continue
    const id = courses.value.find(course => course.code.toUpperCase() === code)?.id || code
    if (!terms.value.some(item => item.proposedCourses.includes(id))) term.proposedCourses.push(id)
  }
  error.value = ''
}
function sectionGroups(courseId) {
  const sections = sectionData.value[courseId]?.sections || []
  const groups = new Map()
  for (const section of sections) {
    const component = section.component || 'Section'
    if (!groups.has(component)) groups.set(component, [])
    groups.get(component).push(section)
  }
  return [...groups].map(([component, items]) => ({ component, sections: items }))
}
function selectedSectionKey(term, courseId, component) {
  const selected = term.sectionSelections.find(item => item.courseId === courseId && item.component === component)
  return selected ? sectionKey(selected) : ''
}
function chooseSection(term, courseId, component, key) {
  term.sectionSelections = term.sectionSelections.filter(item => item.courseId !== courseId || item.component !== component)
  if (!key) return
  const section = sectionGroups(courseId).find(group => group.component === component)?.sections.find(item => sectionKey(item) === key)
  if (section) term.sectionSelections.push({ ...section, courseId, courseCode: sectionData.value[courseId].courseCode,
    retrievedAt: sectionData.value[courseId].retrievedAt })
}
async function loadSections(courseId) {
  if (sectionData.value[courseId]) return
  sectionRequests[courseId]?.abort()
  const controller = new AbortController()
  sectionRequests[courseId] = controller
  sectionLoading.value[courseId] = true
  sectionErrors.value[courseId] = ''
  const code = courses.value.find(course => course.id === courseId)?.code || courseId
  const timeout = setTimeout(() => controller.abort(), 10000)
  try {
    const response = await fetch(`/api/schedule/sections?courseCode=${encodeURIComponent(code)}&term=2271`, { cache: 'no-store', signal: controller.signal })
    if (!response.ok) throw new Error('unavailable')
    const data = await response.json()
    if (data?.courseCode !== code.toUpperCase() || !Array.isArray(data.sections) || typeof data.sourceUrl !== 'string') throw new Error('invalid')
    if (sectionRequests[courseId] !== controller) return
    sectionData.value = { ...sectionData.value, [courseId]: data }
    const current = terms.value.find(term => term.proposedCourses.includes(courseId))
    if (current) {
      current.sectionSelections = current.sectionSelections.filter(selected => selected.courseId !== courseId || data.sections.some(section => sectionKey(section) === sectionKey(selected)))
    }
  } catch {
    if (sectionRequests[courseId] === controller) sectionErrors.value[courseId] = 'Could not load current Spring 2027 sections. Retry or open NC State Class Search.'
  } finally {
    clearTimeout(timeout)
    if (sectionRequests[courseId] === controller) {
      sectionLoading.value[courseId] = false
      delete sectionRequests[courseId]
    }
  }
}
function classSearchLink(courseId) {
  const code = (courses.value.find(course => course.id === courseId)?.code || courseId).trim().toUpperCase().replace(/\s+/g, ' ')
  const match = /^([A-Z]{1,4})\s*(\d{3}[A-Z]?)$/.exec(code)
  return match ? `https://webappprd.acs.ncsu.edu/php/coursecat/index.php?term=2271&subject=${encodeURIComponent(match[1])}&course-number=${encodeURIComponent(match[2])}` : 'https://webappprd.acs.ncsu.edu/php/coursecat/index.php?term=2271'
}
function addAttempt() { completedCourses.value.push({ key: nextAttemptId++, courseId: '', grade: '', creditEarned: '' }) }
function gradeChanged(attempt) { if (noCreditGrades.has(attempt.grade)) attempt.creditEarned = 'false' }
function attemptPayload() {
  return completedCourses.value.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned: creditEarned === 'true' }))
}
function getPlan() {
  if (completedCourses.value.some(attempt => !attempt.courseId || !attempt.grade || !['true', 'false'].includes(attempt.creditEarned))) {
    error.value = 'Complete or remove each prior attempt; imported earned-credit fields need your confirmation.'
    return null
  }
  if (completedCourses.value.some(attempt => noCreditGrades.has(attempt.grade) && attempt.creditEarned === 'true')) {
    error.value = 'Failed or non-credit outcomes cannot earn credit.'
    return null
  }
  const plan = {
    completedCourses: attemptPayload(),
    terms: terms.value.map(term => ({ label: term.label.trim(), creditCap: Number(term.creditCap),
      proposedCourses: [...term.proposedCourses], carryPassed: term.carryPassed, assumedGrade: term.assumedGrade,
      sectionSelections: term.sectionSelections.map(section => ({ ...section, meetingDays: [...section.meetingDays], notes: [...section.notes] })) })),
  }
  if (!validPlan(plan)) {
    error.value = 'Add at least one course to every term, complete each prior attempt, and enter a positive whole-number cap.'
    return null
  }
  return plan
}
function importAttempts(attempts) {
  completedCourses.value.push(...attempts.map(attempt => ({ ...attempt, creditEarned: attempt.creditEarned === null ? '' : String(attempt.creditEarned), key: nextAttemptId++ })))
  error.value = ''
}
function draftPayload() {
  return {
    completedCourses: completedCourses.value.map(({ courseId, grade, creditEarned }) => ({ courseId, grade, creditEarned })),
    terms: terms.value.map(term => ({ label: term.label, creditCap: term.creditCap, proposedCourses: [...term.proposedCourses],
      carryPassed: term.carryPassed, assumedGrade: term.assumedGrade,
      sectionSelections: term.sectionSelections.map(section => ({ ...section, meetingDays: [...section.meetingDays], notes: [...section.notes] })) })),
  }
}
function syncDraftAvailability() {
  try { draftAvailable.value = Boolean(readDraft()); draftStatus.value = '' }
  catch { draftAvailable.value = true; draftStatus.value = 'Saved progress could not be read. Delete the incompatible local copy before saving new progress.' }
}
function saveProgress() {
  try {
    const saved = saveDraft(draftPayload())
    draftAvailable.value = true
    draftStatus.value = `Progress saved in this browser at ${new Date(saved.savedAt).toLocaleTimeString()}.`
  } catch { draftStatus.value = 'Could not save progress. Browser storage may be unavailable or full; previous saved progress is unchanged.' }
}
function loadProgress() {
  try {
    const saved = readDraft()
    if (!saved) { draftAvailable.value = false; draftStatus.value = 'No saved progress is available.'; return }
    clearResults()
    completedCourses.value = saved.draft.completedCourses.map(attempt => ({ ...attempt, key: nextAttemptId++ }))
    terms.value = saved.draft.terms.map(term => ({ ...term, proposedCourses: [...term.proposedCourses], sectionSelections: term.sectionSelections.map(section => ({
      ...section, meetingDays: [...section.meetingDays], notes: [...section.notes],
    })), id: nextTermId++ }))
    manualCourseCodes.value = {}
    draftStatus.value = `Restored saved progress from ${new Date(saved.savedAt).toLocaleString()}. Review imported attempts and credits before checking the plan.`
  } catch { draftStatus.value = 'Could not load saved progress. Browser storage may be unavailable or the data may be incompatible.' }
}
function removeProgress() {
  try { deleteDraft(); draftAvailable.value = false; draftStatus.value = 'Saved progress deleted from this browser.' }
  catch { draftStatus.value = 'Could not delete saved progress. Browser storage is unavailable.' }
}
function loadPlan(plan) {
  clearResults()
  completedCourses.value = plan.completedCourses.map(attempt => ({ ...attempt, creditEarned: String(attempt.creditEarned), key: nextAttemptId++ }))
  terms.value = plan.terms.map(term => ({ ...term, proposedCourses: [...term.proposedCourses], sectionSelections: [...(term.sectionSelections || [])], id: nextTermId++ }))
}
async function checkPlan() {
  clearResults()
  const plan = getPlan()
  if (!plan) return
  const controller = new AbortController()
  request = controller
  checking.value = true
  let timedOut = false
  const timeout = setTimeout(() => { timedOut = true; controller.abort() }, 30000)
  try {
    const output = await checkPlanAgainstCurrentRules(plan, { signal: controller.signal })
    if (request === controller) results.value = output
  } catch (failure) {
    if (request === controller) error.value = timedOut ? 'The plan check took too long. Your draft is still here; try again.'
      : failure.name !== 'AbortError' ? failure.message || 'Could not check the plan. Your draft is still here; try again.' : ''
  } finally {
    clearTimeout(timeout)
    if (request === controller) { checking.value = false; request = null }
  }
}
onMounted(() => { loadCatalog(); syncDraftAvailability() })
onUnmounted(() => { catalogRequest?.abort(); Object.values(sectionRequests).forEach(controller => controller.abort()); clearResults() })
</script>

<template>
  <section class="hero eligibility-hero">
    <p class="eyebrow">Multi-term plan · Sample catalog</p>
    <h1>Put courses in sequence.</h1>
    <p class="intro">Arrange courses in Spring 2027 or add terms before or after it. Check each term against the stored sample rules and the assumptions carried forward.</p>
    <p class="scope-note">Term order follows the order shown here. Labels, course offerings, and future passing outcomes are planning assumptions; none are verified. This sample cannot confirm graduation eligibility or registration.</p>
  </section>

  <section v-if="!hasCatalog" class="card" aria-live="polite">
    <h2>{{ catalogState === 'loading' ? 'Loading courses…' : catalogState === 'empty' ? 'No courses available yet' : 'Course list unavailable' }}</h2>
    <p v-if="catalogState === 'error'">We couldn’t load the course list. Check your connection and try again.</p>
    <button v-if="catalogState === 'error'" type="button" @click="loadCatalog">Reload courses</button>
  </section>

  <template v-else>
    <p v-if="catalogState === 'sample-fallback'" class="form-error" role="status">The catalog API is unavailable or empty. You can draft and save with the bundled eight-course sample, but rule checks need imported catalog data and a running database. <button type="button" class="secondary-button" @click="loadCatalog">Retry catalog</button></p>
    <section class="card planner-inputs">
      <h2>Completed before the first term</h2>
      <p class="field-help">Record previous attempts so they can satisfy prerequisites. Save progress or a plan alternative to keep them in this browser; unresolved earned-credit entries can be kept in saved progress.</p>
      <CourseHistoryImport :courses="courses" @import="importAttempts" />
      <div class="draft-actions">
        <p class="field-help">Save incomplete progress on this device, including imported attempts that still need review. It stays in this browser and is separate from the two complete plan alternatives.</p>
        <button type="button" class="secondary-button" @click="saveProgress">Save progress</button>
        <button type="button" class="secondary-button" :disabled="!draftAvailable" @click="loadProgress">Load saved progress</button>
        <button v-if="draftAvailable" type="button" class="text-button" @click="removeProgress">Delete saved progress</button>
        <p v-if="draftStatus" :class="draftStatus.startsWith('Could not') || draftStatus.startsWith('Saved progress could not') ? 'form-error' : 'field-help'" role="status" aria-live="polite">{{ draftStatus }}</p>
      </div>
      <article v-for="(attempt, index) in completedCourses" :key="attempt.key" class="attempt-row">
        <div class="attempt-title"><strong>Attempt {{ index + 1 }}</strong><button type="button" class="text-button" :aria-label="`Remove prior attempt ${index + 1}`" @click="completedCourses.splice(index, 1)">Remove</button></div>
        <label :for="`planner-course-${attempt.key}`">Course</label>
        <select :id="`planner-course-${attempt.key}`" v-model="attempt.courseId"><option disabled value="">Choose a completed course</option><option v-if="attempt.courseId && !courses.some(course => course.id === attempt.courseId)" :value="attempt.courseId">{{ attempt.courseId }} · imported, not in the current catalog</option><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.code }} · {{ course.title }} ({{ course.catalogYear }})</option></select>
        <div class="attempt-fields">
          <div><label :for="`planner-grade-${attempt.key}`">Grade or outcome</label><select :id="`planner-grade-${attempt.key}`" v-model="attempt.grade" @change="gradeChanged(attempt)"><option disabled value="">Choose an outcome</option><option v-for="[grade, label] in grades" :key="grade" :value="grade">{{ label }}</option></select></div>
          <div><label :for="`planner-credit-${attempt.key}`">Earned credit?</label><select :id="`planner-credit-${attempt.key}`" v-model="attempt.creditEarned"><option disabled value="">Choose yes or no</option><option value="true" :disabled="noCreditGrades.has(attempt.grade)">Yes</option><option value="false">No</option></select></div>
        </div>
      </article>
      <button type="button" class="secondary-button" @click="addAttempt">+ Add prior attempt</button>
    </section>

    <section class="card planner-inputs">
      <h2>Courses to consider</h2>
      <DegreeAuditImport :courses="courses" :terms="terms" @add="addAuditCourses" />
    </section>

    <section class="planner-terms" :class="{ 'single-planner-term': terms.length === 1 }" aria-label="Terms in planned order">
      <article v-for="(term, index) in terms" :key="term.id" class="card planner-term">
        <div class="planner-term-heading"><div><p class="eyebrow">Term {{ index + 1 }}</p><h2>{{ term.label || `Term ${index + 1}` }}</h2></div><button v-if="terms.length > 1" type="button" class="text-button" :aria-label="`Remove term ${index + 1}`" @click="removeTerm(index)">Remove term</button></div>
        <label :for="`term-name-${term.id}`">Term label</label>
        <input :id="`term-name-${term.id}`" v-model="term.label" class="credit-cap-input" maxlength="60" placeholder="For example, Fall 2026" />
        <label :for="`term-cap-${term.id}`">Credit cap</label>
        <input :id="`term-cap-${term.id}`" v-model="term.creditCap" class="credit-cap-input" type="number" min="1" step="1" required />
        <fieldset>
          <legend>Courses in this term</legend>
          <p class="field-help">A course can appear in only one planned term. Section listings are live NC State data; seat availability may change before registration.</p>
          <div class="concurrent-options">
            <label v-for="course in eligibleChoices(term)" :key="course.id" class="checkbox-label"><input v-model="term.proposedCourses" type="checkbox" :value="course.id" /><span><strong>{{ course.code }}</strong> <span class="field-help">{{ course.title }} · {{ course.credits }} credits · {{ course.catalogYear }}</span></span></label>
            <label v-for="id in term.proposedCourses.filter(id => !courses.some(course => course.id === id))" :key="id" class="checkbox-label"><input v-model="term.proposedCourses" type="checkbox" :value="id" /><span>{{ id }} · not in current catalog<span v-if="courseCredits(id) !== null">; {{ courseCredits(id) }} listed credits</span><span v-else>; credit total needs review</span></span></label>
          </div>
          <p v-if="term.proposedCourses.length" class="field-help" role="status">Known catalog credits: {{ termCreditSummary(term).total }}<span v-if="termCreditSummary(term).unknown.length"> · Still unknown: {{ termCreditSummary(term).unknown.join(', ') }}</span><span>. Section checks can supply course credits from the current Class Search, but do not replace stored rule or degree-audit data.</span></p>
          <div class="manual-course-add"><label :for="`manual-course-${term.id}`">Add another course by code</label><div class="manual-course-controls"><input :id="`manual-course-${term.id}`" v-model="manualCourseCodes[term.id]" class="credit-cap-input" maxlength="12" placeholder="CSC 326" /><button type="button" class="secondary-button" @click="addManualCourse(term)">Add course</button></div><p class="field-help">Courses outside the sample can be saved in your draft. Find sections to check the Spring listing and displayed credits; eligibility rules still need review.</p></div>
          <section v-if="term.proposedCourses.length" class="section-planner" aria-label="Spring 2027 class sections">
            <h3>Class sections and meeting times</h3>
            <p class="field-help">Load published section data for each course, then choose one section per component you need. Exact meeting-time conflicts are flagged below.</p>
            <article v-for="id in term.proposedCourses" :key="id" class="section-course">
              <div class="section-course-heading"><strong>{{ courses.find(course => course.id === id)?.code || id }}</strong><button type="button" class="secondary-button" :disabled="sectionLoading[id]" @click="loadSections(id)">{{ sectionLoading[id] ? 'Loading sections…' : sectionData[id] ? 'Refresh sections' : 'Find sections' }}</button></div>
              <p v-if="sectionErrors[id]" class="form-error" role="alert">{{ sectionErrors[id] }} <a :href="classSearchLink(id)" target="_blank" rel="noopener noreferrer">Open NC State Class Search ↗</a></p>
              <div v-if="sectionData[id]" class="section-options">
                <p class="field-help">{{ sectionData[id].title || 'Course title unavailable' }} · {{ sectionData[id].sections.length }} section{{ sectionData[id].sections.length === 1 ? '' : 's' }} · refreshed {{ new Date(sectionData[id].retrievedAt).toLocaleTimeString() }} · <a :href="sectionData[id].sourceUrl" target="_blank" rel="noopener noreferrer">Official results ↗</a></p>
                <p v-if="!sectionData[id].sections.length" class="field-help">No Spring 2027 sections were returned for this course.</p>
                <div v-for="group in sectionGroups(id)" :key="group.component" class="section-choice">
                  <label :for="`section-${term.id}-${id}-${group.component}`">{{ group.component }} section</label>
                  <select :id="`section-${term.id}-${id}-${group.component}`" :value="selectedSectionKey(term, id, group.component)" @change="chooseSection(term, id, group.component, $event.target.value)"><option value="">Choose a section</option><option v-for="section in group.sections" :key="sectionKey(section)" :value="sectionKey(section)">{{ section.section }} · #{{ section.classNumber }} · {{ section.availability }} · {{ section.meetingText }} · {{ section.location || 'Location TBA' }}</option></select>
                  <template v-for="section in group.sections.filter(item => selectedSectionKey(term, id, group.component) === sectionKey(item))" :key="`${sectionKey(section)}-notes`"><details v-if="section.notes.length"><summary>Section notes and reserves</summary><p v-for="note in section.notes" :key="note">{{ note }}</p></details></template>
                </div>
              </div>
            </article>
          </section>
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
    <section v-if="timeConflicts.length" class="form-error schedule-conflicts" role="alert" aria-label="Schedule time conflicts"><h3>Meeting-time conflicts</h3><p v-for="(conflict, index) in timeConflicts" :key="index">{{ conflict.term || 'This term' }}: {{ conflict.left.courseCode }} {{ conflict.left.component }} {{ conflict.left.section }} conflicts with {{ conflict.right.courseCode }} {{ conflict.right.component }} {{ conflict.right.section }} on {{ conflict.left.meetingDays.filter(day => conflict.right.meetingDays.includes(day)).join(', ') }}.</p></section>
    <div class="planner-actions"><button type="button" class="secondary-button" :disabled="terms.length >= 6" @click="addTerm">+ Add a term</button><button type="button" :disabled="checking" @click="checkPlan">{{ checking ? 'Checking terms in order…' : 'Check multi-term plan' }}</button></div>
    <p v-if="checking" role="status" aria-live="polite">Checks each term in order against current stored rules. Later terms use only your completed attempts and the explicit assumptions from earlier terms.</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="results.length" class="scope-note" role="status">Checked each term separately against current stored rules. Results are not stored. No result is a registration or graduation approval.</p>
  </template>
  <PlanAlternatives v-if="hasCatalog" :get-plan="getPlan" :course-names="courseNames" @load="loadPlan" />
</template>
