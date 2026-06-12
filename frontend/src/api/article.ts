import request from './request'


export interface BaseResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}

export interface ArticleVO {
  id: number
  taskId: string
  topic: string
  style: string
  phase: string
  status: string
  titleOptions?: string
  selectedTitle?: string
  outline?: string
  content?: string
  imagePrompts?: string
  fullContent?: string
  errorMessage?: string
  imageResults?: string
  finalMarkdown?: string
  createTime: string
  updateTime: string
}

export interface ArticleCreateRequest {
  topic: string
  style?: string
}

export interface ArticleListRequest {
  pageNo: number
  pageSize: number
}

export interface TitleOption {
  title: string
  reason: string
}

export interface OutlineItem {
  heading: string
  description: string
}

export interface ConfirmTitleRequest {
  taskId: string
  selectedTitle: string
}

export interface AgentLogVO {
  id: number
  taskId: string
  agentName: string
  inputText?: string
  outputText?: string
  status: string
  costMs?: number
  errorMessage?: string
  createTime: string
}

export interface SaveOutlineRequest {
  taskId: string
  outline: OutlineItem[]
}

export interface SaveTitleRequest {
  taskId: string
  selectedTitle: string
}

export interface SaveContentRequest {
  taskId: string
  content: string
}

export interface ImagePromptOption {
  imageTitle: string
  usageScene: string
  promptZh: string
  promptEn: string
}

export type ImageProvider = 'AUTO' | 'PEXELS' | 'SILICONFLOW' | 'GOOGLE_AI'

export interface ImageResultOption {
  imageTitle: string
  usageScene: string
  imageUrl: string
  promptZh: string
  promptEn: string
  source?: string
  sourceUrl?: string
  author?: string
  authorUrl?: string
}

export const createArticle = async (data: ArticleCreateRequest) => {
  const res = await request.post<BaseResponse<string>>('/api/article/create', data)
  return res.data
}

export const listArticles = async (data: ArticleListRequest) => {
  const res = await request.post<BaseResponse<PageResult<ArticleVO>>>('/api/article/list', data)
  return res.data
}

export const getArticle = async (taskId: string) => {
  const res = await request.get<BaseResponse<ArticleVO>>(`/api/article/${taskId}`)
  return res.data
}

export const deleteArticle = async (taskId: string) => {
  const res = await request.post<BaseResponse<boolean>>(`/api/article/delete/${taskId}`)
  return res.data
}

export const generateTitles = async (taskId: string) => {
  const res = await request.post<BaseResponse<ArticleVO>>(`/api/article/generate-titles/${taskId}`)
  return res.data
}

export const confirmTitle = async (data: ConfirmTitleRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/confirm-title', data)
  return res.data
}

export const generateContent = async (taskId: string) => {
  const res = await request.post<BaseResponse<ArticleVO>>(`/api/article/generate-content/${taskId}`)
  return res.data
}

export const streamGenerateContentUrl = (taskId: string) => {
  return `http://localhost:8123/api/article/stream-generate-content/${taskId}`
}


export const listAgentLogs = async (taskId: string) => {
  const res = await request.get<BaseResponse<AgentLogVO[]>>(`/api/article/agent-logs/${taskId}`)
  return res.data
}


export const realStreamGenerateContentUrl = (taskId: string) => {
  return `http://localhost:8123/api/article/real-stream-generate-content/${taskId}`
}

export const saveOutline = async (data: SaveOutlineRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/save-outline', data)
  return res.data
}

export const saveTitle = async (data: SaveTitleRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/save-title', data)
  return res.data
}

export const saveContent = async (data: SaveContentRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/save-content', data)
  return res.data
}

export const generateImagePrompts = async (taskId: string) => {
  const res = await request.post<BaseResponse<ArticleVO>>(`/api/article/generate-image-prompts/${taskId}`)
  return res.data
}

export const continueWorkflow = async (taskId: string) => {
  const res = await request.post<BaseResponse<ArticleVO>>(`/api/article/continue-workflow/${taskId}`)
  return res.data
}

export const generateImageResults = async (
  taskId: string,
  provider: ImageProvider = 'AUTO'
) => {
  const res = await request.post<BaseResponse<ArticleVO>>(
    `/api/article/generate-image-results/${taskId}`,
    null,
    {
      params: {
        provider,
      },
    }
  )
  return res.data
}

/**
 * 生成最终图文稿。
 */
export const composeArticle = async (
  taskId: string
) => {
  const res = await request.post<
    BaseResponse<ArticleVO>
  >(
    `/api/article/compose/${taskId}`
  )

  return res.data
}