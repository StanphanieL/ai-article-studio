# AI Article Studio：原计划 Day 14 完整开发教程

> Day 14 目标：实现 **错误处理与任务状态优化**。  
> 让项目从“失败后只弹提示”升级为“失败状态可持久化、错误信息可见、用户可以重试”。

---

## 0. Day 14 最终成果

完成 Day 14 后，项目支持：

```text
1. 文章任务失败时，数据库能记录 FAILED 状态
2. 文章详情页能展示 errorMessage
3. 标题生成失败 / 大纲生成失败 / 正文生成失败时，任务状态更准确
4. 前端能展示清晰的失败提示
5. 前端能根据失败阶段显示重试按钮
6. 用户可以点击重试，不需要重新创建任务
7. 重试成功后，旧错误信息会自动清空
```

修改文件：

```text
后端：
- ArticleMapper.java
- ArticleServiceImpl.java

前端：
- ArticleDetail.vue
```

---

## 1. Day 14 要解决的问题

在 Day 14 之前，如果模型调用失败、JSON 解析失败、正文生成失败，通常只是前端弹出“生成失败”，Agent 日志里有错误，但文章任务本身可能仍然显示：

```text
status = SUCCESS
phase = TITLE_SELECTION / OUTLINE_EDITING / CONTENT_GENERATION
errorMessage = 空
```

这会导致：

```text
1. 用户不知道当前任务到底失败在哪一步
2. 页面刷新后失败提示可能消失
3. 不知道应该重试标题、大纲还是正文
4. 数据库状态不准确
5. 项目演示时不够像真实产品
```

Day 14 的核心思路：

```text
后端失败时写入 FAILED 状态和 error_message
前端读取 FAILED 状态并展示失败原因
前端根据失败 phase 显示对应重试按钮
成功后清空旧 error_message
```

---

# 第一部分：后端修改

## 2. ArticleMapper 新增失败状态更新方法

打开：

```text
src/main/java/com/nana/aiarticlestudio/mapper/ArticleMapper.java
```

新增方法：

```java
@Update("""
        UPDATE article
        SET phase = #{phase},
            status = #{status},
            error_message = #{errorMessage}
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateFailedStatus(@Param("taskId") String taskId,
                       @Param("phase") String phase,
                       @Param("status") String status,
                       @Param("errorMessage") String errorMessage);
```

作用：当某个阶段失败时，把失败阶段、FAILED 状态、错误信息写入 `article` 表。

---

## 3. 成功更新时清空旧错误

Day 14 验收时发现：故意改错 API key 后，失败信息会写入 `error_message`；恢复 API key 后重试成功，`status` 变回 `SUCCESS`，但旧的 `error_message` 仍然留着。

原因是成功 SQL 只更新了 `status`，没有清空 `error_message`。

所以所有成功更新 SQL 都要加：

```sql
error_message = NULL
```

需要修改：

```text
updateTitleOptions
updateSelectedTitleAndOutline
updateContent
updateOutline
updateSelectedTitle
```

例如：

```java
@Update("""
        UPDATE article
        SET content = #{content},
            phase = #{phase},
            status = #{status},
            error_message = NULL
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateContent(@Param("taskId") String taskId,
                  @Param("content") String content,
                  @Param("phase") String phase,
                  @Param("status") String status);
```

完整改法：只要是“成功写入标题 / 大纲 / 正文 / 保存标题 / 保存大纲 / 保存正文”的 SQL，都清空 `error_message`。

---

## 4. 标题生成失败时写入 FAILED

在 `ArticleServiceImpl.java` 中，找到：

```java
public ArticleVO generateTitles(String taskId)
```

在 `catch` 中加入：

```java
articleMapper.updateFailedStatus(
        taskId,
        ArticlePhase.TITLE_SELECTION.name(),
        ArticleStatus.FAILED.name(),
        e.getMessage()
);
```

推荐结构：

```java
} catch (Exception e) {
    long costMs = System.currentTimeMillis() - start;

    agentLogService.saveFailed(
            taskId,
            "TitleGeneratorAgent",
            prompt,
            costMs,
            e.getMessage()
    );

    articleMapper.updateFailedStatus(
            taskId,
            ArticlePhase.TITLE_SELECTION.name(),
            ArticleStatus.FAILED.name(),
            e.getMessage()
    );

    throw new RuntimeException("生成标题失败：" + e.getMessage());
}
```

---

## 5. 大纲生成失败时写入 FAILED

找到：

```java
public ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request)
```

在 `catch` 中加入：

```java
articleMapper.updateFailedStatus(
        request.getTaskId(),
        ArticlePhase.OUTLINE_EDITING.name(),
        ArticleStatus.FAILED.name(),
        e.getMessage()
);
```

---

## 6. 正文生成失败时写入 FAILED

找到：

```java
public ArticleVO generateContent(String taskId)
```

在 `catch` 中加入：

```java
articleMapper.updateFailedStatus(
        taskId,
        ArticlePhase.CONTENT_GENERATION.name(),
        ArticleStatus.FAILED.name(),
        e.getMessage()
);
```

---

## 7. 流式生成失败时写入 FAILED

如果 `streamGenerateContent` / `realStreamGenerateContent` 里有 `catch`，也加入：

```java
articleMapper.updateFailedStatus(
        taskId,
        ArticlePhase.CONTENT_GENERATION.name(),
        ArticleStatus.FAILED.name(),
        e.getMessage()
);
```

同时通过 SSE 发送 fail 事件：

```java
emitter.send(SseEmitter.event()
        .name("fail")
        .data(objectMapper.writeValueAsString(Map.of(
                "message", "真实流式生成失败：" + e.getMessage()
        ))));
```

---

# 第二部分：前端修改

## 8. 状态标签优化

在 `ArticleDetail.vue` 的文章详情卡片中，将状态展示改为：

```vue
<a-descriptions-item label="状态">
  <a-tag :color="article.status === 'SUCCESS' ? 'green' : article.status === 'FAILED' ? 'red' : 'blue'">
    {{ article.status }}
  </a-tag>
</a-descriptions-item>
```

---

## 9. 在详情中展示错误信息

在 `a-descriptions` 中加入：

```vue
<a-descriptions-item v-if="article.errorMessage" label="错误信息">
  <span class="error-text">{{ article.errorMessage }}</span>
</a-descriptions-item>
```

CSS：

```css
.error-text {
  color: #cf1322;
}
```

---

## 10. 增加失败 Alert

在文章任务详情卡片下面加入：

```vue
<a-alert
  v-if="article?.status === 'FAILED'"
  class="card"
  type="error"
  show-icon
  message="当前任务执行失败"
  :description="article.errorMessage || '暂无详细错误信息'"
/>
```

---

## 11. 新增失败阶段判断

在 computed 区域新增：

```ts
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
```

---

## 12. 新增失败处理卡片

放在失败 Alert 下面：

```vue
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
```

---

## 13. 失败后刷新文章详情和日志

生成标题、大纲、正文等函数的失败分支中加入：

```ts
await loadArticle()
await loadAgentLogs()
```

例如：

```ts
} catch (error) {
  console.error(error)
  message.error('生成标题失败')
  await loadArticle()
  await loadAgentLogs()
} finally {
  titleLoading.value = false
}
```

需要处理的位置：

```text
1. handleGenerateTitles catch
2. handleConfirmTitle catch
3. handleGenerateContent catch
4. handleStreamGenerateContent fail / onerror
5. handleRealStreamGenerateContent fail / onerror
```

---

## 14. 避免 loadArticle / loadAgentLogs 递归调用自己

错误写法：

```ts
if (res.code !== 0) {
  message.error(res.message)
  await loadArticle()
  await loadAgentLogs()
  return
}
```

如果这段出现在 `loadArticle` 自己内部，会造成递归请求。

正确的 `loadArticle`：

```ts
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
```

正确的 `loadAgentLogs`：

```ts
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
```

---

## 15. 普通流式生成 onerror 刷新状态

普通流式生成的 `onerror` 应改成：

```ts
eventSource.onerror = async () => {
  if (!closedByDone) {
    message.error('SSE 连接异常')
    streamLogs.value.push('SSE 连接异常')
    await loadArticle()
    await loadAgentLogs()
  }

  eventSource.close()
  streaming.value = false
}
```

---

# 第三部分：测试流程

## 16. 重启后端和前端

后端在 IDEA 中重启。

前端执行：

```powershell
npm run dev
```

命令含义：启动 Vue 前端开发服务器。

---

## 17. 测试标题生成失败

临时把 API Key 改错，然后：

```text
1. 创建文章任务
2. 点击生成标题
3. 等待失败
4. 页面显示“当前任务执行失败”
5. 错误信息显示出来
6. 状态是 FAILED
7. 阶段是 TITLE_SELECTION
8. 出现“重试生成标题”按钮
```

---

## 18. 测试重试成功后清空错误

恢复正确 API Key 后：

```text
1. 点击“重试生成标题”
2. 成功生成标题
3. 状态变回 SUCCESS
4. 失败 Alert 消失
5. 详情表格中的错误信息消失
6. 数据库 error_message 变成 NULL
```

---

## 19. 测试正文失败与恢复

```text
1. 临时改错 API Key
2. 点击生成正文
3. 页面显示 FAILED
4. phase = CONTENT_GENERATION
5. 出现“重试生成正文”
6. 恢复 API Key
7. 点击重试生成正文
8. 成功后 status = SUCCESS
9. 旧 errorMessage 消失
```

---

## 20. 数据库验收

进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

命令含义：进入 MySQL 容器，并使用 utf8mb4 字符集。

执行：

```sql
SET NAMES utf8mb4;
USE ai_article_studio;

SELECT
  task_id,
  phase,
  status,
  error_message,
  update_time
FROM article
ORDER BY update_time DESC
LIMIT 5;
```

失败时应看到：

```text
status = FAILED
error_message 不为空
```

重试成功后应看到：

```text
status = SUCCESS
error_message = NULL
```

---

# 第四部分：常见问题

## 21. 数据库状态没有变成 FAILED

检查后端 catch 里是否调用：

```java
articleMapper.updateFailedStatus(...)
```

---

## 22. 数据库有 FAILED，但前端不显示

检查失败后前端是否调用：

```ts
await loadArticle()
```

---

## 23. 重试成功后错误提示还在

检查成功 SQL 是否加了：

```sql
error_message = NULL
```

重点检查：

```text
updateTitleOptions
updateSelectedTitleAndOutline
updateContent
updateOutline
updateSelectedTitle
```

---

## 24. loadArticle 或 loadAgentLogs 无限请求

检查它们内部是否自己调用自己。失败分支中不要写：

```ts
await loadArticle()
```

或：

```ts
await loadAgentLogs()
```

---

## 25. SSE onerror 后状态不刷新

普通流式和真实流式的 `onerror` 都建议：

```ts
await loadArticle()
await loadAgentLogs()
```

并且函数要写成：

```ts
eventSource.onerror = async () => {
  ...
}
```

---

# 第五部分：Day 14 验收标准

Day 14 完成标准：

```text
1. ArticleMapper 新增 updateFailedStatus
2. 标题生成失败时写入 FAILED 状态
3. 大纲生成失败时写入 FAILED 状态
4. 正文生成失败时写入 FAILED 状态
5. 流式生成失败时写入 FAILED 状态
6. 失败时 article.errorMessage 有错误原因
7. 前端显示失败 alert
8. 前端显示失败处理卡片
9. 根据 phase 显示正确的重试按钮
10. 重试成功后状态恢复 SUCCESS
11. 成功后 errorMessage 被清空
12. Agent 日志仍然正常记录失败原因
13. loadArticle / loadAgentLogs 没有递归调用自身
14. SSE onerror 后能刷新文章状态
```

---

# Day 14 完成后的项目状态

完成 Day 14 后，项目的工程化程度明显提升：

```text
失败状态可持久化
错误信息可见
用户可以重试
状态流转更清楚
Agent 日志可辅助排查
成功后旧错误自动清空
```

这比单纯“调用模型生成文章”更像真实可用的 AI 产品。
