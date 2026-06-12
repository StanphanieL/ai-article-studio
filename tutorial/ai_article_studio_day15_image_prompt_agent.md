# AI Article Studio：Day 15 完整开发教程

> Day 15 目标：实现 **图片生成 Agent / 配图提示词生成 Agent**。  
> 让项目从“只生成文章文字”升级为“能基于文章内容生成配图提示词”，为后续图文合成做准备。

---

## 0. Day 15 最终成果

完成 Day 15 后，项目支持：

```text
1. 基于文章标题 + 大纲 + 正文生成配图提示词
2. 一次生成 3 个图片提示词方案
3. 每个方案包含图片标题、使用场景、中文提示词、英文提示词
4. 生成结果保存到数据库 article.image_prompts 字段
5. 前端能展示配图提示词方案
6. 中文 / 英文提示词可以一键复制
7. Agent 执行日志能记录 ImagePromptAgent 的 Prompt、输出和错误
8. 失败时能写入 FAILED 状态和 errorMessage
9. 成功后会清空旧 errorMessage
```

---

# 第一部分：数据库修改

## 1. 给 article 表新增 image_prompts 字段

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

命令含义：进入 MySQL 容器，并使用 utf8mb4 字符集，避免中文乱码。

```sql
SET NAMES utf8mb4;
USE ai_article_studio;

ALTER TABLE article
ADD COLUMN image_prompts MEDIUMTEXT NULL COMMENT '图片提示词方案 JSON'
AFTER content;
```

## 2. 检查字段

```sql
DESC article;
```

确认能看到：

```text
image_prompts
```

---

# 第二部分：后端实体和 VO 修改

## 3. 修改 Article.java

```text
src/main/java/com/nana/aiarticlestudio/model/entity/Article.java
```

新增字段：

```java
private String imagePrompts;
```

## 4. 修改 ArticleVO.java

```text
src/main/java/com/nana/aiarticlestudio/model/vo/ArticleVO.java
```

新增字段：

```java
private String imagePrompts;
```

---

# 第三部分：新增图片提示词数据结构

## 5. 新增 ImagePromptOption.java

创建：

```text
src/main/java/com/nana/aiarticlestudio/model/vo/ImagePromptOption.java
```

写入：

```java
package com.nana.aiarticlestudio.model.vo;

import lombok.Data;

@Data
public class ImagePromptOption {

    private String imageTitle;

    private String usageScene;

    private String promptZh;

    private String promptEn;
}
```

---

# 第四部分：ArticleMapper 修改

## 6. 新增 updateImagePrompts 方法

```java
@Update("""
        UPDATE article
        SET image_prompts = #{imagePrompts},
            phase = #{phase},
            status = #{status},
            error_message = NULL
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateImagePrompts(@Param("taskId") String taskId,
                       @Param("imagePrompts") String imagePrompts,
                       @Param("phase") String phase,
                       @Param("status") String status);
```

作用：保存图片提示词方案 JSON，并在成功时清空旧 `error_message`。

---

# 第五部分：新增 ImagePromptAgent

## 7. 创建 ImagePromptAgent.java

创建：

```text
src/main/java/com/nana/aiarticlestudio/agent/ImagePromptAgent.java
```

你的项目当前大模型客户端是：

```java
private final LlmClient llmClient;
```

所以 `ImagePromptAgent` 写成：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ImagePromptAgent {

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper;

    public String buildPrompt(String topic,
                              String selectedTitle,
                              String outline,
                              String content,
                              String style) {
        return """
                你是一名专业的 AI 图像提示词策划专家，擅长根据文章内容设计配图方案。

                请根据下面文章信息，生成 3 个图片提示词方案。

                用户选题：%s
                已选标题：%s
                文章风格：%s
                文章大纲：%s
                文章正文：%s

                输出要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 不要输出 <think>、</think> 或任何思考过程
                6. 数组中必须有 3 个对象
                7. 每个对象必须包含 imageTitle、usageScene、promptZh、promptEn
                8. promptZh 用中文，适合用户理解
                9. promptEn 用英文，适合直接给图片生成模型使用
                10. 图片风格要和文章风格匹配
                11. 不要生成低俗、暴力、违法内容

                字段说明：
                - imageTitle：图片方案标题
                - usageScene：图片使用场景，例如“文章封面”“正文配图”“小红书封面”
                - promptZh：中文图片提示词
                - promptEn：英文图片提示词

                示例格式：
                [
                  {
                    "imageTitle": "科技感效率封面",
                    "usageScene": "文章封面",
                    "promptZh": "一张现代科技感封面图，表现 AI 工具提升工作效率，画面干净，适合文章封面",
                    "promptEn": "A modern technology-style cover image showing AI tools improving productivity, clean composition, suitable for an article cover"
                  }
                ]

                请直接输出 JSON 数组。
                """.formatted(topic, selectedTitle, style, outline, content);
    }

    public String callRaw(String prompt) {
        return llmClient.chat(prompt);
    }

    public String clean(String response) {
        if (response == null) {
            return "";
        }

        return response
                .replaceAll("(?s)<think>.*?</think>", "")
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    public List<ImagePromptOption> parse(String response) throws Exception {
        String cleaned = clean(response);

        int start = cleaned.indexOf("[");
        int end = cleaned.lastIndexOf("]");

        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("模型返回中未找到 JSON 数组：" + response);
        }

        String jsonArray = cleaned.substring(start, end + 1);

        return objectMapper.readValue(
                jsonArray,
                new TypeReference<List<ImagePromptOption>>() {}
        );
    }

    public List<ImagePromptOption> generate(String topic,
                                            String selectedTitle,
                                            String outline,
                                            String content,
                                            String style) throws Exception {
        String prompt = buildPrompt(topic, selectedTitle, outline, content, style);
        String response = callRaw(prompt);
        return parse(response);
    }
}
```

---

# 第六部分：Service 层修改

## 8. 修改 ArticleService.java

新增方法：

```java
ArticleVO generateImagePrompts(String taskId);
```

## 9. 修改 ArticleServiceImpl.java

新增 import：

```java
import com.nana.aiarticlestudio.agent.ImagePromptAgent;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
```

新增字段：

```java
private final ImagePromptAgent imagePromptAgent;
```

新增方法：

```java
@Override
public ArticleVO generateImagePrompts(String taskId) {
    long start = System.currentTimeMillis();
    String prompt = null;
    String response = null;

    try {
        Article article = articleMapper.selectByTaskId(taskId);
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        if (!StringUtils.hasText(article.getSelectedTitle())) {
            throw new RuntimeException("请先确认标题");
        }

        if (!StringUtils.hasText(article.getContent())) {
            throw new RuntimeException("请先生成正文");
        }

        prompt = imagePromptAgent.buildPrompt(
                article.getTopic(),
                article.getSelectedTitle(),
                article.getOutline(),
                article.getContent(),
                article.getStyle()
        );

        response = imagePromptAgent.callRaw(prompt);

        List<ImagePromptOption> imagePrompts = imagePromptAgent.parse(response);
        String imagePromptsJson = objectMapper.writeValueAsString(imagePrompts);

        int result = articleMapper.updateImagePrompts(
                taskId,
                imagePromptsJson,
                ArticlePhase.CONTENT_GENERATION.name(),
                ArticleStatus.SUCCESS.name()
        );

        if (result != 1) {
            throw new RuntimeException("保存图片提示词失败");
        }

        long costMs = System.currentTimeMillis() - start;

        agentLogService.saveSuccess(
                taskId,
                "ImagePromptAgent",
                prompt,
                response,
                costMs
        );

        return getByTaskId(taskId);
    } catch (Exception e) {
        long costMs = System.currentTimeMillis() - start;

        agentLogService.saveFailed(
                taskId,
                "ImagePromptAgent",
                prompt,
                e.getMessage(),
                costMs
        );

        articleMapper.updateFailedStatus(
                taskId,
                ArticlePhase.CONTENT_GENERATION.name(),
                ArticleStatus.FAILED.name(),
                e.getMessage()
        );

        throw new RuntimeException("生成图片提示词失败：" + e.getMessage());
    }
}
```

注意：`saveFailed` 的参数顺序必须是：

```java
saveFailed(taskId, agentName, inputText, errorMessage, costMs)
```

---

# 第七部分：Controller 修改

## 10. 修改 ArticleController.java

新增接口：

```java
@PostMapping("/generate-image-prompts/{taskId}")
public BaseResponse<ArticleVO> generateImagePrompts(@PathVariable String taskId) {
    return BaseResponse.success(articleService.generateImagePrompts(taskId));
}
```

---

# 第八部分：前端 API 修改

## 11. 修改 article.ts

在 `ArticleVO` 中新增字段：

```ts
imagePrompts?: string
```

新增类型：

```ts
export interface ImagePromptOption {
  imageTitle: string
  usageScene: string
  promptZh: string
  promptEn: string
}
```

新增接口方法：

```ts
export const generateImagePrompts = async (taskId: string) => {
  const res = await request.post<BaseResponse<ArticleVO>>(`/api/article/generate-image-prompts/${taskId}`)
  return res.data
}
```

---

# 第九部分：前端页面修改

## 12. ArticleDetail.vue 新增 import

```ts
generateImagePrompts,
type ImagePromptOption,
```

## 13. 新增 loading 状态

```ts
const imagePromptLoading = ref(false)
```

## 14. 解析图片提示词 JSON

```ts
const imagePromptOptions = computed(() => {
  return parseJsonArray<ImagePromptOption>(article.value?.imagePrompts)
})
```

## 15. 新增生成图片提示词方法

```ts
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
```

## 16. 顶部按钮加入“生成配图提示词”

```vue
<a-button
  v-if="article?.content || streamContent"
  :loading="imagePromptLoading"
  @click="handleGenerateImagePrompts"
>
  生成配图提示词
</a-button>
```

## 17. 新增图片提示词展示卡片

建议放在“文章正文”卡片下面，“Agent 执行日志”上面：

```vue
<a-card
  v-if="imagePromptOptions.length > 0"
  class="card"
  title="配图提示词方案"
>
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
```

## 18. 新增样式

```css
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
```

---

# 第十部分：测试流程

## 19. 重启后端和前端

```powershell
npm run dev
```

## 20. 测试生成配图提示词

```text
1. 打开一篇已经有正文的文章
2. 点击“生成配图提示词”
3. 等待模型返回
4. 页面出现“配图提示词方案”
5. 应该有 3 个方案
6. 每个方案有 imageTitle、usageScene、promptZh、promptEn
```

## 21. 检查数据库

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

```sql
SET NAMES utf8mb4;
USE ai_article_studio;

SELECT
  task_id,
  LEFT(image_prompts, 500) AS image_prompts_preview
FROM article
ORDER BY update_time DESC
LIMIT 3;
```

## 22. 检查 Agent 日志

在前端展开 Agent 执行日志，应该能看到：

```text
ImagePromptAgent
```

---

# 第十一部分：常见问题

## 23. ArticleServiceImpl 编译报 saveFailed 参数错误

正确：

```java
agentLogService.saveFailed(
        taskId,
        "ImagePromptAgent",
        prompt,
        e.getMessage(),
        costMs
);
```

错误：

```java
agentLogService.saveFailed(
        taskId,
        "ImagePromptAgent",
        prompt,
        costMs,
        e.getMessage()
);
```

## 24. 前端没有显示图片提示词

检查：

```text
1. 数据库是否有 image_prompts 字段
2. Article.java 是否新增 imagePrompts
3. ArticleVO.java 是否新增 imagePrompts
4. 前端 ArticleVO 是否新增 imagePrompts?: string
5. imagePromptOptions 是否解析 article.value?.imagePrompts
```

---

# Day 15 验收标准

```text
1. article 表新增 image_prompts 字段
2. Article.java 新增 imagePrompts
3. ArticleVO.java 新增 imagePrompts
4. 前端 ArticleVO 新增 imagePrompts?: string
5. 新增 ImagePromptOption
6. 新增 ImagePromptAgent
7. ArticleMapper 新增 updateImagePrompts
8. ArticleService 新增 generateImagePrompts
9. ArticleServiceImpl 实现 generateImagePrompts
10. ArticleController 新增接口
11. 前端 article.ts 新增类型和接口
12. ArticleDetail.vue 新增生成按钮
13. 前端能展示 3 个配图提示词方案
14. 中文 / 英文提示词可复制
15. Agent 日志能看到 ImagePromptAgent
16. 失败时能写 FAILED 状态和错误信息
17. 成功后能清空旧 errorMessage
```
