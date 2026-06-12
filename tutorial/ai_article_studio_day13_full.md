# AI Article Studio：Day 13 完整开发教程

> Day 13 目标：实现 **Markdown 渲染预览优化 + 正文编辑 / 预览切换**，并修复保存正文时前端误报失败的问题。  
> 让文章正文区域更接近真实 AI 写作产品的体验。

---

## 0. Day 13 最终成果

完成 Day 13 后，文章正文区域支持：

```text
1. Markdown 正文以更美观的格式渲染
2. 支持标题、段落、列表、引用、代码块、表格等样式
3. 编辑正文时支持“编辑 / 预览”切换
4. 用户可以边修改 Markdown 原文，边预览最终排版效果
5. 保存后仍然保存 Markdown 原文
6. 修复了“保存正文成功但前端提示保存失败”的问题
```

Day 13 完成后的正文体验：

```text
AI 生成 Markdown 正文
↓
前端渲染成排版后的文章
↓
用户点击编辑正文
↓
进入 Markdown 原文编辑模式
↓
切换到预览模式查看效果
↓
保存最终正文
```

---

## 1. Day 13 要做什么？

Day 13 主要做 4 件事：

```text
1. 使用 markdown-it 渲染正文
2. 优化 Markdown 正文展示样式
3. 编辑正文时增加“编辑 / 预览”切换
4. 修复保存正文后的前端变量名错误
```

主要修改文件：

```text
frontend/src/pages/ArticleDetail.vue
```

后端不需要修改。

---

# 第一部分：确认 markdown-it 依赖

## 2. 安装 markdown-it

如果你已经安装过 `markdown-it`，这一步可以跳过。

进入前端目录：

```powershell
cd D:\Desktop\projects\ai-article-studio\frontend
```

执行：

```powershell
npm install markdown-it
```

命令含义：

```text
安装 markdown-it，用于把 Markdown 文本转换成 HTML。
```

如果安装成功，`package.json` 中会出现：

```json
"markdown-it": "..."
```

---

## 3. 引入 markdown-it

打开：

```text
frontend/src/pages/ArticleDetail.vue
```

在 `<script setup lang="ts">` 顶部加入：

```ts
import MarkdownIt from 'markdown-it'
```

然后创建 MarkdownIt 实例：

```ts
const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})
```

配置含义：

| 配置 | 作用 |
|---|---|
| `html: false` | 不允许直接渲染 HTML，降低安全风险 |
| `linkify: true` | 自动识别链接 |
| `breaks: true` | 普通换行也会显示为换行 |

---

# 第二部分：正文展示逻辑

## 4. 正文展示优先级

在 computed 区域加入：

```ts
const displayContent = computed(() => {
  return article.value?.content || streamContent.value || ''
})
```

作用：

```text
决定当前页面应该展示哪一份正文。
```

优先级是：

```text
article.value.content
↓
streamContent.value
↓
空字符串
```

为什么优先使用 `article.value.content`？

```text
article.value.content 是数据库中已经保存的正式正文；
streamContent.value 是流式生成过程中的临时正文。
```

所以正常展示历史正文时，应该优先展示数据库中的 `content`。

---

## 5. 正文 Markdown 渲染

继续新增：

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
把正式正文 Markdown 转换成 HTML。
```

后面模板里会使用：

```vue
<div class="markdown-body" v-html="contentHtml"></div>
```

---

# 第三部分：正文编辑 / 预览切换

## 6. 新增编辑模式状态

Day 11 已经有：

```ts
const contentEditing = ref(false)
const contentSaving = ref(false)
const editableContent = ref('')
```

Day 13 新增：

```ts
const contentEditMode = ref<'edit' | 'preview'>('edit')
```

含义：

| 值 | 含义 |
|---|---|
| `edit` | 编辑模式，展示 textarea |
| `preview` | 预览模式，展示 Markdown 渲染效果 |

---

## 7. 进入编辑正文时重置为编辑模式

找到：

```ts
const handleStartEditContent = () => {
  editableContent.value = article.value?.content || ''
  contentEditing.value = true
}
```

改成：

```ts
const handleStartEditContent = () => {
  editableContent.value = article.value?.content || streamContent.value || ''
  contentEditMode.value = 'edit'
  contentEditing.value = true
}
```

改动点：

```text
1. 用 article.value.content 作为主来源
2. 用 streamContent.value 兜底
3. 每次进入编辑正文时，默认回到 edit 模式
```

---

## 8. 取消编辑时重置状态

找到：

```ts
const handleCancelEditContent = () => {
  editableContent.value = ''
  contentEditing.value = false
}
```

改成：

```ts
const handleCancelEditContent = () => {
  editableContent.value = ''
  contentEditMode.value = 'edit'
  contentEditing.value = false
}
```

作用：

```text
取消编辑后，下次再次进入编辑正文时，默认仍然是编辑模式。
```

---

## 9. 新增编辑中预览 HTML

在 computed 区域新增：

```ts
const editableContentHtml = computed(() => {
  if (!editableContent.value) {
    return ''
  }

  return markdown.render(editableContent.value)
})
```

作用：

```text
把正在编辑的 Markdown 原文转换成 HTML。
```

区别：

| 变量 | 作用 |
|---|---|
| `contentHtml` | 查看模式下，渲染已保存正文 |
| `editableContentHtml` | 编辑模式下，渲染正在编辑的正文 |

---

## 10. 新增字数统计

在 computed 区域新增：

```ts
const editableContentCount = computed(() => {
  return editableContent.value.length
})
```

作用：

```text
在编辑正文时显示当前正文字符数。
```

这不是核心功能，但可以让页面更像真实写作工具。

---

# 第四部分：修改正文卡片模板

## 11. 正文卡片完整推荐版本

找到正文卡片：

```vue
<a-card v-if="article?.content || streamContent || contentEditing" class="card" title="文章正文">
```

推荐整体替换为：

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
```

---

## 12. 模板逻辑解释

这部分：

```vue
<template v-if="!contentEditing">
  <div class="markdown-body" v-html="contentHtml"></div>
</template>
```

表示：

```text
非编辑状态下，展示已经保存好的 Markdown 渲染结果。
```

这部分：

```vue
<a-radio-group v-model:value="contentEditMode" button-style="solid">
  <a-radio-button value="edit">
    编辑
  </a-radio-button>
  <a-radio-button value="preview">
    预览
  </a-radio-button>
</a-radio-group>
```

表示：

```text
编辑正文时，可以在“编辑”和“预览”之间切换。
```

这部分：

```vue
<a-textarea
  v-if="contentEditMode === 'edit'"
  v-model:value="editableContent"
  placeholder="请输入或编辑正文 Markdown"
  :rows="22"
/>
```

表示：

```text
当前是编辑模式，展示 Markdown 原文输入框。
```

这部分：

```vue
<div
  v-else
  class="markdown-body markdown-preview-box"
  v-html="editableContentHtml"
></div>
```

表示：

```text
当前是预览模式，展示正在编辑内容的 Markdown 渲染效果。
```

---

# 第五部分：优化 Markdown 样式

## 13. Markdown 基础样式

在 `<style scoped>` 中加入或替换：

```css
.markdown-body {
  line-height: 1.85;
  color: #1f2937;
  background: #fff;
  font-size: 15px;
}
```

作用：

```text
控制正文整体字号、颜色和行高。
```

---

## 14. 标题样式

```css
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
```

作用：

```text
让一级标题、二级标题、三级标题有更清晰的层级。
```

---

## 15. 段落、列表、加粗样式

```css
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
```

作用：

```text
优化段落间距、列表缩进和列表项间距。
```

---

## 16. 引用样式

```css
.markdown-body :deep(blockquote) {
  margin: 18px 0;
  padding: 12px 16px;
  color: #4b5563;
  background: #f9fafb;
  border-left: 4px solid #d9d9d9;
}
```

作用：

```text
让 Markdown 引用块看起来更像正式文章里的提示块。
```

---

## 17. 代码样式

行内代码样式：

```css
.markdown-body :deep(code) {
  padding: 2px 6px;
  background: #f3f4f6;
  border-radius: 4px;
  font-size: 13px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
```

代码块样式：

```css
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
```

注意：

```text
pre code 这段很重要。
否则代码块里面的 code 会被行内代码样式覆盖。
```

---

## 18. 链接、分割线、表格样式

```css
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
```

作用：

```text
让链接、分割线和表格也有清晰样式。
```

---

## 19. 编辑 / 预览区域样式

新增：

```css
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

.markdown-preview-box {
  min-height: 420px;
  padding: 20px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
}
```

作用：

| 类名 | 作用 |
|---|---|
| `.content-editor-toolbar` | 编辑 / 预览切换工具栏 |
| `.content-count` | 字符数提示 |
| `.markdown-preview-box` | 编辑模式下的预览容器 |

注意：如果之前已经有 `.content-editor-toolbar`，保留一份即可，不要重复写两遍。

---

# 第六部分：修复保存正文误报失败

## 20. 问题现象

Day 13 测试时发现：

```text
点击保存正文
↓
前端弹出“保存正文失败”
↓
但去数据库看，正文已经真实被修改了
```

Network 里看到：

```text
save-content 状态码是 200
```

说明：

```text
后端接口成功了，数据库也保存成功了。
```

所以问题在前端。

---

## 21. 最终定位

浏览器 Console 报错类似：

```text
ReferenceError: aarticle is not defined
at handleSaveContent
```

原因是 `handleSaveContent` 里写错了变量名：

```ts
aarticle.value = res.data
```

但真实定义的是：

```ts
const article = ref<ArticleVO | null>(null)
```

所以应该写：

```ts
article.value = res.data
```

---

## 22. 修复方式

把 `handleSaveContent` 里的：

```ts
aarticle.value = res.data
contentEditing.value = false
contentEditMode.value = 'edit'
editableContent.value = ''
```

改成：

```ts
article.value = res.data
contentEditing.value = false
contentEditMode.value = 'edit'
editableContent.value = ''
```

完整推荐版本：

```ts
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
```

---

## 23. 为什么数据库保存了，但前端还提示失败？

流程是：

```text
save-content 请求成功
↓
后端更新数据库成功
↓
前端继续执行 aarticle.value = res.data
↓
aarticle 这个变量不存在
↓
触发 ReferenceError
↓
进入 catch
↓
弹出“保存正文失败”
```

所以这不是后端问题，也不是数据库问题，而是：

```text
保存成功后的前端状态更新代码写错变量名。
```

---

# 第七部分：完整测试流程

## 24. 重启前端

如果前端正在运行，按：

```text
Ctrl + C
```

然后执行：

```powershell
npm run dev
```

命令含义：

```text
启动 Vue 前端开发服务器。
```

---

## 25. 测试 Markdown 展示效果

打开：

```text
http://localhost:5173/article/list
```

测试：

```text
1. 打开一个已经有正文的文章任务
2. 进入详情页
3. 查看“文章正文”
4. 正文应该以 Markdown 样式展示
5. h1 / h2 / 列表 / 加粗 / 引用 / 代码块样式应该明显
```

---

## 26. 测试编辑 / 预览切换

测试：

```text
1. 点击“编辑正文”
2. 默认进入“编辑”模式
3. 修改正文内容
4. 点击“预览”
5. 页面展示修改后的 Markdown 渲染效果
6. 再切回“编辑”
7. 内容不丢失
```

---

## 27. 测试保存正文

测试：

```text
1. 编辑正文
2. 切换预览确认效果
3. 点击“保存正文”
4. 前端提示“正文保存成功”
5. 页面退出编辑模式
6. 展示保存后的 Markdown 渲染结果
7. 刷新页面
8. 正文仍然存在，样式正常
```

---

## 28. 测试导出仍然正常

Day 13 修改的是正文渲染，但导出仍然应该导出 Markdown 原文。

测试：

```text
1. 编辑并保存正文
2. 点击“导出 Markdown”
3. 打开 .md 文件
4. 确认导出内容是 Markdown 原文，不是 HTML
```

正确导出：

```markdown
# 标题

正文 Markdown
```

不应该导出：

```html
<h1>标题</h1>
<p>正文</p>
```

---

# 第八部分：常见问题

## 29. 预览没有效果，显示空白

检查是否有：

```ts
const editableContentHtml = computed(() => {
  if (!editableContent.value) {
    return ''
  }

  return markdown.render(editableContent.value)
})
```

以及模板里是否写了：

```vue
<div
  v-else
  class="markdown-body markdown-preview-box"
  v-html="editableContentHtml"
></div>
```

---

## 30. 正文查看模式没有内容

检查 `displayContent` 是否是：

```ts
const displayContent = computed(() => {
  return article.value?.content || streamContent.value || ''
})
```

以及正文卡片中是否有：

```vue
<div class="markdown-body" v-html="contentHtml"></div>
```

---

## 31. 样式没生效

因为使用了 `v-html`，而 `<style scoped>` 默认不会直接影响动态渲染出来的 HTML，所以必须用：

```css
.markdown-body :deep(h1) {
  ...
}
```

不要写成：

```css
.markdown-body h1 {
  ...
}
```

在 scoped 样式里，后者可能不生效。

---

## 32. 代码块样式很奇怪

确认有这两段：

```css
.markdown-body :deep(pre) {
  margin: 18px 0;
  padding: 14px 16px;
  overflow: auto;
  background: #111827;
  color: #f9fafb;
  border-radius: 8px;
  line-height: 1.7;
}
```

以及：

```css
.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
  border-radius: 0;
}
```

第一段控制代码块整体，第二段避免代码块里的 `code` 被行内代码样式覆盖。

---

## 33. 保存正文成功但提示失败

重点看控制台是否有：

```text
ReferenceError
```

如果有，说明接口可能已经成功了，但前端后续代码报错。

这次实际原因是：

```ts
aarticle.value = res.data
```

变量名写错，应该改成：

```ts
article.value = res.data
```

---

## 34. v-html 是否有安全风险？

有潜在风险。

但当前创建 MarkdownIt 时用了：

```ts
html: false
```

这表示不允许 Markdown 中的原始 HTML 被直接渲染。

所以当前风险较低。

后续如果允许用户输入 HTML，建议再加 HTML sanitize，例如：

```text
DOMPurify
```

Day 13 暂时不做。

---

# 第九部分：Day 13 验收标准

Day 13 完成标准：

```text
1. 正文查看模式使用 markdown-it 渲染
2. Markdown 标题样式更清晰
3. 段落、列表、引用、代码块、表格样式正常
4. 编辑正文时支持“编辑 / 预览”切换
5. 预览显示的是当前正在编辑的内容
6. 切换编辑 / 预览时内容不丢失
7. 保存正文后退出编辑模式
8. 保存正文成功后不再误报失败
9. 刷新页面后正文仍正常显示
10. 导出 Markdown 仍然导出原始 Markdown 文本
```

---

# 第十部分：Day 13 完成后的项目状态

到 Day 13 后，你的项目已经具备比较完整的 AI 写作产品体验：

```text
AI 生成标题
用户编辑标题
AI 生成大纲
用户编辑大纲
AI 生成正文
Markdown 渲染预览
用户编辑正文
编辑 / 预览切换
复制全文
导出 Markdown
```

这已经非常适合作为秋招项目展示。

---

# Day 14 预告

Day 14 建议做：

```text
文章版本历史
```

因为现在用户可以编辑正文，但没有版本记录。

Day 14 可以实现：

```text
1. 每次保存正文时生成一个版本
2. 查看历史版本
3. 恢复某个历史版本
4. 区分 AI 生成版本和用户编辑版本
```

这样项目会更接近真实生产级内容工具。
