<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  confirmTitle,
  generateContent,
  generateTitles,
  getArticle,
  saveOutline,
  listAgentLogs,
  saveTitle,
  saveContent,
  streamGenerateContentUrl,
  realStreamGenerateContentUrl,
  generateImagePrompts,
  continueWorkflow,
  generateImageResults,
  composeArticle,
  type ImageProvider,
  type ImageResultOption,
  type ImagePromptOption,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
  type AgentLogVO,
} from '../api/article'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const titleLoading = ref(false)
const outlineLoading = ref(false)
const contentLoading = ref(false)

const article = ref<ArticleVO | null>(null)
const selectedTitle = ref('')

const realStreaming = ref(false)
const streaming = ref(false)
const streamLogs = ref<string[]>([])
const streamContent = ref('')

const agentLogs = ref<AgentLogVO[]>([])
const logLoading = ref(false)
const logStatusFilter = ref<'ALL' | 'SUCCESS' | 'FAILED'>('ALL')

const outlineEditing = ref(false)
const outlineSaving = ref(false)
const editableOutline = ref<OutlineItem[]>([])

const titleEditing = ref(false)
const titleSaving = ref(false)
const editableTitle = ref('')

const contentEditing = ref(false)
const contentSaving = ref(false)
const editableContent = ref('')
const contentEditMode = ref<'edit' | 'preview'>('edit')

const imagePromptLoading = ref(false)

const workflowLoading = ref(false)

const imageResultLoading = ref(false)
const imageProvider = ref<ImageProvider>('AUTO')

const composeLoading = ref(false)

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})


const handleStartEditOutline = () => {
  editableOutline.value = outlineItems.value.map((item) => ({
    heading: item.heading,
    description: item.description,
  }))

  outlineEditing.value = true
}

const handleStartEditTitle = () => {
  editableTitle.value = article.value?.selectedTitle || ''
  titleEditing.value = true
}

const handleCancelEditTitle = () => {
  editableTitle.value = ''
  titleEditing.value = false
}

const handleSaveTitle = async () => {
  if (!taskId.value) {
    return
  }

  if (!editableTitle.value.trim()) {
    message.warning('标题不能为空')
    return
  }

  titleSaving.value = true
  try {
    const res = await saveTitle({
      taskId: taskId.value,
      selectedTitle: editableTitle.value.trim(),
    })

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    selectedTitle.value = res.data.selectedTitle || ''
    titleEditing.value = false
    editableTitle.value = ''

    message.success('标题保存成功')
  } catch (error) {
    console.error(error)
    message.error('保存标题失败')
  } finally {
    titleSaving.value = false
  }
}

const handleCancelEditOutline = () => {
  editableOutline.value = []
  outlineEditing.value = false
}

const handleAddOutlineItem = () => {
  editableOutline.value.push({
    heading: '',
    description: '',
  })
}

const handleRemoveOutlineItem = (index: number) => {
  editableOutline.value.splice(index, 1)
}

const handleSaveOutline = async () => {
  if (!taskId.value) {
    return
  }

  if (editableOutline.value.length === 0) {
    message.warning('大纲不能为空')
    return
  }

  for (let i = 0; i < editableOutline.value.length; i++) {
    const item = editableOutline.value[i]

    if (!item.heading || !item.heading.trim()) {
      message.warning(`第 ${i + 1} 个章节标题不能为空`)
      return
    }

    if (!item.description || !item.description.trim()) {
      message.warning(`第 ${i + 1} 个章节说明不能为空`)
      return
    }
  }

  outlineSaving.value = true
  try {
    const res = await saveOutline({
      taskId: taskId.value,
      outline: editableOutline.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    outlineEditing.value = false
    editableOutline.value = []

    message.success('大纲保存成功')
  } catch (error) {
    console.error(error)
    message.error('保存大纲失败')
  } finally {
    outlineSaving.value = false
  }
}

const handleStartEditContent = () => {
  editableContent.value = article.value?.content || streamContent.value || ''
  contentEditMode.value = 'edit'
  contentEditing.value = true
}

const handleCancelEditContent = () => {
  editableContent.value = ''
  contentEditMode.value = 'edit'
  contentEditing.value = false
}

const handleSaveContent = async () => {
  if (!taskId.value) {
    return
  }

  if (!editableContent.value.trim()) {
    message.warning('正文不能为空')
    return
  }

  contentSaving.value = true
  try {
    const res = await saveContent({
      taskId: taskId.value,
      content: editableContent.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    contentEditing.value = false
    contentEditMode.value = 'edit'
    editableContent.value = ''

    message.success('正文保存成功')
  } catch (error) {
    console.error(error)
    message.error('保存正文失败')
  } finally {
    contentSaving.value = false
  }
}

const handleRealStreamGenerateContent = async () => {
  if (!taskId.value) {
    return
  }

  if (!article.value?.outline) {
    message.warning('请先生成大纲')
    return
  }

  if (realStreaming.value) {
    message.warning('真实流式生成中，请稍等')
    return
  }

  realStreaming.value = true
  streamLogs.value = []
  streamContent.value = ''

  let closedByDone = false

  const eventSource = new EventSource(realStreamGenerateContentUrl(taskId.value))

  eventSource.addEventListener('progress', (event) => {
    const data = parseSseData(event as MessageEvent)
    if (data.message) {
      streamLogs.value.push(data.message)
    }
  })

  eventSource.addEventListener('content', (event) => {
    const data = parseSseData(event as MessageEvent)

    if (data.message && streamLogs.value[streamLogs.value.length - 1] !== data.message) {
      streamLogs.value.push(data.message)
    }

    if (data.content) {
      streamContent.value = data.content
    }
  })

  eventSource.addEventListener('done', async (event) => {
    const data = parseSseData(event as MessageEvent)

    if (data.message) {
      streamLogs.value.push(data.message)
    }

    if (data.content) {
      streamContent.value = data.content
    }

    closedByDone = true
    eventSource.close()
    realStreaming.value = false

    message.success('真实流式正文生成成功')

    await loadArticle()
    await loadAgentLogs()
  })

  eventSource.addEventListener('fail', async (event) => {
    const data = parseSseData(event as MessageEvent)

    if (data.message) {
      streamLogs.value.push(data.message)
      message.error(data.message)
    } else {
      message.error('真实流式生成失败')
    }

    closedByDone = true
    eventSource.close()
    realStreaming.value = false
    await loadArticle()
    await loadAgentLogs()
  })

  eventSource.onerror = async () => {
    if (!closedByDone) {
      message.error('真实流式 SSE 连接异常')
      streamLogs.value.push('真实流式 SSE 连接异常')
      await loadArticle()
      await loadAgentLogs()
    }

    eventSource.close()
    realStreaming.value = false
  }
}

const loadAgentLogs = async () => {
  if (!taskId.value) {
    return
  }

  logLoading.value = true
  try {
    const res = await listAgentLogs(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    agentLogs.value = res.data || []
  } catch (error) {
    console.error(error)
    message.error('加载 Agent 日志失败')
  } finally {
    logLoading.value = false
  }
}

const parseJsonArray = <T,>(jsonText?: string): T[] => {
  if (!jsonText) {
    return []
  }

  try {
    return JSON.parse(jsonText) as T[]
  } catch (error) {
    console.error(error)
    return []
  }
}

const filteredAgentLogs = computed(() => {
  if (logStatusFilter.value === 'ALL') {
    return agentLogs.value
  }

  return agentLogs.value.filter((log) => log.status === logStatusFilter.value)
})

const successLogCount = computed(() => {
  return agentLogs.value.filter((log) => log.status === 'SUCCESS').length
})

const failedLogCount = computed(() => {
  return agentLogs.value.filter((log) => log.status === 'FAILED').length
})

const titleOptions = computed(() => {
  return parseJsonArray<TitleOption>(article.value?.titleOptions)
})

const outlineItems = computed(() => {
  return parseJsonArray<OutlineItem>(article.value?.outline)
})

const displayContent = computed(() => {
  return article.value?.content || streamContent.value || ''
})

const imageResultOptions = computed(() => {
  return parseJsonArray<ImageResultOption>(article.value?.imageResults)
})

const taskId = computed(() => route.params.taskId as string)

const contentHtml = computed(() => {
  if (!displayContent.value) {
    return ''
  }
  return markdown.render(displayContent.value)
})

const editableContentHtml = computed(() => {
  if (!editableContent.value) {
    return ''
  }

  return markdown.render(editableContent.value)
})

const finalArticleHtml = computed(() => {
  const finalMarkdownText =
    article.value?.finalMarkdown || ''

  if (!finalMarkdownText) {
    return ''
  }

  const rawHtml = marked.parse(
    finalMarkdownText,
    {
      async: false,
    }
  ) as string

  return DOMPurify.sanitize(rawHtml)
})

const editableContentCount = computed(() => {
  return editableContent.value.length
})

const imagePromptOptions = computed(() => {
  return parseJsonArray<ImagePromptOption>(article.value?.imagePrompts)
})

const canRetryTitle = computed(() => {
  return article.value?.status === 'FAILED'
    && article.value?.phase === 'TITLE_SELECTION'
})

const canRetryOutline = computed(() => {
  return article.value?.status === 'FAILED'
    && article.value?.phase === 'OUTLINE_EDITING'
    && !!selectedTitle.value
})

const canRetryContent = computed(() => {
  return article.value?.status === 'FAILED'
    && article.value?.phase === 'CONTENT_GENERATION'
    && !!article.value?.outline
})

const nextWorkflowText = computed(() => {
  if (!article.value) {
    return '继续下一步'
  }

  if (article.value.status === 'FAILED') {
    return '当前失败，请先处理错误'
  }

  if (article.value.phase === 'CREATED') {
    return '继续下一步：生成标题'
  }

  if (article.value.phase === 'TITLE_SELECTION') {
    if (
      !article.value.selectedTitle
      && !selectedTitle.value
    ) {
      return '请选择标题后继续'
    }

    return '继续下一步：生成大纲'
  }

  if (article.value.phase === 'OUTLINE_EDITING') {
    if (!article.value.outline) {
      return '请先生成或保存大纲'
    }

    return '继续下一步：生成正文'
  }

  if (article.value.phase === 'CONTENT_GENERATION') {
    if (
      !article.value.content
      && !streamContent.value
    ) {
      return '继续下一步：生成正文'
    }

    if (!article.value.imagePrompts) {
      return '继续下一步：生成配图提示词'
    }

    if (!article.value.imageResults) {
      return '继续下一步：生成图片结果'
    }

    if (!article.value.finalMarkdown) {
      return '继续下一步：生成最终图文稿'
    }

    return '主线流程已完成'
  }

  if (
    article.value.phase
      === 'ARTICLE_COMPOSITION'
  ) {
    if (!article.value.finalMarkdown) {
      return '继续下一步：生成最终图文稿'
    }

    return '主线流程已完成'
  }

  return '继续下一步'
})

const workflowDisabled = computed(() => {
  if (!article.value) {
    return true
  }

  if (article.value.status === 'FAILED') {
    return true
  }

  if (
    article.value.phase === 'TITLE_SELECTION'
    && !article.value.selectedTitle
    && !selectedTitle.value
  ) {
    return true
  }

  if (
    article.value.phase === 'CONTENT_GENERATION'
    && article.value.content
    && article.value.imagePrompts
    && article.value.imageResults
    && article.value.finalMarkdown
  ) {
    return true
  }

  if (
    article.value.phase
      === 'ARTICLE_COMPOSITION'
    && article.value.finalMarkdown
  ) {
    return true
  }

  return false
})

const handleContinueWorkflow = async () => {
  if (!taskId.value) {
    return
  }

  workflowLoading.value = true
  try {
    if (article.value?.phase === 'TITLE_SELECTION' && selectedTitle.value) {
      const res = await confirmTitle({
        taskId: taskId.value,
        selectedTitle: selectedTitle.value,
      })

      if (res.code !== 0) {
        message.error(res.message)
        await loadArticle()
        await loadAgentLogs()
        return
      }

      article.value = res.data
      message.success('大纲生成成功')
      await loadAgentLogs()
      return
    }

    const res = await continueWorkflow(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    selectedTitle.value = res.data.selectedTitle || selectedTitle.value || ''

    message.success('已推进到下一步')

    await loadAgentLogs()
  } catch (error) {
    console.error(error)
    message.error('继续下一步失败')
    await loadArticle()
    await loadAgentLogs()
  } finally {
    workflowLoading.value = false
  }
}

const handleCopyText = async (text?: string, successMessage = '复制成功') => {
  if (!text || !text.trim()) {
    message.warning('暂无内容可复制')
    return
  }

  try {
    await navigator.clipboard.writeText(text)
    message.success(successMessage)
  } catch (error) {
    console.error(error)
    message.error('复制失败，请检查浏览器权限')
  }
}

const getLogStatusColor = (status: string) => {
  if (status === 'SUCCESS') {
    return 'green'
  }

  if (status === 'FAILED') {
    return 'red'
  }

  return 'default'
}

const getAgentDisplayName = (agentName: string) => {
  const map: Record<string, string> = {
    TitleGeneratorAgent:
      '标题生成 Agent',

    OutlineGeneratorAgent:
      '大纲生成 Agent',

    ContentGeneratorAgent:
      '正文生成 Agent',

    ImagePromptAgent:
      '配图提示词 Agent',

    ImageResultAgent:
      '图片结果 Agent',

    ArticleComposeAgent:
      '图文合成 Agent',

    JsonRepairAgent:
      'JSON 修复 Agent',
  }

  return map[agentName]
    || agentName
    || 'UnknownAgent'
}

const loadArticle = async () => {
  if (!taskId.value) {
    message.error('缺少 taskId')
    return
  }

  loading.value = true
  try {
    const res = await getArticle(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    article.value = res.data
    selectedTitle.value = res.data.selectedTitle || ''
  } catch (error) {
    console.error(error)
    message.error('加载文章详情失败')
  } finally {
    loading.value = false
  }
}

const handleGenerateTitles = async () => {
  if (!taskId.value) {
    return
  }

  titleLoading.value = true
  try {
    const res = await generateTitles(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    message.success('标题生成成功')
    await loadAgentLogs()
  } catch (error) {
    console.error(error)
    message.error('生成标题失败')
    await loadArticle()
    await loadAgentLogs()
  } finally {
    titleLoading.value = false
  }
}

const parseSseData = (event: MessageEvent) => {
  try {
    return JSON.parse(event.data)
  } catch (error) {
    console.error(error)
    return {
      message: event.data,
    }
  }
}

const handleConfirmTitle = async () => {
  if (!selectedTitle.value) {
    message.warning('请先选择一个标题')
    return
  }

  outlineLoading.value = true
  try {
    const res = await confirmTitle({
      taskId: taskId.value,
      selectedTitle: selectedTitle.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    message.success('大纲生成成功')
    await loadAgentLogs()
  } catch (error) {
    console.error(error)
    message.error('生成大纲失败')
    await loadArticle()
    await loadAgentLogs()
  } finally {
    outlineLoading.value = false
  }
}

const handleGenerateContent = async () => {
  if (!taskId.value) {
    return
  }

  if (!article.value?.outline) {
    message.warning('请先生成大纲')
    return
  }

  contentLoading.value = true
  try {
    const res = await generateContent(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    message.success('正文生成成功')
    await loadAgentLogs()
  } catch (error) {
    console.error(error)
    message.error('生成正文失败')
    await loadArticle()
    await loadAgentLogs()
  } finally {
    contentLoading.value = false
  }
}

const handleGenerateImagePrompts = async () => {
  if (!taskId.value) {
    return
  }

  if (!article.value?.content) {
    message.warning('请先生成正文')
    return
  }

  imagePromptLoading.value = true
  try {
    const res = await generateImagePrompts(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    message.success('图片提示词生成成功')

    await loadAgentLogs()
  } catch (error) {
    console.error(error)
    message.error('生成图片提示词失败')
    await loadArticle()
    await loadAgentLogs()
  } finally {
    imagePromptLoading.value = false
  }
}

const handleGenerateImageResults = async () => {
  if (!taskId.value) {
    return
  }

  if (!article.value?.imagePrompts) {
    message.warning('请先生成配图提示词')
    return
  }

  imageResultLoading.value = true
  try {
    const res = await generateImageResults(taskId.value, imageProvider.value)

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data
    message.success('图片结果生成成功')

    await loadAgentLogs()
  } catch (error) {
    console.error(error)
    message.error('生成图片结果失败')
    await loadArticle()
    await loadAgentLogs()
  } finally {
    imageResultLoading.value = false
  }
}

const handleComposeArticle = async () => {
  if (!taskId.value) {
    return
  }

  if (!article.value?.content) {
    message.warning(
      '请先生成文章正文'
    )
    return
  }

  if (!article.value?.imageResults) {
    message.warning(
      '请先生成图片结果'
    )
    return
  }

  composeLoading.value = true

  try {
    const res =
      await composeArticle(
        taskId.value
      )

    if (res.code !== 0) {
      message.error(res.message)

      await loadArticle()
      await loadAgentLogs()

      return
    }

    article.value = res.data

    message.success(
      '最终图文稿生成成功'
    )

    await loadAgentLogs()

  } catch (error) {
    console.error(error)

    message.error(
      '最终图文稿生成失败'
    )

    await loadArticle()
    await loadAgentLogs()

  } finally {
    composeLoading.value = false
  }
}

const sanitizeFileName = (value: string) => {
  return value
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, '_')
    .slice(0, 60)
    .trim() || 'article'
}

const handleDownloadMarkdown = () => {
  const finalMarkdown =
    article.value?.finalMarkdown

  if (!finalMarkdown) {
    message.warning('当前没有最终图文稿')
    return
  }

  const title =
    article.value?.selectedTitle
    || '未命名文章'

  const blob = new Blob(
    [finalMarkdown],
    {
      type: 'text/markdown;charset=utf-8',
    }
  )

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')

  link.href = url
  link.download =
    `${sanitizeFileName(title)}.md`

  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  URL.revokeObjectURL(url)

  message.success('最终图文 Markdown 已下载')
}

const handleStreamGenerateContent = async () => {
  if (!taskId.value) {
    return
  }

  if (!article.value?.outline) {
    message.warning('请先生成大纲')
    return
  }

  if (streaming.value) {
    message.warning('正在生成中，请稍等')
    return
  }

  streaming.value = true
  streamLogs.value = []
  streamContent.value = ''

  let closedByDone = false

  const eventSource = new EventSource(streamGenerateContentUrl(taskId.value))

  eventSource.addEventListener('progress', (event) => {
    const data = parseSseData(event as MessageEvent)
    if (data.message) {
      streamLogs.value.push(data.message)
    }
  })

  eventSource.addEventListener('content', (event) => {
    const data = parseSseData(event as MessageEvent)
    if (data.message) {
      streamLogs.value.push(data.message)
    }
    if (data.content) {
      streamContent.value = data.content
    }
  })

  eventSource.addEventListener('done', async (event) => {
    const data = parseSseData(event as MessageEvent)

    if (data.message) {
      streamLogs.value.push(data.message)
    }

    if (data.content) {
      streamContent.value = data.content
    }

    closedByDone = true
    eventSource.close()
    streaming.value = false

    message.success('流式正文生成成功')

    await loadArticle()
    await loadAgentLogs()
  })

  eventSource.addEventListener('fail', async(event) => {
    const data = parseSseData(event as MessageEvent)

    if (data.message) {
      streamLogs.value.push(data.message)
      message.error(data.message)
    } else {
      message.error('流式生成失败')
    }

    closedByDone = true
    eventSource.close()
    streaming.value = false
    await loadArticle()
    await loadAgentLogs()
  })

  eventSource.onerror = async() => {
    if (!closedByDone) {
      message.error('SSE 连接异常')
      streamLogs.value.push('SSE 连接异常')
      await loadArticle()
      await loadAgentLogs()
    }

    eventSource.close()
    streaming.value = false
  }
}

const buildFinalMarkdown = () => {
  const title = article.value?.selectedTitle || '未命名文章'
  const content = article.value?.content || streamContent.value || ''

  return `# ${title}\n\n${content}`
}

const handleCopyMarkdown = async () => {
  const content = article.value?.content || streamContent.value || ''

  if (!content.trim()) {
    message.warning('暂无正文可复制')
    return
  }

  try {
    const markdown = buildFinalMarkdown()
    await navigator.clipboard.writeText(markdown)
    message.success('已复制全文 Markdown')
  } catch (error) {
    console.error(error)
    message.error('复制失败，请检查浏览器权限')
  }
}

const handleExportMarkdown = () => {
  const content = article.value?.content || streamContent.value || ''

  if (!content.trim()) {
    message.warning('暂无正文可导出')
    return
  }

  const markdown = buildFinalMarkdown()
  const title = article.value?.selectedTitle || '未命名文章'
  const fileName = `${sanitizeFileName(title)}.md`

  const blob = new Blob([markdown], {
    type: 'text/markdown;charset=utf-8',
  })

  const url = URL.createObjectURL(blob)

  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  URL.revokeObjectURL(url)

  message.success('Markdown 文件已导出')
}

const goBack = () => {
  router.push('/article/list')
}

onMounted(() => {
  loadArticle()
  loadAgentLogs()
})
</script>

<template>
  <div class="page">
   <a-space>
    <a-button @click="goBack">返回列表</a-button>

    <a-button
      type="primary"
      :loading="workflowLoading"
      :disabled="workflowDisabled"
      @click="handleContinueWorkflow"
    >
      {{ nextWorkflowText }}
    </a-button>

    <a-button type="primary" :loading="titleLoading" @click="handleGenerateTitles">
      生成标题
    </a-button>

    <a-button :loading="outlineLoading" @click="handleConfirmTitle">
      确认标题并生成大纲
    </a-button>

    <a-button :loading="contentLoading" @click="handleGenerateContent">
      生成正文
    </a-button>

    <a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
      流式生成正文
    </a-button>

    <a-button type="primary" danger ghost :loading="realStreaming" @click="handleRealStreamGenerateContent">
      真实流式生成正文
    </a-button>

    <a-button
      v-if="article?.content || streamContent"
      :loading="imagePromptLoading"
      @click="handleGenerateImagePrompts"
    >
      生成配图提示词
    </a-button>

    <a-button
      v-if="article?.content || streamContent"
      @click="handleExportMarkdown"
    >
      导出纯文本 Markdown
    </a-button>
  </a-space>

    <a-card v-if="article" class="card" :loading="loading">
      <template #title>
        文章任务详情
      </template>

      <a-descriptions bordered :column="1">
        <a-descriptions-item label="任务 ID">
          {{ article.taskId }}
        </a-descriptions-item>

        <a-descriptions-item label="选题">
          {{ article.topic }}
        </a-descriptions-item>

        <a-descriptions-item label="风格">
          {{ article.style }}
        </a-descriptions-item>

        <a-descriptions-item label="阶段">
          {{ article.phase }}
        </a-descriptions-item>

        <a-descriptions-item label="状态">
          <a-tag :color="article.status === 'SUCCESS' ? 'green' : article.status === 'FAILED' ? 'red' : 'blue'">
            {{ article.status }}
          </a-tag>
        </a-descriptions-item>

        <a-descriptions-item label="已选标题">
          {{ article.selectedTitle || '暂未选择' }}
        </a-descriptions-item>

        <a-descriptions-item label="创建时间">
          {{ article.createTime }}
        </a-descriptions-item>

        <a-descriptions-item label="更新时间">
          {{ article.updateTime }}
        </a-descriptions-item>

        <a-descriptions-item v-if="article.errorMessage" label="错误信息">
          <span class="error-text">{{ article.errorMessage }}</span>
        </a-descriptions-item>
        
      </a-descriptions>
    </a-card>

    <a-card v-if="article" class="card" title="多 Agent 协作流程">
      <a-steps
        :current="
          article.phase === 'CREATED' ? 0 :
          article.phase === 'TITLE_SELECTION' ? 1 :
          article.phase === 'OUTLINE_EDITING' ? 2 :
          article.phase === 'CONTENT_GENERATION'
            && !article.content ? 3 :
          article.phase === 'CONTENT_GENERATION'
            && !article.imagePrompts ? 4 :
          article.phase === 'CONTENT_GENERATION'
            && !article.imageResults ? 5 :
          article.phase === 'CONTENT_GENERATION'
            && !article.finalMarkdown ? 6 :
          article.phase === 'ARTICLE_COMPOSITION' ? 6 :
          6
        "
        size="small"
      >
        <a-step
          title="创建任务"
          description="输入选题"
        />

        <a-step
          title="标题生成"
          description="TitleGeneratorAgent"
        />

        <a-step
          title="大纲生成"
          description="OutlineGeneratorAgent"
        />

        <a-step
          title="正文生成"
          description="ContentGeneratorAgent"
        />

        <a-step
          title="配图提示词"
          description="ImagePromptAgent"
        />

        <a-step
          title="图片结果"
          description="ImageResultAgent"
        />

        <a-step
          title="最终图文稿"
          description="ArticleComposeAgent"
        />
      </a-steps>
    </a-card>

    <a-alert
      v-if="article?.status === 'FAILED'"
      class="card"
      type="error"
      show-icon
      message="当前任务执行失败"
      :description="article.errorMessage || '暂无详细错误信息'"
    />

    <a-card
      v-if="article?.status === 'FAILED'"
      class="card"
      title="失败处理"
    >
      <a-space>
        <a-button
          v-if="canRetryTitle"
          type="primary"
          :loading="titleLoading"
          @click="handleGenerateTitles"
        >
          重试生成标题
        </a-button>

        <a-button
          v-if="canRetryOutline"
          type="primary"
          :loading="outlineLoading"
          @click="handleConfirmTitle"
        >
          重试生成大纲
        </a-button>

        <a-button
          v-if="canRetryContent"
          type="primary"
          :loading="contentLoading"
          @click="handleGenerateContent"
        >
          重试生成正文
        </a-button>

        <a-button
          v-if="canRetryContent"
          danger
          ghost
          :loading="realStreaming"
          @click="handleRealStreamGenerateContent"
        >
          重试真实流式生成正文
        </a-button>
      </a-space>
    </a-card>

    <a-card v-if="titleOptions.length > 0" class="card" title="标题候选">
          <a-radio-group v-model:value="selectedTitle" class="title-list">
            <a-radio
              v-for="item in titleOptions"
              :key="item.title"
              :value="item.title"
              class="title-option"
            >
              <div class="title-text">{{ item.title }}</div>
              <div class="reason-text">{{ item.reason }}</div>
            </a-radio>
          </a-radio-group>

          <div class="action-row">
            <a-button type="primary" :loading="outlineLoading" @click="handleConfirmTitle">
              确认标题并生成大纲
            </a-button>
          </div>
        </a-card>

        <a-card v-if="article?.selectedTitle" class="card" title="已选标题">
      <template #extra>
        <a-space>
          <a-button v-if="!titleEditing" @click="handleStartEditTitle">
            编辑标题
          </a-button>

          <template v-else>
            <a-button @click="handleCancelEditTitle">
              取消
            </a-button>

            <a-button type="primary" :loading="titleSaving" @click="handleSaveTitle">
              保存标题
            </a-button>
          </template>
        </a-space>
      </template>

      <template v-if="!titleEditing">
        <h2>{{ article.selectedTitle }}</h2>
        <p class="hint-text">
          如果修改标题，后续可以基于新标题重新生成大纲。
        </p>
      </template>

      <template v-else>
        <a-input
          v-model:value="editableTitle"
          placeholder="请输入标题"
          size="large"
        />
      </template>
    </a-card>

    <a-card v-if="outlineItems.length > 0 || outlineEditing" class="card" title="文章大纲">
      <template #extra>
        <a-space>
          <a-button v-if="!outlineEditing" @click="handleStartEditOutline">
            编辑大纲
          </a-button>

          <template v-else>
            <a-button @click="handleAddOutlineItem">
              新增章节
            </a-button>

            <a-button @click="handleCancelEditOutline">
              取消
            </a-button>

            <a-button type="primary" :loading="outlineSaving" @click="handleSaveOutline">
              保存大纲
            </a-button>
          </template>
        </a-space>
      </template>

      <template v-if="!outlineEditing">
        <a-timeline>
          <a-timeline-item v-for="item in outlineItems" :key="item.heading">
            <h3>{{ item.heading }}</h3>
            <p>{{ item.description }}</p>
          </a-timeline-item>
        </a-timeline>

        <div class="action-row">
          <a-button type="primary" :loading="contentLoading" @click="handleGenerateContent">
            基于当前大纲生成正文
          </a-button>

          <a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
            流式生成正文
          </a-button>

          <a-button type="primary" danger ghost :loading="realStreaming" @click="handleRealStreamGenerateContent">
            真实流式生成正文
          </a-button>
        </div>
      </template>

      <template v-else>
        <div class="outline-edit-list">
          <a-card
            v-for="(item, index) in editableOutline"
            :key="index"
            size="small"
            class="outline-edit-item"
          >
            <template #title>
              第 {{ index + 1 }} 个章节
            </template>

            <template #extra>
              <a-button danger size="small" @click="handleRemoveOutlineItem(index)">
                删除
              </a-button>
            </template>

            <a-form layout="vertical">
              <a-form-item label="章节标题">
                <a-input
                  v-model:value="item.heading"
                  placeholder="请输入章节标题"
                />
              </a-form-item>

              <a-form-item label="章节说明">
                <a-textarea
                  v-model:value="item.description"
                  placeholder="请输入章节说明"
                  :rows="3"
                />
              </a-form-item>
            </a-form>
          </a-card>
        </div>
      </template>
    </a-card>

    <a-card v-if="streamLogs.length > 0" class="card" title="生成进度">
      <a-timeline>
        <a-timeline-item v-for="(log, index) in streamLogs" :key="index">
          {{ log }}
        </a-timeline-item>
      </a-timeline>
    </a-card>

    <a-card v-if="article?.content || streamContent || contentEditing" class="card" title="文章正文">
      <template #extra>
        <a-space>
          <a-button
            v-if="!contentEditing && (article?.content || streamContent)"
            @click="handleCopyMarkdown"
          >
            复制全文
          </a-button>

          <a-button
            v-if="!contentEditing && (article?.content || streamContent)"
            :loading="imagePromptLoading"
            @click="handleGenerateImagePrompts"
          >
            生成配图提示词
          </a-button>

          <a-button
            v-if="!contentEditing && (article?.content || streamContent)"
            @click="handleExportMarkdown"
          >
            导出纯文本 Markdown
          </a-button>

          <a-button
            v-if="!contentEditing && (article?.content || streamContent)"
            @click="handleStartEditContent"
          >
            编辑正文
          </a-button>

          <template v-if="contentEditing">
            <a-button @click="handleCancelEditContent">
              取消
            </a-button>

            <a-button type="primary" :loading="contentSaving" @click="handleSaveContent">
              保存正文
            </a-button>
          </template>
        </a-space>
      </template>

      <template v-if="!contentEditing">
        <div class="markdown-body" v-html="contentHtml"></div>
      </template>

      <template v-else>
        <div class="content-editor-toolbar">
          <span class="content-count">
            {{ editableContentCount }} 字符
          </span>

          <a-radio-group v-model:value="contentEditMode" button-style="solid">
            <a-radio-button value="edit">
              编辑
            </a-radio-button>
            <a-radio-button value="preview">
              预览
            </a-radio-button>
          </a-radio-group>
        </div>

        <a-textarea
          v-if="contentEditMode === 'edit'"
          v-model:value="editableContent"
          placeholder="请输入或编辑正文 Markdown"
          :rows="22"
        />

        <div
          v-else
          class="markdown-body markdown-preview-box"
          v-html="editableContentHtml"
        ></div>
      </template>
    </a-card>

    <a-card
      v-if="imagePromptOptions.length > 0"
      class="card"
      title="配图提示词方案"
    >
      <template #extra>
        <a-space>
          <a-radio-group v-model:value="imageProvider" button-style="solid">
            <a-radio-button value="AUTO">自动</a-radio-button>
            <a-radio-button value="PEXELS">Pexels</a-radio-button>
            <a-radio-button value="SILICONFLOW">SiliconFlow 生图</a-radio-button>
            <a-radio-button value="GOOGLE_AI">Google 生图</a-radio-button>
          </a-radio-group>

          <a-button
            :loading="imageResultLoading"
            @click="handleGenerateImageResults"
          >
            生成图片结果
          </a-button>
        </a-space>
      </template>

      <a-list :data-source="imagePromptOptions" item-layout="vertical">
        <template #renderItem="{ item, index }">
          <a-list-item>
            <a-card size="small" class="image-prompt-card">
              <template #title>
                {{ index + 1 }}. {{ item.imageTitle }}
              </template>

              <template #extra>
                <a-tag color="blue">
                  {{ item.usageScene }}
                </a-tag>
              </template>


              <div class="image-prompt-section">
                <div class="image-prompt-header">
                  <strong>中文提示词</strong>
                  <a-button
                    size="small"
                    @click="handleCopyText(item.promptZh, '中文提示词已复制')"
                  >
                    复制
                  </a-button>
                </div>
                <pre class="log-text">{{ item.promptZh }}</pre>
              </div>

              <div class="image-prompt-section">
                <div class="image-prompt-header">
                  <strong>英文提示词</strong>
                  <a-button
                    size="small"
                    @click="handleCopyText(item.promptEn, '英文提示词已复制')"
                  >
                    复制
                  </a-button>
                </div>
                <pre class="log-text">{{ item.promptEn }}</pre>
              </div>
            </a-card>
          </a-list-item>
        </template>
      </a-list>
    </a-card>

    <a-card
      v-if="imageResultOptions.length > 0"
      class="card"
      title="图片结果"
    >
      <a-row :gutter="[16, 16]">
        <a-col
          v-for="(item, index) in imageResultOptions"
          :key="index"
          :xs="24"
          :md="8"
        >
          <a-card class="image-result-card" hoverable>
            <template #cover>
              <img
                :src="item.imageUrl"
                :alt="item.imageTitle"
                class="image-result-img"
              />
            </template>

            <a-card-meta
              :title="item.imageTitle"
              :description="item.usageScene"
            />

            <div class="image-result-meta">
              <a-tag v-if="item.source" color="blue">
                {{ item.source }}
              </a-tag>

              <div v-if="item.author" class="image-credit">
                图片作者：
                <a
                  v-if="item.authorUrl"
                  :href="item.authorUrl"
                  target="_blank"
                >
                  {{ item.author }}
                </a>
                <span v-else>{{ item.author }}</span>
              </div>

              <div v-if="item.sourceUrl" class="image-credit">
                <a :href="item.sourceUrl" target="_blank">
                  查看图片来源
                </a>
              </div>
            </div>

            <div class="image-prompt-section">
              <div class="image-prompt-header">
                <strong>中文提示词</strong>
                <a-button
                  size="small"
                  @click="handleCopyText(item.promptZh, '中文提示词已复制')"
                >
                  复制
                </a-button>
              </div>
              <pre class="log-text">{{ item.promptZh }}</pre>
            </div>

            <div class="image-prompt-section">
              <div class="image-prompt-header">
                <strong>英文提示词</strong>
                <a-button
                  size="small"
                  @click="handleCopyText(item.promptEn, '英文提示词已复制')"
                >
                  复制
                </a-button>
              </div>
              <pre class="log-text">{{ item.promptEn }}</pre>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </a-card>

    <a-card
      v-if="article?.imageResults"
      class="card"
      title="最终图文稿"
    >
      <template #extra>
        <a-space>
          <a-button
            type="primary"
            :loading="composeLoading"
            :disabled="
              !article?.content
              || !article?.imageResults
            "
            @click="handleComposeArticle"
          >
            {{
              article?.finalMarkdown
                ? '重新生成最终图文稿'
                : '生成最终图文稿'
            }}
          </a-button>

          <a-button
            :disabled="!article?.finalMarkdown"
            @click="
              handleCopyText(
                article?.finalMarkdown,
                '最终图文稿已复制'
              )
            "
          >
            复制最终图文稿
          </a-button>

          <a-button
            :disabled="!article?.finalMarkdown"
            @click="handleDownloadMarkdown"
          >
            下载最终 Markdown
          </a-button>
        </a-space>
      </template>

      <a-empty
        v-if="!article?.finalMarkdown"
        description="图片已经生成，可以开始生成最终图文稿"
      />

      <div
        v-else
        class="final-article-preview"
        v-html="finalArticleHtml"
      />
    </a-card>

    <a-card class="card" :loading="logLoading">
      <template #title>
        Agent 执行日志
      </template>

      <template #extra>
        <a-space>
          <a-radio-group v-model:value="logStatusFilter" button-style="solid">
            <a-radio-button value="ALL">
              全部 {{ agentLogs.length }}
            </a-radio-button>

            <a-radio-button value="SUCCESS">
              成功 {{ successLogCount }}
            </a-radio-button>

            <a-radio-button value="FAILED">
              失败 {{ failedLogCount }}
            </a-radio-button>
          </a-radio-group>

          <a-button :loading="logLoading" @click="loadAgentLogs">
            刷新日志
          </a-button>
        </a-space>
      </template>

      <a-empty
        v-if="filteredAgentLogs.length === 0"
        description="暂无符合条件的 Agent 日志"
      />

      <a-collapse v-else>
        <a-collapse-panel
          v-for="log in filteredAgentLogs"
          :key="log.id"
        >
          <template #header>
            <div class="log-panel-header">
              <a-space>
                <span class="log-agent-name">
                  {{ getAgentDisplayName(log.agentName) }}
                </span>

                <a-tag :color="getLogStatusColor(log.status)">
                  {{ log.status }}
                </a-tag>

                <span class="log-cost">
                  {{ log.costMs || 0 }}ms
                </span>

                <span class="log-time">
                  {{ log.createTime }}
                </span>
              </a-space>
            </div>
          </template>

          <div class="log-meta">
            <p>
              <strong>Agent：</strong>
              {{ log.agentName }}
            </p>

            <p>
              <strong>执行状态：</strong>
              <a-tag :color="getLogStatusColor(log.status)">
                {{ log.status }}
              </a-tag>
            </p>

            <p>
              <strong>耗时：</strong>
              {{ log.costMs || 0 }}ms
            </p>

            <p>
              <strong>创建时间：</strong>
              {{ log.createTime }}
            </p>

            <p v-if="log.errorMessage">
              <strong>错误信息：</strong>
              <span class="error-text">{{ log.errorMessage }}</span>
            </p>
          </div>

          <a-divider />

          <div class="log-section">
            <div class="log-section-header">
              <h4>输入 Prompt</h4>

              <a-button
                size="small"
                @click.stop="handleCopyText(log.inputText, 'Prompt 已复制')"
              >
                复制 Prompt
              </a-button>
            </div>

            <pre class="log-text">{{ log.inputText || '无' }}</pre>
          </div>

          <div class="log-section">
            <div class="log-section-header">
              <h4>模型输出</h4>

              <a-button
                size="small"
                @click.stop="handleCopyText(log.outputText, '模型输出已复制')"
              >
                复制输出
              </a-button>
            </div>

            <pre class="log-text">{{ log.outputText || '无' }}</pre>
          </div>

          <div v-if="log.errorMessage" class="log-section">
            <div class="log-section-header">
              <h4>错误信息</h4>

              <a-button
                size="small"
                danger
                @click.stop="handleCopyText(log.errorMessage, '错误信息已复制')"
              >
                复制错误
              </a-button>
            </div>

            <pre class="log-text error-log-text">{{ log.errorMessage }}</pre>
          </div>
        </a-collapse-panel>
      </a-collapse>
    </a-card>

    <a-empty v-if="!article && !loading" description="文章任务不存在" />
  </div>
</template>

<style scoped>

.markdown-body {
  line-height: 1.85;
  color: #1f2937;
  background: #fff;
  font-size: 15px;
}

.markdown-body :deep(h1) {
  font-size: 30px;
  line-height: 1.35;
  margin: 0 0 24px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
  font-weight: 700;
}

.markdown-body :deep(h2) {
  font-size: 23px;
  line-height: 1.45;
  margin-top: 32px;
  margin-bottom: 14px;
  font-weight: 650;
}

.markdown-body :deep(h3) {
  font-size: 19px;
  line-height: 1.45;
  margin-top: 24px;
  margin-bottom: 10px;
  font-weight: 650;
}

.markdown-body :deep(p) {
  margin: 0 0 14px;
}

.markdown-body :deep(strong) {
  font-weight: 700;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 26px;
  margin: 0 0 16px;
}

.markdown-body :deep(li) {
  margin-bottom: 6px;
}

.markdown-body :deep(blockquote) {
  margin: 18px 0;
  padding: 12px 16px;
  color: #4b5563;
  background: #f9fafb;
  border-left: 4px solid #d9d9d9;
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  background: #f3f4f6;
  border-radius: 4px;
  font-size: 13px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.markdown-body :deep(pre) {
  margin: 18px 0;
  padding: 14px 16px;
  overflow: auto;
  background: #111827;
  color: #f9fafb;
  border-radius: 8px;
  line-height: 1.7;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
  border-radius: 0;
}

.markdown-body :deep(a) {
  color: #1677ff;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #eee;
  margin: 28px 0;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 18px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 8px 10px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f9fafb;
  font-weight: 600;
}

.page {
  padding: 32px;
  background: #f5f5f5;
  min-height: 100vh;
}

.card {
  margin-top: 24px;
}

.title-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.title-option {
  padding: 12px;
  border: 1px solid #eee;
  border-radius: 8px;
  width: 100%;
}

.title-text {
  font-weight: 600;
  margin-bottom: 4px;
}

.reason-text {
  color: #666;
  font-size: 14px;
}

.outline-edit-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.outline-edit-item {
  border: 1px solid #f0f0f0;
}

.action-row {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.markdown-preview-box {
  min-height: 420px;
  padding: 20px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
}

.content-editor-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content-count {
  color: #888;
  font-size: 13px;
}

.log-panel-header {
  display: flex;
  align-items: center;
  width: 100%;
}

.log-agent-name {
  font-weight: 600;
}

.log-cost {
  color: #666;
  font-size: 13px;
}

.log-time {
  color: #999;
  font-size: 13px;
}

.log-meta {
  color: #333;
  font-size: 14px;
}

.log-meta p {
  margin: 0 0 8px;
}

.log-section {
  margin-top: 16px;
}

.log-section-header {
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-section-header h4 {
  margin: 0;
}

.log-text {
  white-space: pre-wrap;
  word-break: break-word;
  background: #f7f7f7;
  padding: 12px;
  border-radius: 6px;
  max-height: 360px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
}

.error-text {
  color: #cf1322;
}

.error-log-text {
  background: #fff1f0;
  color: #a8071a;
}

.image-prompt-card {
  width: 100%;
}

.image-prompt-section {
  margin-top: 12px;
}

.image-prompt-header {
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.image-result-card {
  height: 100%;
}

.image-result-img {
  width: 100%;
  height: 180px;
  object-fit: cover;
}

.image-result-meta {
  margin-top: 12px;
}

.image-credit {
  margin-top: 6px;
  font-size: 13px;
  color: #666;
}

.final-article-preview {
  max-width: 900px;
  margin: 0 auto;
  padding: 28px 36px;
  line-height: 1.85;
  color: #1f2937;
  background: #ffffff;
}

.final-article-preview :deep(h1) {
  margin: 0 0 28px;
  padding-bottom: 14px;
  border-bottom: 1px solid #eeeeee;
  font-size: 32px;
  line-height: 1.35;
  font-weight: 700;
}

.final-article-preview :deep(h2) {
  margin-top: 38px;
  margin-bottom: 16px;
  font-size: 24px;
  line-height: 1.45;
}

.final-article-preview :deep(h3) {
  margin-top: 28px;
  margin-bottom: 12px;
  font-size: 20px;
}

.final-article-preview :deep(p) {
  margin: 0 0 16px;
}

.final-article-preview :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 600px;
  margin: 26px auto 10px;
  border-radius: 10px;
  object-fit: contain;
}

.final-article-preview :deep(blockquote) {
  margin: 8px 0 26px;
  padding: 10px 16px;
  color: #666666;
  background: #fafafa;
  border-left: 4px solid #d9d9d9;
}

.final-article-preview :deep(ul),
.final-article-preview :deep(ol) {
  padding-left: 26px;
}

.final-article-preview :deep(a) {
  color: #1677ff;
}

.final-article-preview :deep(pre) {
  overflow-x: auto;
  padding: 16px;
  color: #f9fafb;
  background: #111827;
  border-radius: 8px;
}

.final-article-preview :deep(code) {
  font-family:
    Consolas,
    Monaco,
    'Courier New',
    monospace;
}

</style>