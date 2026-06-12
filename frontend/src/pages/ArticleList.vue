<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  createArticle,
  deleteArticle,
  listArticles,
  type ArticleVO,
} from '../api/article'

const router = useRouter()

const topic = ref('')
const style = ref('TECH')
const loading = ref(false)
const articles = ref<ArticleVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

const loadArticles = async () => {
  loading.value = true
  try {
    const res = await listArticles({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    articles.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error(error)
    message.error('加载文章列表失败')
  } finally {
    loading.value = false
  }
}

const handleCreate = async () => {
  if (!topic.value.trim()) {
    message.warning('请输入文章选题')
    return
  }

  loading.value = true
  try {
    const res = await createArticle({
      topic: topic.value,
      style: style.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    message.success('创建成功')
    topic.value = ''
    await loadArticles()
  } catch (error) {
    console.error(error)
    message.error('创建文章任务失败')
  } finally {
    loading.value = false
  }
}

const handleView = (taskId: string) => {
  router.push(`/article/detail/${taskId}`)
}

const handleDelete = async (taskId: string) => {
  loading.value = true
  try {
    const res = await deleteArticle(taskId)

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    message.success('删除成功')
    await loadArticles()
  } catch (error) {
    console.error(error)
    message.error('删除失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadArticles()
})
</script>

<template>
  <div class="page">
    <h1>AI Article Studio</h1>
    <p class="subtitle">文章任务列表</p>

    <a-card title="创建文章任务" class="card">
      <div class="form">
        <a-textarea
          v-model:value="topic"
          placeholder="请输入文章选题，例如：AI产品经理如何提升效率"
          :rows="3"
        />

        <a-select v-model:value="style" style="width: 180px">
          <a-select-option value="TECH">技术风格</a-select-option>
          <a-select-option value="BUSINESS">商业风格</a-select-option>
          <a-select-option value="LIFE">生活风格</a-select-option>
        </a-select>

        <a-button type="primary" :loading="loading" @click="handleCreate">
          创建任务
        </a-button>
      </div>
    </a-card>

    <a-card title="文章任务列表" class="card">
      <a-table
        :data-source="articles"
        :loading="loading"
        :pagination="false"
        row-key="taskId"
      >
        <a-table-column title="选题" data-index="topic" />
        <a-table-column title="风格" data-index="style" />
        <a-table-column title="阶段" data-index="phase" />
        <a-table-column title="状态" data-index="status" />
        <a-table-column title="创建时间" data-index="createTime" />

        <a-table-column title="操作">
          <template #default="{ record }">
            <a-space>
              <a-button type="link" @click="handleView(record.taskId)">
                查看详情
              </a-button>
              <a-button type="link" danger @click="handleDelete(record.taskId)">
                删除
              </a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>

      <div class="total">
        共 {{ total }} 条任务
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.page {
  padding: 32px;
  background: #f5f5f5;
  min-height: 100vh;
}

.subtitle {
  color: #666;
  margin-bottom: 24px;
}

.card {
  margin-bottom: 24px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.total {
  margin-top: 16px;
  color: #666;
}
</style>