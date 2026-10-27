import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import AboutView from './views/AboutView.vue'
import EligibilityView from './views/EligibilityView.vue'
import SemesterView from './views/SemesterView.vue'
import PlannerView from './views/PlannerView.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/about', component: AboutView },
    { path: '/eligibility', component: EligibilityView },
    { path: '/semester', component: SemesterView },
    { path: '/planner', component: PlannerView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

createApp(App).use(router).mount('#app')
