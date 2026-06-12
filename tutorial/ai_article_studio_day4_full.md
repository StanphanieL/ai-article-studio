# AI Article Studio：Day 4 完整开发教程

> Day 4 目标：在 Day 3「标题生成 + 大纲生成」基础上，新增 **正文生成 Agent**，让系统可以根据文章选题、已选标题和大纲生成 Markdown 正文，并在前端详情页展示出来。  
> Day 4 仍然先使用 `MockLlmClient` 模拟大模型返回，先把流程跑通。

---

## 0. Day 4 最终成果

完成 Day 4 后，详情页支持：

```text
1. 查看文章基础信息
2. 生成标题候选
3. 选择标题并生成大纲
4. 点击生成正文
5. 展示 Markdown 正文内容
```

完整流程：

```text
创建文章任务
↓
进入详情页
↓
点击生成标题
↓
选择一个标题
↓
点击确认标题并生成大纲
↓
点击生成正文
↓
展示文章正文
```

新增后端接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/generate-content/{taskId}` | POST | 根据大纲生成文章正文 |

新增后端类：

| 类 | 作用 |
|---|---|
| `ContentGeneratorAgent` | 正文生成 Agent |

本日修改文件：

```text
MockLlmClient.java
ContentGeneratorAgent.java
ArticleMapper.java
ArticleService.java
ArticleServiceImpl.java
ArticleController.java
frontend/src/api/article.ts
frontend/src/pages/ArticleDetail.vue
```

---

# 第一部分：后端开发

---

## 1. 修改 `MockLlmClient`

打开：

```text
src/main/java/com/nana/aiarticlestudio/agent/MockLlmClient.java
```

在原来的两个 `if` 后面、`return "[]"` 前面，新增：

```java
if (prompt.contains("生成文章正文")) {
    return """
            # AI 产品经理如何用工具提升 10 倍效率

            在 AI 工具快速发展的今天，产品经理的工作方式正在发生明显变化。过去需要大量时间完成的资料整理、需求分析、文档撰写和会议总结，现在都可以借助 AI 工具进行加速。

            ## 一、为什么 AI 产品经理需要系统化提效

            AI 产品经理的工作往往同时包含需求分析、用户研究、竞品调研、原型设计、PRD 撰写、数据分析和跨团队沟通。任务类型多、信息密度高，如果缺少系统化方法，很容易被碎片任务消耗。

            系统化提效的关键，不是简单地把所有工作都交给 AI，而是把 AI 放到合适的位置，让它承担信息整理、初稿生成、结构化总结等高频重复任务。

            ## 二、用 AI 辅助需求分析

            在需求分析阶段，AI 可以帮助产品经理快速梳理用户痛点、业务目标和功能优先级。

            例如，当你收集到一批用户反馈后，可以让 AI 帮你完成三件事：

            1. 提取高频问题；
            2. 按用户场景进行分类；
            3. 初步判断哪些问题值得进入需求池。

            ## 三、用 AI 提升原型和文档效率

            PRD 和原型说明是产品经理日常工作中的高频任务。AI 可以帮助生成页面说明、用户故事、异常状态、按钮文案和交互说明。

            但需要注意的是，AI 生成的内容通常只能作为初稿。真正的业务规则、边界条件和优先级判断，仍然需要产品经理自己确认。

            ## 四、用 AI 做会议总结和任务追踪

            会议结束后，AI 可以将会议内容整理成纪要、行动项、负责人和截止时间。这对于跨团队协作尤其有价值。

            一个好的会议总结不只是记录“大家说了什么”，更重要的是明确：

            - 谁负责；
            - 什么时候完成；
            - 当前阻塞是什么；
            - 下一步需要谁决策。

            ## 五、如何避免过度依赖 AI

            AI 可以提高效率，但不能替代产品判断。产品经理仍然需要理解用户、理解业务、理解团队资源，并对最终方案负责。

            ## 总结

            AI 产品经理的提效核心，不是掌握多少工具，而是知道哪些工作适合交给 AI，哪些工作必须由自己判断。只有把 AI 放进稳定的工作流中，才能真正提升效率，而不是制造更多混乱。
            """;
}
```

作用：

```text
当 ContentGeneratorAgent 要生成正文时，MockLlmClient 返回一篇 Markdown 格式的文章。
```

现在 `MockLlmClient` 支持三类模拟返回：

```text
1. 生成 3 个标题 → 返回 JSON 标题数组
2. 生成文章大纲 → 返回 JSON 大纲数组
3. 生成文章正文 → 返回 Markdown 正文
```

---

## 2. 创建 `ContentGeneratorAgent`

在：

```text
src/main/java/com/nana/aiarticlestudio/agent
```

新建：

```text
ContentGeneratorAgent.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentGeneratorAgent {

    private final LlmClient llmClient;

    public String generate(String topic, String selectedTitle, String outline, String style) {
        try {
            String prompt = """
                    你是一个专业的长文写作专家。
                    请根据用户选题、已选标题和文章大纲，生成一篇结构清晰的 Markdown 正文。

                    用户选题：%s
                    已选标题：%s
                    文章风格：%s
                    文章大纲：%s

                    要求：
                    1. 使用 Markdown 格式
                    2. 包含一级标题、二级标题、段落和列表
                    3. 内容要围绕大纲展开
                    4. 语言清晰，适合普通读者阅读
                    5. 不要输出任何额外解释

                    请生成文章正文。
                    """.formatted(topic, selectedTitle, style, outline);

            return llmClient.chat(prompt);
        } catch (Exception e) {
            throw new RuntimeException("生成正文失败：" + e.getMessage());
        }
    }
}
```

作用：

```text
ContentGeneratorAgent 根据选题、已选标题、大纲和风格生成文章正文。
```

和 Day 3 两个 Agent 的区别：

```text
TitleGeneratorAgent：返回 JSON，需要解析成标题列表。
OutlineGeneratorAgent：返回 JSON，需要解析成大纲列表。
ContentGeneratorAgent：返回 Markdown 字符串，不需要 JSON 解析。
```

---

## 3. 修改 `ArticleMapper`

打开：

```text
src/main/java/com/nana/aiarticlestudio/mapper/ArticleMapper.java
```

新增方法：

```java
@Update("""
        UPDATE article
        SET content = #{content},
            phase = #{phase},
            status = #{status}
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateContent(@Param("taskId") String taskId,
                  @Param("content") String content,
                  @Param("phase") String phase,
                  @Param("status") String status);
```

作用：

```text
把生成出来的 Markdown 正文保存到 article.content 字段。
同时更新 phase 和 status。
```

最终 `ArticleMapper` 应该包含：

```text
insert
selectByTaskId
list
count
deleteByTaskId
updateTitleOptions
updateSelectedTitleAndOutline
updateContent
```

---

## 4. 修改 `ArticleService`

打开：

```text
src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

新增方法：

```java
ArticleVO generateContent(String taskId);
```

完整结构类似：

```java
public interface ArticleService {

    String createArticle(ArticleCreateRequest request);

    ArticleVO getByTaskId(String taskId);

    PageResult<ArticleVO> listArticles(ArticleListRequest request);

    Boolean deleteByTaskId(String taskId);

    ArticleVO generateTitles(String taskId);

    ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request);

    ArticleVO generateContent(String taskId);
}
```

作用：

```text
给 Controller 暴露一个“生成正文”的业务方法。
```

---

## 5. 修改 `ArticleServiceImpl`

打开：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/ArticleServiceImpl.java
```

新增 import：

```java
import com.nana.aiarticlestudio.agent.ContentGeneratorAgent;
```

在类字段中新增：

```java
private final ContentGeneratorAgent contentGeneratorAgent;
```

因为类上用了：

```java
@RequiredArgsConstructor
```

所以 Spring 会自动通过构造方法注入 `ContentGeneratorAgent`。

在类末尾新增：

```java
@Override
public ArticleVO generateContent(String taskId) {
    try {
        Article article = articleMapper.selectByTaskId(taskId);
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        if (!StringUtils.hasText(article.getSelectedTitle())) {
            throw new RuntimeException("请先确认标题");
        }

        if (!StringUtils.hasText(article.getOutline())) {
            throw new RuntimeException("请先生成大纲");
        }

        String content = contentGeneratorAgent.generate(
                article.getTopic(),
                article.getSelectedTitle(),
                article.getOutline(),
                article.getStyle()
        );

        int result = articleMapper.updateContent(
                taskId,
                content,
                ArticlePhase.CONTENT_GENERATION.name(),
                ArticleStatus.SUCCESS.name()
        );

        if (result != 1) {
            throw new RuntimeException("保存正文失败");
        }

        return getByTaskId(taskId);
    } catch (Exception e) {
        throw new RuntimeException("生成正文失败：" + e.getMessage());
    }
}
```

作用：

```text
1. 查询文章任务
2. 检查是否已经选择标题
3. 检查是否已经生成大纲
4. 调用 ContentGeneratorAgent 生成正文
5. 保存正文到 article.content
6. 更新 phase 和 status
7. 返回最新文章详情
```

为什么要检查标题和大纲？

```text
正文生成必须依赖标题和大纲。
如果用户还没完成前面的步骤就直接生成正文，流程会乱。
```

常见报错：

```text
Class 'ArticleServiceImpl' must either be declared abstract or implement abstract method 'generateTitles(String)' in 'ArticleService'
```

原因：

```text
ArticleServiceImpl 没有完整实现 ArticleService 接口。
```

需要确认 `ArticleServiceImpl` 里有这些方法：

```text
createArticle
getByTaskId
listArticles
deleteByTaskId
generateTitles
confirmTitleAndGenerateOutline
generateContent
```

如果缺少 `generateTitles` 或 `confirmTitleAndGenerateOutline`，补回 Day 3 的方法。

---

## 6. 修改 `ArticleController`

打开：

```text
src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增接口：

```java
@PostMapping("/generate-content/{taskId}")
public BaseResponse<ArticleVO> generateContent(@PathVariable String taskId) {
    return BaseResponse.success(articleService.generateContent(taskId));
}
```

作用：

```text
前端点击“生成正文”时，请求这个接口。
后端根据 taskId 找到文章任务，并生成正文。
```

最终 `ArticleController` 应包含：

```text
POST /api/article/create
GET  /api/article/{taskId}
POST /api/article/list
POST /api/article/delete/{taskId}
POST /api/article/generate-titles/{taskId}
POST /api/article/confirm-title
POST /api/article/generate-content/{taskId}
```

---

## 7. 重启后端

在 IDEA 底部 `Run` 窗口点击红色方块停止项目。

然后重新运行：

```text
AiArticleStudioApplication
```

看到：

```text
Tomcat started on port 8123
Started AiArticleStudioApplication
```

说明启动成功。

---

# 第二部分：后端接口测试

---

## 8. 创建任务

打开新的 PowerShell：

```powershell
$createRes = Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/create" -ContentType "application/json" -Body '{"topic":"AI Product Manager Efficiency","style":"TECH"}'
$taskId = $createRes.data
$taskId
```

含义：

```text
创建一条文章任务，并保存 taskId。
这里用英文，是为了避免 PowerShell 中文编码问题。
```

---

## 9. 生成标题

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/generate-titles/$taskId"
```

含义：

```text
调用标题生成接口。
```

---

## 10. 确认标题并生成大纲

```powershell
$body = @{
  taskId = $taskId
  selectedTitle = "AI 产品经理如何用工具提升 10 倍效率"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/confirm-title" -ContentType "application/json" -Body $body
```

含义：

```text
模拟用户选择一个标题，并让后端生成大纲。
```

---

## 11. 生成正文

```powershell
$contentRes = Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/generate-content/$taskId"
$contentRes.data.content
```

含义：

```text
调用正文生成接口，并输出生成后的 Markdown 正文。
```

如果能看到一篇 Markdown 正文，说明 Day 4 后端完成。

注意：

```text
PowerShell 传中文可能有编码问题。
中文主流程建议用前端页面测试。
```

---

# 第三部分：前端开发

---

## 12. 安装 Markdown 渲染库

进入前端目录：

```powershell
cd D:\Desktop\projects\ai-article-studio\frontend
```

执行：

```powershell
npm install markdown-it
```

含义：

```text
安装 markdown-it，用于把 Markdown 字符串渲染成 HTML。
```

再执行：

```powershell
npm install -D @types/markdown-it
```

含义：

```text
安装 markdown-it 的 TypeScript 类型声明。
-D 表示这是开发依赖，只在开发和类型检查时使用。
```

---

## 13. 修改前端 `article.ts`

打开：

```text
frontend/src/api/article.ts
```

在文件末尾新增：

```ts
export const generateContent = async (taskId: string) => {
  const res = await request.post<BaseResponse<ArticleVO>>(`/api/article/generate-content/${taskId}`)
  return res.data
}
```

作用：

```text
封装“生成正文”接口。
前端页面只需要调用 generateContent(taskId)。
```

---

## 14. 修改 `ArticleDetail.vue`

打开：

```text
frontend/src/pages/ArticleDetail.vue
```

### 14.1 修改 import

新增：

```ts
import MarkdownIt from 'markdown-it'
```

并在 `../api/article` 的导入中加入 `generateContent`：

```ts
import {
  confirmTitle,
  generateContent,
  generateTitles,
  getArticle,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
} from '../api/article'
```

---

### 14.2 创建 Markdown 渲染器

在变量定义附近新增：

```ts
const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})
```

作用：

```text
创建 Markdown 渲染器。
html: false：不允许 Markdown 中直接插入 HTML，安全一点。
linkify: true：自动识别链接。
breaks: true：换行更符合普通文本习惯。
```

---

### 14.3 新增正文 HTML 计算属性

在 `outlineItems` 下面新增：

```ts
const contentHtml = computed(() => {
  if (!article.value?.content) {
    return ''
  }
  return markdown.render(article.value.content)
})
```

作用：

```text
把 article.content 里的 Markdown 字符串转换成 HTML。
```

---

### 14.4 新增生成正文方法

在 `handleConfirmTitle` 方法后面新增：

```ts
const handleGenerateContent = async () => {
  if (!taskId.value) {
    return
  }

  if (!article.value?.outline) {
    message.warning('请先生成大纲')
    return
  }

  loading.value = true
  try {
    const res = await generateContent(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    article.value = res.data
    message.success('正文生成成功')
  } catch (error) {
    console.error(error)
    message.error('生成正文失败')
  } finally {
    loading.value = false
  }
}
```

作用：

```text
点击按钮后调用后端生成正文接口。
成功后更新 article，页面自动展示正文。
```

---

### 14.5 新增“生成正文”按钮

找到按钮区域：

```vue
<a-space>
  <a-button @click="goBack">返回列表</a-button>
  <a-button type="primary" :loading="loading" @click="handleGenerateTitles">
    生成标题
  </a-button>
</a-space>
```

改成：

```vue
<a-space>
  <a-button @click="goBack">返回列表</a-button>

  <a-button type="primary" :loading="loading" @click="handleGenerateTitles">
    生成标题
  </a-button>

  <a-button :loading="loading" @click="handleGenerateContent">
    生成正文
  </a-button>
</a-space>
```

作用：

```text
增加一个“生成正文”按钮。
```

---

### 14.6 新增正文卡片

在文章大纲卡片下面新增：

```vue
<a-card v-if="article?.content" class="card" title="文章正文">
  <div class="markdown-body" v-html="contentHtml"></div>
</a-card>
```

作用：

```text
如果 article.content 有内容，就把 Markdown 正文渲染出来。
```

---

### 14.7 增加 Markdown 样式

在 `<style scoped>` 末尾新增：

```css
.markdown-body {
  line-height: 1.8;
  color: #222;
  background: #fff;
}

.markdown-body :deep(h1) {
  font-size: 28px;
  margin-bottom: 20px;
}

.markdown-body :deep(h2) {
  font-size: 22px;
  margin-top: 28px;
  margin-bottom: 12px;
}

.markdown-body :deep(p) {
  margin-bottom: 12px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin-bottom: 12px;
}
```

作用：

```text
让 Markdown 正文更像一篇文章，而不是一大段普通文本。
```

---

# 第四部分：完整测试流程

---

## 15. 重启前端

如果前端正在运行，先按：

```text
Ctrl + C
```

然后执行：

```powershell
npm run dev
```

含义：

```text
重新启动前端开发服务器。
```

---

## 16. 确认后端正常

浏览器访问：

```text
http://localhost:8123/api/health
```

看到：

```text
ok
```

说明后端正常。

---

## 17. 从前端完整测试

打开：

```text
http://localhost:5173/article/list
```

测试流程：

```text
1. 创建一条文章任务
2. 点击查看详情
3. 点击生成标题
4. 选择一个标题
5. 点击确认标题并生成大纲
6. 点击生成正文
7. 页面出现文章正文
```

成功后，详情页应该看到：

```text
标题候选
文章大纲
文章正文
```

文章状态应该变成：

```text
phase: CONTENT_GENERATION
status: SUCCESS
```

---

# 第五部分：常见问题

---

## 18. 点击生成正文提示“请先生成大纲”

原因：

```text
当前文章任务还没有 outline。
```

解决：

```text
先点击生成标题
选择标题
点击确认标题并生成大纲
然后再点击生成正文
```

---

## 19. 后端报错找不到 `generateContent`

原因可能是：

```text
1. ArticleService 没有新增 generateContent 方法
2. ArticleServiceImpl 没有实现 generateContent 方法
3. ArticleController 没有新增接口
4. 没有重启后端
```

检查：

```text
ArticleService.java
ArticleServiceImpl.java
ArticleController.java
```

---

## 20. 后端报错找不到 `updateContent`

原因：

```text
ArticleMapper 没有新增 updateContent 方法。
```

检查：

```text
ArticleMapper.java
```

确认有：

```java
int updateContent(...)
```

---

## 21. 前端报错找不到 `markdown-it`

原因：

```text
没有安装 markdown-it。
```

执行：

```powershell
npm install markdown-it
npm install -D @types/markdown-it
```

含义：

```text
安装 Markdown 渲染库和 TypeScript 类型声明。
```

然后重启前端。

---

## 22. 正文展示出来但样式不好看

这是正常的。Day 4 只做基础 Markdown 展示。

后面可以继续优化：

```text
1. 增加文章预览布局
2. 增加复制正文按钮
3. 增加导出 Markdown
4. 增加编辑正文能力
```

---

# 第六部分：Day 4 验收标准

Day 4 完成标准：

```text
1. 后端新增 ContentGeneratorAgent
2. MockLlmClient 能返回 Markdown 正文
3. ArticleMapper 能保存 content
4. ArticleService 能根据 taskId 生成正文
5. ArticleController 暴露 generate-content 接口
6. 前端 article.ts 封装 generateContent
7. 详情页出现“生成正文”按钮
8. 点击后能显示 Markdown 正文
9. article 表中的 content 字段有正文内容
```

---

# 第七部分：Day 5 预告

Day 5 会做：

```text
1. SSE 流式输出
2. 生成过程实时展示
3. 任务进度事件
4. 前端实时接收后端推送
```

这样项目会更像真实 AI 应用，而不是点击按钮后等待接口一次性返回。
