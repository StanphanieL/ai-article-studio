import { createRouter, createWebHistory } from 'vue-router'
import ArticleList from '../pages/ArticleList.vue'
import ArticleDetail from '../pages/ArticleDetail.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/article/list',
    },
    {
      path: '/article/list',
      component: ArticleList,
    },
    {
      path: '/article/detail/:taskId',
      component: ArticleDetail,
    },
  ],
})

export default router