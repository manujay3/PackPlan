<template>
  <section class="hero"><p class="eyebrow">The project</p><h1>Small milestones.<br /><span>Understandable decisions.</span></h1><p class="intro">A personal portfolio project for exploring degree requirements, prerequisites, and semester planning.</p></section>
  <section class="card"><h2>Implementation roadmap</h2>
    <ol class="roadmap">
      <li><strong>Application foundation — implemented</strong><p>Vue 3, Vue Router, a Java 21 Spring Boot API, and Docker Compose.</p></li>
      <li><strong>Reviewed curriculum data</strong><p>Computer Science BS and Economics BA, targeting 2026–2027. Import source URLs, requirements, and explicit unsupported rules into Neo4j.</p></li>
      <li><strong>Requirement and prerequisite checks</strong><p>Completed grades, current courses, AND/OR alternatives, minimum grades, corequisites, elective choices, and blocking explanations.</p></li>
      <li><strong>Semester planning</strong><p>Credit limits, offering assumptions, target graduation, course editing, two saved alternatives, and a readable prerequisite graph. Keep demo profiles in browser local storage.</p></li>
      <li><strong>Cache and deployment</strong><p>Add Redis once the main flow works, then document an AWS deployment.</p></li>
    </ol>
    <p>Planned courses and current enrollment will never be treated as earned credit. Future scenarios may explicitly assume a passing grade.</p>
    <a href="https://catalog.ncsu.edu/">Official NC State catalog ↗</a>
  </section>
</template>
