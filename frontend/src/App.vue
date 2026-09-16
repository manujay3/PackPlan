<template>
  <a class="skip-link" href="#main">Skip to content</a>
  <header class="header">
    <RouterLink class="brand" to="/"><span class="brand-mark" aria-hidden="true">P</span>PackPlan</RouterLink>
    <nav aria-label="Main navigation"><RouterLink to="/">Overview</RouterLink><RouterLink to="/about">About the project</RouterLink></nav>
  </header>
  <main id="main" tabindex="-1"><RouterView /></main>
  <footer>Built for the next semester. <span>Independent student project · Not an official NC State tool.</span></footer>
</template>
