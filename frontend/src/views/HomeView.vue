<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const state = ref('loading')
const databaseState = ref('loading')
const checking = ref(false)
const detail = ref('Checking the Spring Boot API…')
let controller

async function checkConnection() {
  if (checking.value) return
  checking.value = true
  controller?.abort()
  controller = new AbortController()
  const request = controller
  state.value = 'loading'
  databaseState.value = 'loading'
  detail.value = 'Checking the Spring Boot API…'
  const timeout = setTimeout(() => request.abort(), 8000)
  try {
    const response = await fetch('/api/status', { signal: request.signal, cache: 'no-store' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    if (data.application !== 'PackPlan' || data.status !== 'UP') {
      throw new Error('Unexpected API response')
    }
    state.value = 'success'
    detail.value = 'The Vue frontend received a live response from the Spring Boot API.'
    try {
      const databaseResponse = await fetch('/api/database/status', { signal: request.signal, cache: 'no-store' })
      const database = await databaseResponse.json()
      databaseState.value = databaseResponse.ok && database.status === 'UP' ? 'success' : 'error'
    } catch {
      databaseState.value = 'error'
    }
  } catch {
    databaseState.value = 'unknown'
    state.value = 'error'
    detail.value = 'Could not reach the PackPlan API. Make sure the backend is running, then try again.'
  } finally {
    checking.value = false
    clearTimeout(timeout)
  }
}

onMounted(checkConnection)
onUnmounted(() => controller?.abort())
</script>

<template>
  <section class="hero">
    <p class="eyebrow">NC State degree planning · In development</p>
    <h1>A clearer path.<br /><span>One semester at a time.</span></h1>
    <p class="intro">PackPlan will help you connect the courses you’ve taken with the semesters ahead. We’re starting with a working foundation.</p>
  </section>
  <div class="grid">
    <section class="card" aria-labelledby="connection-title">
      <p class="eyebrow">Milestone 01</p>
      <h2 id="connection-title">Connect the application</h2>
      <div class="connection" :class="state" role="status" aria-live="polite">
        <strong>{{ state === 'success' ? 'Backend connected' : state === 'error' ? 'Connection unavailable' : 'Connecting…' }}</strong>
        <p>{{ detail }}</p>
      </div>
      <button :disabled="checking" @click="checkConnection">{{ checking ? 'Checking…' : 'Check connection again' }}</button>
      <div class="connection" :class="databaseState" role="status" aria-live="polite">
        <strong>{{ databaseState === 'success' ? 'Neo4j connected' : databaseState === 'loading' ? 'Checking database…' : databaseState === 'unknown' ? 'Database status unknown' : 'Database unavailable' }}</strong>
        <p>{{ databaseState === 'success' ? 'Spring Boot successfully queried Neo4j. Curriculum data has not been imported yet.' : databaseState === 'unknown' ? 'Restore the backend connection to check the database.' : databaseState === 'error' ? 'The backend is running, but its database check failed. Check the Neo4j container and connection settings, then retry.' : 'Waiting for the database connection check.' }}</p>
      </div>
      <p class="caption">A successful connection verifies the application foundation. Catalog data is not loaded yet.</p>
    </section>
    <section class="card muted" aria-labelledby="next-title">
      <p class="eyebrow">Up next</p>
      <h2 id="next-title">Give your plan a foundation</h2>
      <p>The next milestone will review official catalog requirements and store them in Neo4j.</p>
      <ul class="programs"><li><strong>Computer Science BS</strong><span>Planned demonstration program</span></li><li><strong>Economics BA</strong><span>Planned demonstration program</span></li></ul>
      <p class="caption">Target curriculum: 2026–2027. Neither program is supported yet. No graduation eligibility checks are available.</p>
      <RouterLink to="/about">See the implementation roadmap →</RouterLink>
    </section>
  </div>
</template>
