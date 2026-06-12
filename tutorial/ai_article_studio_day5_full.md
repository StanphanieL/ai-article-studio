# AI Article Studio：Day 5 完整开发教程

> Day 5 目标：在 Day 4「正文生成」基础上，新增 **SSE 流式输出**，让前端可以实时看到生成进度和正文内容。今天不新增新的 Agent，重点是让生成过程从「点击后等待接口返回」变成「前端实时接收后端推送」。

---

## 0. Day 5 最终成果

完成 Day 5 后，详情页会新增：

```text
流式生成正文
```

点击后页面会实时显示：

```text
生成进度
- 开始检查文章任务
- 已找到文章任务
- 正在调用正文生成 Agent
- 正文生成中，正在接收内容
- 正文生成并保存成功
```

同时，文章正文会逐段显示在页面中。

完整流程：

```text
创建文章任务
↓
生成标题
↓
选择标题并生成大纲
↓
点击“流式生成正文”
↓
前端实时显示生成进度
↓
前端实时展示正文内容
↓
最终正文保存到 article.content 字段
```

---

## 1. Day 5 核心设计

Day 5 使用的是 SSE：

```text
SSE = Server-Sent Events
```

它的作用是：

```text
后端可以持续向前端推送消息。
前端不需要反复轮询接口。
浏览器原生 EventSource 就可以接收 SSE。
```

本项目中的流式逻辑：

```text
前端点击“流式生成正文”
↓
前端用 EventSource 连接后端 SSE 接口
↓
后端检查任务、标题、大纲
↓
后端调用 ContentGeneratorAgent 生成正文
↓
后端把正文按段落拆成多个 chunk
↓
后端逐段推送给前端
↓
前端实时更新正文展示
↓
后端最终保存完整正文到数据库
```

注意：当前仍然使用 `MockLlmClient`。Mock 是一次性返回完整正文，所以 Day 5 是“模拟流式输出”：

```text
完整正文 → 后端拆成段落 → SSE 逐段推给前端
```

后面接真实大模型时，会变成：

```text
真实模型逐 token / chunk 返回 → 后端实时转发给前端 → 前端实时展示
```

---

# 第一部分：后端开发

---

## 2. 创建 SSE 消息对象 `SseMessage`

在后端 IDEA 中找到：

```text
src/main/java/com/nana/aiarticlestudio/model/vo
```

新建：

```text
SseMessage.java
```

代码：

```java
package com.nana.aiarticlestudio.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SseMessage {

    private String type;

    private String message;

    private String content;

    private String phase;

    private String status;
}
```

作用：

```text
SseMessage 是后端通过 SSE 推送给前端的消息结构。
```

字段解释：

| 字段 | 含义 |
|---|---|
| `type` | 消息类型，比如 progress、content、done、fail |
| `message` | 进度提示文案 |
| `content` | 当前生成出来的正文内容 |
| `phase` | 当前文章阶段 |
| `status` | 当前任务状态 |

示例：

```json
{
  "type": "progress",
  "message": "正在调用正文生成 Agent",
  "content": null,
  "phase": "CONTENT_GENERATION",
  "status": "RUNNING"
}
```

---

## 3. 修改 `ArticleService`

打开：

```text
src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

新增 import：

```java
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
```

在接口中新增方法：

```java
SseEmitter streamGenerateContent(String taskId);
```

完整结构应类似：

```java
package com.nana.aiarticlestudio.service;

import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
import com.nana.aiarticlestudio.model.vo.ArticleVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ArticleService {

    String createArticle(ArticleCreateRequest request);

    ArticleVO getByTaskId(String taskId);

    PageResult<ArticleVO> listArticles(ArticleListRequest request);

    Boolean deleteByTaskId(String taskId);

    ArticleVO generateTitles(String taskId);

    ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request);

    ArticleVO generateContent(String taskId);

    SseEmitter streamGenerateContent(String taskId);
}
```

作用：

```text
给 Service 层新增一个“流式生成正文”的方法。
这个方法不再一次性返回 ArticleVO，而是返回 SseEmitter。
```

---

## 4. 修改 `ArticleServiceImpl`

打开：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/ArticleServiceImpl.java
```

### 4.1 新增 import

在文件顶部新增：

```java
import com.nana.aiarticlestudio.model.vo.SseMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
```

这些 import 的作用：

| import | 作用 |
|---|---|
| `SseMessage` | 后端推送给前端的消息对象 |
| `SseEmitter` | Spring 提供的 SSE 推送工具 |
| `IOException` | 处理 SSE 发送异常 |
| `CompletableFuture` | 异步执行生成逻辑 |

---

### 4.2 新增 SSE 发送辅助方法

在 `ArticleServiceImpl` 类里新增：

```java
private void sendSse(SseEmitter emitter, String eventName, SseMessage message) throws IOException {
    emitter.send(SseEmitter.event()
            .name(eventName)
            .data(message));
}
```

作用：

```text
统一发送 SSE 消息。
eventName 是前端监听的事件名，例如 progress、content、done、fail。
message 是真正传给前端的数据。
```

---

### 4.3 新增模拟延迟方法

继续在类里新增：

```java
private void sleep(long millis) {
    try {
        Thread.sleep(millis);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

作用：

```text
模拟 AI 生成过程中的停顿。
否则 Mock 内容会瞬间返回，看不出流式效果。
```

---

### 4.4 新增 `streamGenerateContent` 方法

在 `ArticleServiceImpl` 类中新增：

```java
@Override
public SseEmitter streamGenerateContent(String taskId) {
    SseEmitter emitter = new SseEmitter(0L);

    CompletableFuture.runAsync(() -> {
        try {
            sendSse(emitter, "progress", new SseMessage(
                    "progress",
                    "开始检查文章任务",
                    null,
                    null,
                    null
            ));

            sleep(500);

            Article article = articleMapper.selectByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            sendSse(emitter, "progress", new SseMessage(
                    "progress",
                    "已找到文章任务：" + article.getTopic(),
                    null,
                    article.getPhase(),
                    article.getStatus()
            ));

            sleep(500);

            if (!StringUtils.hasText(article.getSelectedTitle())) {
                throw new RuntimeException("请先确认标题");
            }

            if (!StringUtils.hasText(article.getOutline())) {
                throw new RuntimeException("请先生成大纲");
            }

            sendSse(emitter, "progress", new SseMessage(
                    "progress",
                    "正在调用正文生成 Agent",
                    null,
                    article.getPhase(),
                    ArticleStatus.RUNNING.name()
            ));

            sleep(700);

            String content = contentGeneratorAgent.generate(
                    article.getTopic(),
                    article.getSelectedTitle(),
                    article.getOutline(),
                    article.getStyle()
            );

            sendSse(emitter, "progress", new SseMessage(
                    "progress",
                    "正文生成中，正在接收内容",
                    null,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.RUNNING.name()
            ));

            String[] chunks = content.split("\\n\\s*\\n");
            StringBuilder streamedContent = new StringBuilder();

            for (String chunk : chunks) {
                if (!StringUtils.hasText(chunk)) {
                    continue;
                }

                if (streamedContent.length() > 0) {
                    streamedContent.append("\n\n");
                }

                streamedContent.append(chunk);

                sendSse(emitter, "content", new SseMessage(
                        "content",
                        "正文片段已生成",
                        streamedContent.toString(),
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.RUNNING.name()
                ));

                sleep(300);
            }

            int result = articleMapper.updateContent(
                    taskId,
                    content,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存正文失败");
            }

            sendSse(emitter, "done", new SseMessage(
                    "done",
                    "正文生成并保存成功",
                    content,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            ));

            emitter.complete();
        } catch (Exception e) {
            try {
                sendSse(emitter, "fail", new SseMessage(
                        "fail",
                        e.getMessage(),
                        null,
                        ArticlePhase.FAILED.name(),
                        ArticleStatus.FAILED.name()
                ));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }
    });

    return emitter;
}
```

这段代码做了什么：

```text
1. 创建 SseEmitter
2. 异步执行正文生成逻辑
3. 推送 progress 事件给前端
4. 检查文章任务是否存在
5. 检查标题和大纲是否已生成
6. 调用 ContentGeneratorAgent 生成正文
7. 把正文按段落拆分
8. 逐段通过 content 事件推送给前端
9. 全部生成后保存到数据库
10. 推送 done 事件
11. 如果失败，推送 fail 事件
```

关键代码解释：

```java
SseEmitter emitter = new SseEmitter(0L);
```

含义：

```text
创建一个不主动超时的 SSE 连接。
0L 表示不设置超时时间。
```

```java
CompletableFuture.runAsync(...)
```

含义：

```text
让正文生成过程在异步线程里执行。
否则接口会一直阻塞，不利于 SSE 持续推送。
```

```java
String[] chunks = content.split("\\n\\s*\\n");
```

含义：

```text
把 Markdown 正文按空行拆成多个段落。
这样可以模拟“逐段生成”的效果。
```

---

## 5. 修改 `ArticleController`

打开：

```text
src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增 import：

```java
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
```

新增接口：

```java
@GetMapping(value = "/stream-generate-content/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamGenerateContent(@PathVariable String taskId) {
    return articleService.streamGenerateContent(taskId);
}
```

作用：

```text
提供一个 SSE 接口。
前端用 EventSource 连接这个接口后，后端可以持续推送生成进度和正文内容。
```

最终 `ArticleController` 应包含这些接口：

```text
POST /api/article/create
GET  /api/article/{taskId}
POST /api/article/list
POST /api/article/delete/{taskId}
POST /api/article/generate-titles/{taskId}
POST /api/article/confirm-title
POST /api/article/generate-content/{taskId}
GET  /api/article/stream-generate-content/{taskId}
```

注意：

```text
SSE 通常使用 GET。
浏览器原生 EventSource 只支持 GET 请求。
```

---

## 6. 重启后端

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

说明后端启动成功。

---

# 第二部分：后端 SSE 测试

---

## 7. 准备一个有效 taskId

这个 taskId 必须已经完成：

```text
生成标题
↓
选择标题并生成大纲
```

也就是说，这个任务在数据库里应该已经有：

```text
selected_title
outline
```

否则调用流式生成正文时会提示：

```text
请先确认标题
```

或者：

```text
请先生成大纲
```

---

## 8. 用 curl 测试 SSE

在 PowerShell 中执行：

```powershell
curl.exe -N "http://localhost:8123/api/article/stream-generate-content/你的taskId"
```

含义：

```text
curl.exe：调用 Windows 中真正的 curl 工具。
-N：关闭缓冲，让你能实时看到 SSE 输出。
URL：你的 SSE 接口地址。
```

你应该看到类似：

```text
event:progress
data:{"type":"progress","message":"开始检查文章任务",...}

event:progress
data:{"type":"progress","message":"已找到文章任务：AI产品经理如何提升效率",...}

event:content
data:{"type":"content","message":"正文片段已生成","content":"# AI 产品经理如何用工具提升 10 倍效率",...}

event:done
data:{"type":"done","message":"正文生成并保存成功",...}
```

如果能看到这些，说明后端 SSE 正常。

---

# 第三部分：前端开发

---

## 9. 修改 `article.ts`

打开：

```text
frontend/src/api/article.ts
```

在文件末尾新增：

```ts
export const streamGenerateContentUrl = (taskId: string) => {
  return `http://localhost:8123/api/article/stream-generate-content/${taskId}`
}
```

作用：

```text
生成 SSE 接口地址。
EventSource 需要直接传入 URL，所以这里返回完整的后端地址。
```

---

## 10. 修改 `ArticleDetail.vue`

打开：

```text
frontend/src/pages/ArticleDetail.vue
```

---

### 10.1 修改 import

把 `../api/article` 的导入改成包含 `streamGenerateContentUrl`。

原来大概是：

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

改成：

```ts
import {
  confirmTitle,
  generateContent,
  generateTitles,
  getArticle,
  streamGenerateContentUrl,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
} from '../api/article'
```

---

### 10.2 新增流式状态变量

找到这些变量：

```ts
const loading = ref(false)
const article = ref<ArticleVO | null>(null)
const selectedTitle = ref('')
```

在下面新增：

```ts
const streaming = ref(false)
const streamLogs = ref<string[]>([])
const streamContent = ref('')
```

作用：

| 变量 | 作用 |
|---|---|
| `streaming` | 是否正在流式生成正文 |
| `streamLogs` | 记录生成过程日志 |
| `streamContent` | 实时接收的正文内容 |

---

### 10.3 修改正文展示计算属性

Day 4 应该已有：

```ts
const contentHtml = computed(() => {
  if (!article.value?.content) {
    return ''
  }
  return markdown.render(article.value.content)
})
```

改成：

```ts
const displayContent = computed(() => {
  return streamContent.value || article.value?.content || ''
})

const contentHtml = computed(() => {
  if (!displayContent.value) {
    return ''
  }
  return markdown.render(displayContent.value)
})
```

作用：

```text
如果正在流式生成，就优先展示 streamContent。
如果没有流式内容，就展示数据库里的 article.content。
```

---

### 10.4 新增 SSE 数据解析方法

在方法区新增：

```ts
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
```

作用：

```text
把后端 SSE 推送过来的 JSON 字符串解析成对象。
```

---

### 10.5 新增流式生成正文方法

在 `handleGenerateContent` 方法后面新增：

```ts
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
  })

  eventSource.addEventListener('fail', (event) => {
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
  })

  eventSource.onerror = () => {
    if (!closedByDone) {
      message.error('SSE 连接异常')
      streamLogs.value.push('SSE 连接异常')
    }

    eventSource.close()
    streaming.value = false
  }
}
```

作用：

```text
1. 创建 EventSource 连接后端 SSE 接口
2. 监听 progress 事件，显示生成进度
3. 监听 content 事件，实时更新正文内容
4. 监听 done 事件，表示生成完成
5. 监听 fail 事件，表示业务失败
6. 监听 onerror，处理连接异常
```

---

### 10.6 新增“流式生成正文”按钮

找到按钮区域，Day 4 版本大概是：

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

  <a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
    流式生成正文
  </a-button>
</a-space>
```

作用：

```text
保留 Day 4 的普通生成正文按钮。
新增 Day 5 的流式生成正文按钮。
```

---

### 10.7 新增生成进度卡片

在文章大纲卡片下面、文章正文卡片上面，新增：

```vue
<a-card v-if="streamLogs.length > 0" class="card" title="生成进度">
  <a-timeline>
    <a-timeline-item v-for="(log, index) in streamLogs" :key="index">
      {{ log }}
    </a-timeline-item>
  </a-timeline>
</a-card>
```

作用：

```text
展示后端通过 SSE 推送过来的生成过程。
```

---

### 10.8 修改正文卡片显示条件

Day 4 的正文卡片可能是：

```vue
<a-card v-if="article?.content" class="card" title="文章正文">
  <div class="markdown-body" v-html="contentHtml"></div>
</a-card>
```

改成：

```vue
<a-card v-if="displayContent" class="card" title="文章正文">
  <div class="markdown-body" v-html="contentHtml"></div>
</a-card>
```

作用：

```text
普通正文和流式正文都可以展示。
```

---

# 第四部分：完整结构检查

---

## 11. `ArticleDetail.vue` 的 script 部分必须有

```ts
const streaming = ref(false)
const streamLogs = ref<string[]>([])
const streamContent = ref('')
```

必须有：

```ts
const displayContent = computed(() => {
  return streamContent.value || article.value?.content || ''
})
```

必须有：

```ts
const handleStreamGenerateContent = async () => {
  // EventSource 逻辑
}
```

---

## 12. `ArticleDetail.vue` 的 template 部分必须有

按钮：

```vue
<a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
  流式生成正文
</a-button>
```

生成进度卡片：

```vue
<a-card v-if="streamLogs.length > 0" class="card" title="生成进度">
  <a-timeline>
    <a-timeline-item v-for="(log, index) in streamLogs" :key="index">
      {{ log }}
    </a-timeline-item>
  </a-timeline>
</a-card>
```

正文卡片：

```vue
<a-card v-if="displayContent" class="card" title="文章正文">
  <div class="markdown-body" v-html="contentHtml"></div>
</a-card>
```

---

# 第五部分：前端测试

---

## 13. 重启前端

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
重新启动前端开发服务器。
```

---

## 14. 确认后端正常

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

## 15. 从前端完整测试

打开：

```text
http://localhost:5173/article/list
```

按照这个流程测试：

```text
1. 创建一条文章任务
2. 点击查看详情
3. 点击生成标题
4. 选择一个标题
5. 点击确认标题并生成大纲
6. 点击流式生成正文
7. 查看“生成进度”是否逐条出现
8. 查看“文章正文”是否逐段出现
```

成功后应该看到：

```text
生成进度
文章正文
```

并且正文不是突然出现，而是逐段显示。

---

# 第六部分：验收数据库保存

---

## 16. 从前端页面拿到 taskId

在详情页 URL 中可以看到类似：

```text
http://localhost:5173/article/detail/ab052c90402f470dabb9cf0ab9711265
```

最后这一段就是：

```text
ab052c90402f470dabb9cf0ab9711265
```

也就是 `taskId`。

---

## 17. 进入 MySQL

打开 PowerShell，执行：

```powershell
docker exec -it ai_article_mysql mysql -uroot -p123456
```

含义：

```text
进入 MySQL 容器，并用 root / 123456 登录数据库。
```

如果中文显示乱码，可以先在 PowerShell 执行：

```powershell
chcp 65001
```

含义：

```text
把当前 PowerShell 终端编码切到 UTF-8。
```

然后用：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

含义：

```text
进入 MySQL 容器，并强制 MySQL 客户端使用 utf8mb4 字符集。
```

进入 MySQL 后执行：

```sql
SET NAMES utf8mb4;
```

作用：

```text
告诉 MySQL 当前连接使用 utf8mb4 编码传输数据。
```

---

## 18. 查询 `article.content`

执行：

```sql
USE ai_article_studio;

SELECT 
  task_id,
  topic,
  phase,
  status,
  CHAR_LENGTH(content) AS content_chars,
  LEFT(content, 300) AS content_preview
FROM article
WHERE task_id = '你的taskId';
```

作用：

| 字段 | 用途 |
|---|---|
| `task_id` | 确认查的是当前文章任务 |
| `topic` | 确认选题 |
| `phase` | 确认阶段是否为 CONTENT_GENERATION |
| `status` | 确认状态是否为 SUCCESS |
| `content_chars` | 确认正文字符数大于 0 |
| `content_preview` | 预览正文前 300 个字符 |

如果看到：

```text
phase = CONTENT_GENERATION
status = SUCCESS
content_chars > 0
content_preview 有 Markdown 正文
```

说明前端触发流式生成后，最终正文已经保存到数据库 `article.content` 字段。

---

# 第七部分：常见问题

---

## 19. 点击流式生成正文后提示“请先生成大纲”

原因：

```text
当前文章任务还没有 outline。
```

解决：

```text
先生成标题
选择标题
确认标题并生成大纲
再点击流式生成正文
```

---

## 20. 前端提示 `SSE 连接异常`

先检查后端接口是否存在。

浏览器直接访问：

```text
http://localhost:8123/api/article/stream-generate-content/你的taskId
```

如果后端报 404，说明：

```text
ArticleController 没有加接口，或者后端没有重启。
```

如果后端返回业务错误，说明：

```text
taskId 对应的文章还没有标题或大纲。
```

---

## 21. 后端报错 `No converter for SseMessage`

一般 Spring Boot Web 默认有 Jackson，不应该出现。

如果出现，检查 `SseMessage` 是否有：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
```

并且字段是否都是普通字符串。

---

## 22. 后端报错找不到 `streamGenerateContent`

检查三个地方：

```text
ArticleService.java
ArticleServiceImpl.java
ArticleController.java
```

必须分别有：

```java
SseEmitter streamGenerateContent(String taskId);
```

```java
public SseEmitter streamGenerateContent(String taskId) {
    ...
}
```

```java
@GetMapping(value = "/stream-generate-content/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
```

---

## 23. PowerShell / MySQL 查询中文显示乱码

如果数据库查询显示：

```text
?????AI????
```

不一定是数据库存坏了，可能只是 PowerShell 或 MySQL 客户端显示编码问题。

处理：

```powershell
chcp 65001
```

然后：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

进入 MySQL 后：

```sql
SET NAMES utf8mb4;
```

再重新查询。

最终以两个地方判断最可靠：

```text
1. 前端刷新详情页后中文仍然正常
2. 数据库 content_chars > 0，phase = CONTENT_GENERATION，status = SUCCESS
```

---

# 第八部分：Day 5 验收标准

Day 5 完成标准：

```text
1. 后端新增 SseMessage
2. ArticleService 新增 streamGenerateContent
3. ArticleServiceImpl 能通过 SseEmitter 推送 progress/content/done/fail 事件
4. ArticleController 暴露 SSE 接口
5. 前端 article.ts 能生成 SSE URL
6. ArticleDetail.vue 新增“流式生成正文”按钮
7. 前端能实时显示生成进度
8. 前端能实时显示正文内容
9. 正文最终保存到数据库 article.content 字段
```

完成这些，Day 5 就结束。

---

# 第九部分：Day 6 预告

Day 6 可以开始做：

```text
1. 接入真实大模型 API
2. 替换 MockLlmClient
3. 支持 OpenAI-compatible 接口
4. 把标题、大纲、正文真正交给模型生成
5. 处理模型返回 JSON 不稳定的问题
```

Day 6 之后，这个项目就会从“Mock AI 应用”进入“真实 AI 应用”。
