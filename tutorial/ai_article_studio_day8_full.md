# AI Article Studio：Day 8 完整开发教程

> Day 8 目标：把 Day 5 的“模拟流式输出”升级为 **真正 token/chunk 级流式输出**。  
> 前端点击“真实流式生成正文”后，后端不再等待模型完整返回，而是通过 `stream=true` 让模型边生成、后端边接收、前端边展示。

---

## 0. Day 8 最终成果

Day 5 的流式逻辑是：

```text
模型一次性返回完整正文
↓
后端按段落拆分
↓
SSE 分段推给前端
```

Day 8 升级后的真实流式逻辑是：

```text
前端点击“真实流式生成正文”
↓
后端请求大模型 stream=true
↓
模型边生成，后端边接收 chunk
↓
后端通过 SseEmitter 实时转发给前端
↓
前端通过 EventSource 实时显示正文
↓
生成完成后保存到 article.content
↓
写入 agent_log
```

完成后，文章详情页会新增按钮：

```text
真实流式生成正文
```

点击后可以看到：

```text
1. 生成进度实时出现
2. 正文边生成边显示
3. 生成完成后保存到数据库
4. Agent 日志中出现 ContentGeneratorAgent-RealStream
```

---

## 1. Day 8 新增和修改内容

新增后端文件：

```text
StreamChunkHandler.java
StreamingLlmClient.java
OpenAiCompatibleStreamingLlmClient.java
```

修改后端文件：

```text
ArticleService.java
ArticleServiceImpl.java
ArticleController.java
```

修改前端文件：

```text
frontend/src/api/article.ts
frontend/src/pages/ArticleDetail.vue
```

新增后端接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/real-stream-generate-content/{taskId}` | GET | 真实大模型流式生成正文 |

---

## 2. Day 8 架构变化

Day 7 当前结构：

```text
ContentGeneratorAgent
↓
OpenAiCompatibleLlmClient
↓
模型一次性返回完整正文
↓
后端模拟拆段
↓
SSE 推给前端
```

Day 8 新结构：

```text
ContentGeneratorAgent 负责 buildPrompt
↓
OpenAiCompatibleStreamingLlmClient
↓
请求模型 stream=true
↓
模型返回 SSE chunk
↓
后端读取 chunk
↓
后端转发给前端
↓
前端实时显示
```

注意：

```text
Day 8 只做正文真实流式生成。
标题和大纲仍然保持普通调用。
```

原因：

```text
标题和大纲需要稳定返回 JSON。
JSON 输出不适合先做 token 级流式，否则解析更复杂。
```

---

# 第一部分：后端开发

---

## 3. 创建流式回调接口 `StreamChunkHandler`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/StreamChunkHandler.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

@FunctionalInterface
public interface StreamChunkHandler {

    void onChunk(String chunk);
}
```

作用：

```text
当模型流式返回一个文本片段时，调用 onChunk。
```

为什么需要这个接口？

因为真实流式时，模型不是一次性返回完整文本，而是不断返回小片段：

```text
chunk1
chunk2
chunk3
...
```

后端每收到一个片段，就通过这个回调交给业务层处理。

`@FunctionalInterface` 的意思是：

```text
这个接口只有一个抽象方法，可以用 Lambda 表达式实现。
```

例如：

```java
chunk -> {
    streamedContent.append(chunk);
}
```

---

## 4. 创建流式大模型接口 `StreamingLlmClient`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/StreamingLlmClient.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

public interface StreamingLlmClient {

    String chatStream(String prompt, StreamChunkHandler handler);
}
```

作用：

```text
定义真实流式大模型调用接口。
```

为什么返回值还是 `String`？

```text
一边通过 handler 实时推送 chunk，
一边把所有 chunk 拼成完整正文返回，
方便最后保存到数据库 article.content。
```

---

## 5. 创建真实流式客户端 `OpenAiCompatibleStreamingLlmClient`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/OpenAiCompatibleStreamingLlmClient.java
```

完整代码：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class OpenAiCompatibleStreamingLlmClient implements StreamingLlmClient {

    private final ObjectMapper objectMapper;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout-seconds:120}")
    private long timeoutSeconds;

    @Override
    public String chatStream(String prompt, StreamChunkHandler handler) {
        try {
            if (!StringUtils.hasText(apiKey)) {
                throw new RuntimeException("LLM_API_KEY 未配置");
            }

            String url = baseUrl + "/v1/chat/completions";

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "temperature", 0.7,
                    "stream", true
            );

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpResponse<java.io.InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("大模型流式调用失败，状态码：" + response.statusCode()
                        + "，响应：" + errorBody);
            }

            StringBuilder fullContent = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8)
            )) {
                String line;

                while ((line = reader.readLine()) != null) {
                    if (!StringUtils.hasText(line)) {
                        continue;
                    }

                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String data = line.substring("data:".length()).trim();

                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    String chunk = parseChunk(data);

                    if (StringUtils.hasText(chunk)) {
                        fullContent.append(chunk);
                        handler.onChunk(chunk);
                    }
                }
            }

            return fullContent.toString();
        } catch (Exception e) {
            throw new RuntimeException("流式调用大模型失败：" + e.getMessage(), e);
        }
    }

    private String parseChunk(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }

            JsonNode firstChoice = choices.get(0);

            JsonNode deltaContent = firstChoice
                    .path("delta")
                    .path("content");

            if (deltaContent.isTextual()) {
                return deltaContent.asText();
            }

            JsonNode messageContent = firstChoice
                    .path("message")
                    .path("content");

            if (messageContent.isTextual()) {
                return messageContent.asText();
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
```

---

## 6. 理解 `OpenAiCompatibleStreamingLlmClient`

### 6.1 为什么请求体里加 `stream: true`

代码：

```java
"stream", true
```

含义：

```text
告诉模型服务开启流式返回。
```

普通接口返回：

```text
一次性返回完整 JSON
```

流式接口返回：

```text
data: {...}
data: {...}
data: {...}
data: [DONE]
```

### 6.2 为什么使用 `InputStream`

代码：

```java
HttpResponse<java.io.InputStream> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofInputStream()
);
```

含义：

```text
不再一次性读取完整响应，而是拿到一个 InputStream，边读边处理。
```

普通请求一般使用：

```java
HttpResponse.BodyHandlers.ofString()
```

但它会等完整返回后才给你结果。真实流式必须用：

```java
HttpResponse.BodyHandlers.ofInputStream()
```

### 6.3 为什么逐行读取

代码：

```java
while ((line = reader.readLine()) != null) {
```

含义：

```text
持续读取模型返回的每一行 SSE 数据。
```

OpenAI-compatible 流式响应一般是：

```text
data: {"choices":[{"delta":{"content":"第一段"}}]}
data: {"choices":[{"delta":{"content":"第二段"}}]}
data: [DONE]
```

### 6.4 `[DONE]` 是什么

代码：

```java
if ("[DONE]".equals(data)) {
    break;
}
```

含义：

```text
模型返回 [DONE] 表示流式生成结束。
```

### 6.5 `parseChunk` 做了什么

代码优先读取：

```text
choices[0].delta.content
```

这是 OpenAI-compatible 流式返回常见结构。

同时兼容读取：

```text
choices[0].message.content
```

这是为了适配某些平台可能返回的非标准结构。

---

# 第二部分：修改 Service 接口

---

## 7. 修改 `ArticleService`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

新增 import 如果没有：

```java
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
```

新增方法：

```java
SseEmitter realStreamGenerateContent(String taskId);
```

最终正文生成相关方法应类似：

```java
ArticleVO generateContent(String taskId);

SseEmitter streamGenerateContent(String taskId);

SseEmitter realStreamGenerateContent(String taskId);
```

含义：

| 方法 | 作用 |
|---|---|
| `generateContent` | 普通一次性生成正文 |
| `streamGenerateContent` | Day 5 模拟流式生成 |
| `realStreamGenerateContent` | Day 8 真实模型流式生成 |

---

# 第三部分：修改 `ArticleServiceImpl`

---

## 8. 注入 `StreamingLlmClient`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/ArticleServiceImpl.java
```

新增 import：

```java
import com.nana.aiarticlestudio.agent.StreamingLlmClient;
```

在字段区新增：

```java
private final StreamingLlmClient streamingLlmClient;
```

因为类上用了：

```java
@RequiredArgsConstructor
```

Spring 会自动注入。

注意：

```text
StreamingLlmClient 只在 llm.mode=real 时有实现。
所以 Day 8 测试真实流式时必须使用 llm.mode=real。
```

---

## 9. 新增真实流式方法

在 `ArticleServiceImpl` 中新增：

```java
@Override
public SseEmitter realStreamGenerateContent(String taskId) {
    SseEmitter emitter = new SseEmitter(0L);

    CompletableFuture.runAsync(() -> {
        String prompt = null;
        long start = System.currentTimeMillis();

        try {
            sendSse(emitter, "progress", new SseMessage(
                    "progress",
                    "开始检查文章任务",
                    null,
                    null,
                    null
            ));

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

            sendSse(emitter, "progress", new SseMessage(
                    "progress",
                    "正在连接真实大模型流式接口",
                    null,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.RUNNING.name()
            ));

            prompt = contentGeneratorAgent.buildPrompt(
                    article.getTopic(),
                    article.getSelectedTitle(),
                    article.getOutline(),
                    article.getStyle()
            );

            StringBuilder streamedContent = new StringBuilder();

            String rawContent = streamingLlmClient.chatStream(prompt, chunk -> {
                try {
                    streamedContent.append(chunk);

                    String cleaned = contentGeneratorAgent.clean(streamedContent.toString());

                    sendSse(emitter, "content", new SseMessage(
                            "content",
                            "正文生成中",
                            cleaned,
                            ArticlePhase.CONTENT_GENERATION.name(),
                            ArticleStatus.RUNNING.name()
                    ));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            String finalContent = contentGeneratorAgent.clean(rawContent);

            int result = articleMapper.updateContent(
                    taskId,
                    finalContent,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存正文失败");
            }

            long costMs = System.currentTimeMillis() - start;

            agentLogService.saveSuccess(
                    taskId,
                    "ContentGeneratorAgent-RealStream",
                    prompt,
                    rawContent,
                    costMs
            );

            sendSse(emitter, "done", new SseMessage(
                    "done",
                    "真实流式正文生成并保存成功",
                    finalContent,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            ));

            emitter.complete();
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;

            agentLogService.saveFailed(
                    taskId,
                    "ContentGeneratorAgent-RealStream",
                    prompt,
                    e.getMessage(),
                    costMs
            );

            try {
                sendSse(emitter, "fail", new SseMessage(
                        "fail",
                        e.getMessage(),
                        null,
                        ArticlePhase.FAILED.name(),
                        ArticleStatus.FAILED.name()
                ));
            } catch (Exception ignored) {
            }

            emitter.completeWithError(e);
        }
    });

    return emitter;
}
```

---

## 10. 这段业务逻辑做了什么

整体流程：

```text
1. 创建 SseEmitter
2. 异步执行真实流式生成
3. 检查文章任务是否存在
4. 检查标题和大纲是否已经生成
5. 构造正文生成 Prompt
6. 调用 streamingLlmClient.chatStream
7. 每收到一个 chunk，就追加到 streamedContent
8. 清洗 <think> 内容
9. 通过 sendSse 推送 content 事件给前端
10. 模型结束后得到 rawContent
11. 清洗后保存到 article.content
12. 写入 agent_log
13. 推送 done 事件
14. 出错时写入失败日志并推送 fail 事件
```

关键代码：

```java
StringBuilder streamedContent = new StringBuilder();
```

作用：

```text
累积到目前为止模型已经生成的所有正文。
```

```java
streamedContent.append(chunk);
```

作用：

```text
每收到一个新 chunk，就拼接到完整内容里。
```

```java
String cleaned = contentGeneratorAgent.clean(streamedContent.toString());
```

作用：

```text
实时清洗 <think> 内容，避免前端看到模型思考过程。
```

```java
sendSse(emitter, "content", ...)
```

作用：

```text
把当前已经生成的正文实时推给前端。
```

---

## 11. 确认已有辅助方法

Day 5 中你已经加过：

```java
private void sendSse(SseEmitter emitter, String eventName, SseMessage message) throws IOException {
    emitter.send(SseEmitter.event()
            .name(eventName)
            .data(message));
}
```

Day 8 继续复用它。

还需要确认 import 中有：

```java
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CompletableFuture;
```

---

# 第四部分：修改 Controller

---

## 12. 新增真实流式接口

路径：

```text
src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增接口：

```java
@GetMapping(value = "/real-stream-generate-content/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter realStreamGenerateContent(@PathVariable String taskId) {
    return articleService.realStreamGenerateContent(taskId);
}
```

最终正文生成相关接口有三个：

```text
POST /api/article/generate-content/{taskId}
GET  /api/article/stream-generate-content/{taskId}
GET  /api/article/real-stream-generate-content/{taskId}
```

含义：

| 接口 | 作用 |
|---|---|
| `generate-content` | 普通一次性生成 |
| `stream-generate-content` | 模拟流式生成 |
| `real-stream-generate-content` | 真实模型流式生成 |

---

# 第五部分：后端测试

---

## 13. 确认 `application.yml`

确保当前是：

```yaml
llm:
  mode: real
  base-url: https://api.minimax.chat
  api-key: ${LLM_API_KEY:}
  model: MiniMax-M2.5
  timeout-seconds: 120
```

注意：

```text
Day 8 测试真实流式时，必须使用 llm.mode=real。
```

否则 `StreamingLlmClient` 没有实现类，后端可能启动失败。

---

## 14. 用 curl 测试真实流式接口

先准备一个已经有：

```text
selectedTitle
outline
```

的 taskId。

然后在 PowerShell 执行：

```powershell
curl.exe -N "http://localhost:8123/api/article/real-stream-generate-content/你的taskId"
```

含义：

```text
curl.exe：使用 Windows 里的 curl 工具。
-N：关闭缓冲，实时显示 SSE 输出。
URL：真实流式正文生成接口。
```

你应该看到类似：

```text
event:progress
data:{"type":"progress","message":"开始检查文章任务"...}

event:progress
data:{"type":"progress","message":"正在连接真实大模型流式接口"...}

event:content
data:{"type":"content","message":"正文生成中","content":"# 标题"...}

event:content
data:{"type":"content","message":"正文生成中","content":"# 标题\n\n第一段"...}

event:done
data:{"type":"done","message":"真实流式正文生成并保存成功"...}
```

如果内容逐步出现，说明后端真实流式已经成功。

---

# 第六部分：前端开发

---

## 15. 修改 `article.ts`

路径：

```text
frontend/src/api/article.ts
```

新增：

```ts
export const realStreamGenerateContentUrl = (taskId: string) => {
  return `http://localhost:8123/api/article/real-stream-generate-content/${taskId}`
}
```

作用：

```text
返回真实流式接口 URL，给 EventSource 使用。
```

为什么不用 axios？

```text
EventSource 是浏览器原生 SSE 客户端，它需要直接传 URL。
```

---

## 16. 修改 `ArticleDetail.vue` import

路径：

```text
frontend/src/pages/ArticleDetail.vue
```

从 `../api/article` 中新增导入：

```ts
realStreamGenerateContentUrl,
```

最终类似：

```ts
import {
  confirmTitle,
  generateContent,
  generateTitles,
  getArticle,
  listAgentLogs,
  realStreamGenerateContentUrl,
  streamGenerateContentUrl,
  type AgentLogVO,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
} from '../api/article'
```

---

## 17. 新增真实流式 loading 状态

在已有状态附近新增：

```ts
const realStreaming = ref(false)
```

已有的是：

```ts
const streaming = ref(false)
```

两者区别：

| 状态 | 含义 |
|---|---|
| `streaming` | Day 5 模拟流式 |
| `realStreaming` | Day 8 真实模型流式 |

---

## 18. 新增真实流式方法

在 `ArticleDetail.vue` 的方法区新增：

```ts
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

    await loadAgentLogs()
  })

  eventSource.onerror = async () => {
    if (!closedByDone) {
      message.error('真实流式 SSE 连接异常')
      streamLogs.value.push('真实流式 SSE 连接异常')
      await loadAgentLogs()
    }

    eventSource.close()
    realStreaming.value = false
  }
}
```

---

## 19. 这段前端逻辑做了什么

整体流程：

```text
1. 检查 taskId
2. 检查是否已有大纲
3. 设置 realStreaming = true
4. 清空旧的 streamLogs 和 streamContent
5. 创建 EventSource 连接后端真实流式接口
6. 监听 progress 事件
7. 监听 content 事件
8. 监听 done 事件
9. 监听 fail 事件
10. 监听 onerror 连接异常
11. 成功后刷新文章详情和 Agent 日志
```

重点：

```ts
streamContent.value = data.content
```

这里不是追加 chunk，而是直接使用后端传来的完整已生成内容。

原因：

```text
后端每次 content 事件传的是“截至当前为止的完整正文”。
前端直接覆盖展示即可。
```

---

## 20. 新增顶部按钮

在顶部按钮区加：

```vue
<a-button type="primary" danger ghost :loading="realStreaming" @click="handleRealStreamGenerateContent">
  真实流式生成正文
</a-button>
```

建议顶部按钮区最终类似：

```vue
<a-space>
  <a-button @click="goBack">返回列表</a-button>

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
</a-space>
```

---

## 21. 在大纲卡片下也加按钮

找到大纲卡片里的 action-row：

```vue
<div class="action-row">
  <a-button type="primary" :loading="contentLoading" @click="handleGenerateContent">
    生成正文
  </a-button>

  <a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
    流式生成正文
  </a-button>
</div>
```

改成：

```vue
<div class="action-row">
  <a-button type="primary" :loading="contentLoading" @click="handleGenerateContent">
    生成正文
  </a-button>

  <a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
    流式生成正文
  </a-button>

  <a-button type="primary" danger ghost :loading="realStreaming" @click="handleRealStreamGenerateContent">
    真实流式生成正文
  </a-button>
</div>
```

这样用户看完大纲后可以直接点击真实流式生成。

---

# 第七部分：完整测试流程

---

## 22. 重启前端

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
重启前端开发服务器。
```

---

## 23. 前端验收流程

打开：

```text
http://localhost:5173/article/list
```

测试流程：

```text
1. 创建新文章任务
2. 进入详情页
3. 点击生成标题
4. 选择标题
5. 点击确认标题并生成大纲
6. 点击真实流式生成正文
7. 看生成进度是否出现
8. 看正文是否边生成边显示
9. 等待完成
10. 刷新页面，确认正文仍然存在
11. 查看 Agent 执行日志是否出现 ContentGeneratorAgent-RealStream
```

---

# 第八部分：数据库验收

---

## 24. 检查正文是否保存

进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

含义：

```text
进入 MySQL 容器，并使用 utf8mb4 字符集。
```

执行：

```sql
SET NAMES utf8mb4;
USE ai_article_studio;

SELECT
  task_id,
  phase,
  status,
  CHAR_LENGTH(content) AS content_len,
  LEFT(content, 300) AS content_preview
FROM article
ORDER BY update_time DESC
LIMIT 3;
```

验收标准：

```text
phase = CONTENT_GENERATION
status = SUCCESS
content_len > 0
content_preview 有正文内容
```

---

## 25. 检查 Agent 日志

执行：

```sql
SELECT
  agent_name,
  status,
  cost_ms,
  LEFT(error_message, 200) AS error_preview,
  create_time
FROM agent_log
ORDER BY create_time DESC
LIMIT 10;
```

应该能看到：

```text
ContentGeneratorAgent-RealStream
```

说明真实流式生成也记录进日志了。

---

# 第九部分：常见问题

---

## 26. 后端启动失败：找不到 `StreamingLlmClient`

原因：

```text
llm.mode=mock 时，OpenAiCompatibleStreamingLlmClient 不会生效；
但 ArticleServiceImpl 又需要注入 StreamingLlmClient。
```

解决：

Day 8 测试真实流式时改成：

```yaml
llm:
  mode: real
```

后面如果想 mock 模式也能启动，可以再做一个：

```text
MockStreamingLlmClient
```

当前 Day 8 先不做。

---

## 27. 点击真实流式后很久没反应

可能原因：

```text
1. 模型服务不支持 stream=true
2. 模型响应慢
3. 网络慢
4. timeout 太短
```

先用 curl 测试后端接口：

```powershell
curl.exe -N "http://localhost:8123/api/article/real-stream-generate-content/你的taskId"
```

如果 curl 也不逐步输出，说明问题在后端或模型服务。

---

## 28. 前端显示 SSE 连接异常，但正文生成成功了

可能原因：

```text
后端 done 后 emitter.complete()
浏览器有时仍会触发 onerror
```

前端代码里用了：

```ts
let closedByDone = false
```

并在 done 时设置：

```ts
closedByDone = true
```

就是为了避免误报。确认 `done` 事件里是否设置了它。

---

## 29. 正文仍然出现 `<think>`

确认后端用了：

```java
String finalContent = contentGeneratorAgent.clean(rawContent);
```

并且每次 content 事件里也用了：

```java
String cleaned = contentGeneratorAgent.clean(streamedContent.toString());
```

如果模型输出 `<think>` 没闭合，正则可能暂时清不掉。后续可以增强 `clean` 方法，处理未闭合 think。

---

## 30. Agent 日志没有显示失败记录

前端 catch / fail / onerror 中要调用：

```ts
await loadAgentLogs()
```

后端 catch 中也要调用：

```java
agentLogService.saveFailed(...)
```

---

## 31. chunk 解析不到内容

如果接口连接成功，但正文一直为空，可能是模型平台返回格式和 OpenAI 标准不完全一致。

当前代码解析的是：

```text
choices[0].delta.content
```

兼容：

```text
choices[0].message.content
```

如果平台返回字段不同，需要在 `parseChunk` 中适配。

临时调试方式：

```java
System.out.println("stream data = " + data);
```

加在 `parseChunk(data)` 前面，查看模型真实返回结构。

注意：

```text
不要打印 API Key。
```

---

# 第十部分：Day 8 验收标准

Day 8 完成标准：

```text
1. 新增 StreamChunkHandler
2. 新增 StreamingLlmClient
3. 新增 OpenAiCompatibleStreamingLlmClient
4. 请求体支持 stream=true
5. 后端能读取模型 SSE chunk
6. 后端能把 chunk 通过 SseEmitter 转发给前端
7. 新增 realStreamGenerateContent 业务方法
8. 新增 /api/article/real-stream-generate-content/{taskId} 接口
9. 前端新增 realStreamGenerateContentUrl
10. 前端新增真实流式生成正文按钮
11. 正文能边生成边展示
12. 生成完成后保存到 article.content
13. 生成完成后写入 agent_log
14. 前端能看到 ContentGeneratorAgent-RealStream 日志
```

---

# 第十一部分：Day 8 完成后的项目状态

到 Day 8 后，项目已经具备更接近真实 AI 产品的能力：

```text
真实大模型调用
真实 token/chunk 流式生成
SSE 实时转发
前端实时展示
数据库保存
Agent 日志追踪
```

这已经是一个比较完整的 AI 内容生成应用骨架。

---

# Day 9 预告

Day 9 建议做：

```text
JSON 修复重试机制
```

因为你已经遇到过：

```text
标题 JSON 解析失败
大纲 JSON 解析失败
模型输出 <think>
JSON 数组不完整
章节数量不符合要求
```

Day 9 可以让系统在 JSON 解析失败时自动：

```text
1. 记录失败日志
2. 构造修复 Prompt
3. 要求模型把原始输出修复成合法 JSON
4. 再解析一次
5. 仍失败才返回错误
```

这会显著提升真实模型调用的稳定性。
