<script setup>
defineProps({ condition: { type: Object, required: true }, courseNames: { type: Object, required: true } })

const labels = { SATISFIED: 'Satisfied', BLOCKED: 'Blocked', MANUAL_REVIEW: 'Needs review' }
function readable(text) {
  return text.replaceAll('COMPLETED_BEFORE_OR_CONCURRENT', 'prior or same-term completion')
    .replaceAll('COMPLETED_BEFORE', 'prior completion').replaceAll('CONCURRENT', 'same-term enrollment')
    .replaceAll('NOT_SPECIFIED', 'no stated minimum')
    .replace(/([ABCD])_PLUS/g, '$1+').replace(/([ABCD])_MINUS/g, '$1−')
}
</script>

<template>
  <li class="condition-item">
    <div class="condition-heading">
      <strong>{{ condition.type === 'allOf' ? 'All conditions required' : condition.type === 'anyOf' ? 'One alternative required' : condition.type === 'course' ? (courseNames[condition.courseId] || condition.courseId) : 'Manual check' }}</strong>
      <span class="result-badge" :class="condition.status.toLowerCase()">{{ labels[condition.status] }}</span>
    </div>
    <p>{{ readable(condition.reason) }}</p>
    <ul v-if="condition.children.length" class="condition-list">
      <ConditionTree v-for="(child, index) in condition.children" :key="index" :condition="child" :course-names="courseNames" />
    </ul>
  </li>
</template>
