<template>
  <section class="hero"><p class="eyebrow">The project</p><h1>Small milestones.<br /><span>Understandable decisions.</span></h1><p class="intro">A personal portfolio project for exploring degree requirements, prerequisites, and semester planning.</p></section>
  <section class="card"><h2>Implementation roadmap</h2>
    <ol class="roadmap">
      <li><strong>Application foundation — implemented</strong><p>Vue 3, Vue Router, a Java 21 Spring Boot API, and Docker Compose.</p></li>
      <li><strong>Reviewed curriculum data</strong><p>Computer Science BS and Economics BA, targeting 2026–2027. Import source URLs, requirements, and explicit unsupported rules into Neo4j.</p></li>
      <li><strong>Enrollment rule checks — sample implemented</strong><p>Check one course or every course in a proposed semester against prior credit and same-term enrollment. Results explain AND/OR alternatives, minimum grades, corequisites, and manual-review conditions. Degree requirements and elective checks are still pending.</p></li>
      <li><strong>Semester planning — early tools implemented</strong><p>Check one term or sequence up to six terms, with per-term caps, prerequisite checks, and explicit assumed-pass outcomes carried into later terms. Two alternatives can be saved with user supplied offering assumptions. Availability and graduation progress are not verified. Course editing and a prerequisite graph are still planned.</p></li>
      <li><strong>Cache and deployment</strong><p>Add Redis once the main flow works, then document an AWS deployment.</p></li>
    </ol>
    <p>Planned courses and current enrollment will never be treated as earned credit. Future scenarios may explicitly assume a passing grade.</p>
    <a href="https://catalog.ncsu.edu/">Official NC State catalog ↗</a>
  </section>
</template>
