# AI Article Studio：Day 12 完整开发教程

> Day 12 目标：实现 **复制全文 + 导出 Markdown 文件**，并修复“历史正文前端不显示”的问题。  
> 让项目从“网页内生成和编辑文章”升级为“可以把最终文章复制或导出使用”。

---

## 0. Day 12 最终成果

完成 Day 12 后，文章详情页支持：

```text
1. 显示数据库中已经保存过的历史正文
2. 一键复制最终全文 Markdown
3. 一键导出 Markdown 文件
4. 导出的 .md 文件包含标题和正文
5. 用户编辑后的标题 / 正文也能被复制和导出
```

Day 12 完成后的完整流程：

```text
AI 生成标题
↓
用户编辑标题
↓
AI 生成大纲
↓
用户编辑大纲
↓
AI 生成正文
↓
用户编辑正文
↓
显示历史正文
↓
复制全文 / 导出 Markdown
```

---

## 1. Day 12 先修复的问题：历史正文不显示

在做复制和导出之前，先发现了一个问题：

```text
数据库里已经有正文 content，
后端详情接口也返回了 content，
但前端详情页没有显示正文。
```

排查结果：

```text
1. 数据库 article.content 有正文
2. 后端 GET /api/article/{taskId} 返回里也有 content
3. ArticleVO 里也有 content 字段
4. 问题出在 ArticleDetail.vue
```

根因是：

```text
文章正文卡片只有按钮，没有写正文主体展示区域。
```

也就是原来模板类似：

```vue
<a-card v-if="article?.content || streamContent || contentEditing" class="card" title="文章正文">
  <template #extra>
    ...
  </template>
</a-card>
```

它只有右上角按钮，没有正文内容展示，所以即使 `article.content` 有值，也不会显示。

---

## 2. 修复正文展示卡片

把正文卡片改成下面这样：

```vue
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
        @click="handleExportMarkdown"
      >
        导出 Markdown
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
    <a-textarea
      v-model:value="editableContent"
      placeholder="请输入或编辑正文 Markdown"
      :rows="18"
    />
  </template>
</a-card>
```

关键新增的是：

```vue
<template v-if="!contentEditing">
  <div class="markdown-body" v-html="contentHtml"></div>
</template>
```

作用：

```text
非编辑模式下，把正文 Markdown 渲染成 HTML 展示出来。
```

---

## 3. 调整正文展示优先级

原来可能是：

```ts
const displayContent = computed(() => {
  return streamContent.value || article.value?.content || ''
})
```

建议改成：

```ts
const displayContent = computed(() => {
  return article.value?.content || streamContent.value || ''
})
```

原因：

```text
article.value.content 是数据库里已经保存的正式正文。
streamContent 是流式生成过程中的临时内容。
```

所以正常展示历史正文时，应该优先展示：

```text
article.value.content
```

---

## 4. Markdown 渲染逻辑

Day 12 中已经使用：

```ts
import MarkdownIt from 'markdown-it'
```

并创建：

```ts
const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})
```

字段解释：

| 配置 | 作用 |
|---|---|
| `html: false` | 不允许直接渲染 HTML，避免安全风险 |
| `linkify: true` | 自动识别链接 |
| `breaks: true` | 普通换行也会显示为换行 |

正文 HTML 计算属性：

```ts
const contentHtml = computed(() => {
  if (!displayContent.value) {
    return ''
  }
  return markdown.render(displayContent.value)
})
```

作用：

```text
把 Markdown 正文转换成 HTML，供 v-html 渲染。
```

---

# 第一部分：复制全文功能

## 5. 新增构造最终 Markdown 方法

在 `ArticleDetail.vue` 的方法区新增：

```ts
const buildFinalMarkdown = () => {
  const title = article.value?.selectedTitle || '未命名文章'
  const content = article.value?.content || streamContent.value || ''

  return `# ${title}\n\n${content}`
}
```

作用：

```text
把当前标题和正文拼成最终 Markdown 文本。
```

例如：

```markdown
# 如何提高睡眠质量

这里是正文内容……
```

---

## 6. 新增复制全文方法

继续在 `ArticleDetail.vue` 方法区新增：

```ts
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
```

作用：

```text
把标题 + 正文复制到剪贴板。
```

这里用的是浏览器原生能力：

```ts
navigator.clipboard.writeText(...)
```

注意：

```text
部分浏览器要求页面运行在 localhost 或 HTTPS 环境下，剪贴板 API 才能正常使用。
```

你现在是：

```text
http://localhost:5173
```

一般可以正常使用。

---

# 第二部分：导出 Markdown 文件

## 7. 新增安全文件名方法

导出文件时，文件名不能包含某些特殊字符，例如：

```text
/ \ : * ? " < > |
```

所以新增：

```ts
const sanitizeFileName = (name: string) => {
  return name
    .replace(/[\\/:*?"<>|]/g, '')
    .replace(/\s+/g, '_')
    .slice(0, 60) || 'article'
}
```

作用：

```text
把文章标题转换成安全的文件名。
```

---

## 8. 新增导出 Markdown 方法

继续在方法区新增：

```ts
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
```

作用：

```text
1. 读取当前标题和正文
2. 拼成 Markdown 文本
3. 创建 Blob 文件对象
4. 生成临时下载链接
5. 自动触发浏览器下载
6. 下载完成后释放临时 URL
```

---

## 9. 理解导出代码

### 9.1 Blob

```ts
const blob = new Blob([markdown], {
  type: 'text/markdown;charset=utf-8',
})
```

含义：

```text
在浏览器内存中创建一个文件对象。
```

这里的文件内容是 Markdown，文件类型是 `text/markdown`，字符编码是 `utf-8`，可以避免中文乱码。

### 9.2 URL.createObjectURL

```ts
const url = URL.createObjectURL(blob)
```

含义：

```text
给浏览器内存中的文件创建一个临时下载地址。
```

### 9.3 动态创建 a 标签

```ts
const link = document.createElement('a')
link.href = url
link.download = fileName
document.body.appendChild(link)
link.click()
document.body.removeChild(link)
```

含义：

```text
动态创建一个下载链接，并模拟用户点击它。
```

其中：

```ts
link.download = fileName
```

表示告诉浏览器下载时使用这个文件名。

### 9.4 revokeObjectURL

```ts
URL.revokeObjectURL(url)
```

含义：

```text
释放刚刚创建的临时下载地址，避免内存泄漏。
```

---

# 第三部分：页面按钮修改

## 10. 正文卡片右上角加入按钮

正文卡片的 `#extra` 区域加入：

```vue
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
      @click="handleExportMarkdown"
    >
      导出 Markdown
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
```

现在正文卡片右上角会有：

```text
复制全文
导出 Markdown
编辑正文
```

编辑正文时，只显示：

```text
取消
保存正文
```

---

## 11. 顶部按钮区可选加入导出按钮

也可以在顶部按钮区加入：

```vue
<a-button
  v-if="article?.content || streamContent"
  @click="handleExportMarkdown"
>
  导出 Markdown
</a-button>
```

作用：

```text
用户不用滚动到正文区域，也能直接导出 Markdown。
```

---

# 第四部分：完整新增代码汇总

## 12. `ArticleDetail.vue` 新增方法汇总

```ts
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

const sanitizeFileName = (name: string) => {
  return name
    .replace(/[\\/:*?"<>|]/g, '')
    .replace(/\s+/g, '_')
    .slice(0, 60) || 'article'
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
```

---

## 13. 正文展示最终推荐写法

```vue
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
        @click="handleExportMarkdown"
      >
        导出 Markdown
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
    <a-textarea
      v-model:value="editableContent"
      placeholder="请输入或编辑正文 Markdown"
      :rows="18"
    />
  </template>
</a-card>
```

---

# 第五部分：测试流程

## 14. 重启前端

如果前端正在运行，按：

```text
Ctrl + C
```

然后执行：

```powershell
npm run dev
```

含义：

```text
启动 Vue 前端开发服务器。
```

---

## 15. 测试历史正文显示

打开：

```text
http://localhost:5173/article/list
```

测试流程：

```text
1. 打开一个之前已经生成过正文的文章任务
2. 进入详情页
3. 不点击重新生成正文
4. 查看“文章正文”卡片
5. 应该直接显示数据库中保存过的历史正文
```

---

## 16. 测试复制全文

测试流程：

```text
1. 打开一个已经生成正文的文章任务
2. 进入详情页
3. 找到“文章正文”卡片
4. 点击“复制全文”
5. 打开记事本 / VSCode / 任意输入框
6. 粘贴
7. 确认内容包含标题和正文
```

预期格式：

```markdown
# 文章标题

正文内容……
```

---

## 17. 测试导出 Markdown

测试流程：

```text
1. 进入有正文的文章详情页
2. 点击“导出 Markdown”
3. 浏览器自动下载 .md 文件
4. 打开下载的 .md 文件
5. 确认内容包含标题和正文
6. 确认中文没有乱码
```

---

## 18. 测试编辑后导出

继续测试：

```text
1. 点击“编辑标题”
2. 修改标题并保存
3. 点击“编辑正文”
4. 修改正文并保存
5. 点击“导出 Markdown”
6. 打开文件
7. 确认导出的是修改后的标题和正文
```

---

# 第六部分：常见问题

## 19. 数据库有正文，但前端不显示

先确认后端详情接口：

```text
GET http://localhost:8123/api/article/{taskId}
```

如果返回里有 `content`，说明后端没问题。

前端重点检查正文卡片是否有主体展示区域：

```vue
<template v-if="!contentEditing">
  <div class="markdown-body" v-html="contentHtml"></div>
</template>
```

不能只有：

```vue
<template #extra>
  ...
</template>
```

否则只会显示按钮，不会显示正文。

---

## 20. 点击复制失败

可能原因：

```text
1. 浏览器不允许剪贴板权限
2. 页面不是 localhost 或 HTTPS
3. 浏览器安全策略限制
```

当前开发环境是 `http://localhost:5173`，一般可以复制成功。如果失败，可以先使用导出 Markdown。

---

## 21. 导出的文件名很奇怪

原因可能是标题包含特殊字符，例如：

```text
/ \ : * ? " < > |
```

所以使用：

```ts
sanitizeFileName
```

来清理文件名。

---

## 22. 导出的 Markdown 中文乱码

确认 Blob 类型是：

```ts
type: 'text/markdown;charset=utf-8'
```

如果仍然乱码，通常是打开文件的软件没有按 UTF-8 识别。用 VSCode 打开一般没问题。

---

## 23. 导出内容不是最新编辑结果

检查保存正文后是否执行了：

```ts
article.value = res.data
```

如果正文编辑后没有保存，导出当然还是旧正文。

---

## 24. 真实流式生成完成后导出为空

可能是 `article.value.content` 还没刷新完成。

Day 8 的 done 事件里应该有：

```ts
await loadArticle()
```

如果没有，真实流式完成后要刷新文章详情。

Day 12 代码也做了兜底：

```ts
article.value?.content || streamContent.value
```

所以只要 `streamContent` 有内容，也能导出。

---

# 第七部分：Day 12 验收标准

Day 12 完成标准：

```text
1. 历史正文可以在详情页直接显示
2. ArticleDetail.vue 新增 buildFinalMarkdown
3. 新增 handleCopyMarkdown
4. 新增 sanitizeFileName
5. 新增 handleExportMarkdown
6. 正文卡片显示“复制全文”
7. 正文卡片显示“导出 Markdown”
8. 点击复制后能粘贴出标题 + 正文
9. 点击导出后能下载 .md 文件
10. .md 文件中文正常显示
11. 编辑标题和正文后，导出的是最新内容
```

---

# 第八部分：Day 12 完成后的项目状态

到 Day 12 后，项目已经从“生成工具”进一步变成“可交付工具”：

```text
AI 生成标题
用户编辑标题
AI 生成大纲
用户编辑大纲
AI 生成正文
用户编辑正文
历史正文展示
复制全文
导出 Markdown
```

这一步对作品集很重要，因为用户最终能把 AI 产物拿出去使用。

---

# Day 13 预告

Day 13 建议做：

```text
Markdown 渲染预览优化
```

虽然现在已经使用 `markdown-it` 展示正文，但还可以继续优化：

```text
1. 标题样式
2. 段落间距
3. 列表样式
4. 代码块样式
5. 引用样式
6. 编辑 / 预览切换
```

这样页面会更像真实 AI 写作工具。
