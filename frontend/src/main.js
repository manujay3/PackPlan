import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import AboutView from './views/AboutView.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/about', component: AboutView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

createApp(App).use(router).mount('#app')
