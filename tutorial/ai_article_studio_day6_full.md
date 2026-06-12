# AI Article Studio：Day 6 完整开发教程

> Day 6 目标：把前五天的 Mock AI 应用升级成真实 AI 应用。  
> 本日重点是：接入真实大模型 API，替换 `MockLlmClient`，并修复真实模型接入后出现的前端加载状态、模型输出清洗、API 配置等问题。

---

## 0. Day 6 最终成果

完成 Day 6 后，项目从：

```text
MockLlmClient 固定返回模拟内容
```

升级为：

```text
OpenAI-compatible 大模型 API 真实生成标题、大纲、正文
```

完整流程：

```text
前端创建文章任务
↓
后端调用真实大模型生成标题候选
↓
用户选择标题
↓
后端调用真实大模型生成文章大纲
↓
后端调用真实大模型生成正文
↓
前端展示真实生成结果
↓
正文保存到 article.content
```

Day 6 完成后，你的项目已经从“Mock AI 应用”进入“真实 AI 应用雏形”。

---

## 1. Day 6 核心变化

Day 5 之前：

```text
TitleGeneratorAgent
OutlineGeneratorAgent
ContentGeneratorAgent
↓
LlmClient
↓
MockLlmClient
```

Day 6 之后：

```text
TitleGeneratorAgent
OutlineGeneratorAgent
ContentGeneratorAgent
↓
LlmClient
↓
MockLlmClient / OpenAiCompatibleLlmClient
```

也就是说：

```text
Mock 模式：适合没有 API Key、调试流程
Real 模式：适合真正调用大模型
```

本日新增后端文件：

```text
OpenAiCompatibleLlmClient.java
JsonExtractUtils.java
```

本日修改后端文件：

```text
application.yml
MockLlmClient.java
TitleGeneratorAgent.java
OutlineGeneratorAgent.java
ContentGeneratorAgent.java
```

本日修改前端文件：

```text
ArticleDetail.vue
```

---

# 第一部分：后端接入真实大模型

---

## 2. 修改 `application.yml`

打开：

```text
src/main/resources/application.yml
```

在文件末尾新增：

```yaml
llm:
  mode: mock
  base-url: https://api.minimax.chat
  api-key: ${LLM_API_KEY:}
  model: MiniMax-M2.5
  timeout-seconds: 120
```

字段解释：

| 字段 | 含义 |
|---|---|
| `llm.mode` | 模型模式，`mock` 表示使用 Mock，`real` 表示使用真实模型 |
| `llm.base-url` | OpenAI-compatible API 基础地址 |
| `llm.api-key` | API Key，从环境变量 `LLM_API_KEY` 读取 |
| `llm.model` | 模型名称 |
| `llm.timeout-seconds` | 请求超时时间 |

一开始建议保留：

```yaml
mode: mock
```

这样不会立刻影响现有项目。确认真实 API Key 配好后，再切换为：

```yaml
mode: real
```

---

## 3. MiniMax 配置说明

你测试后发现 MiniMax 使用下面这个地址可以正常生成：

```text
https://api.minimax.chat
```

所以当前推荐配置是：

```yaml
llm:
  mode: real
  base-url: https://api.minimax.chat
  api-key: ${LLM_API_KEY:}
  model: MiniMax-M2.5
  timeout-seconds: 120
```

如果你使用的是 MiniMax 新模型，也可以尝试：

```yaml
model: MiniMax-M3
```

注意：

```text
当前代码里会自动拼接 /v1/chat/completions。
```

所以：

```yaml
base-url: https://api.minimax.chat
```

不要写成：

```yaml
base-url: https://api.minimax.chat/v1
```

否则最终请求地址会变成：

```text
https://api.minimax.chat/v1/v1/chat/completions
```

---

## 4. 其他 OpenAI-compatible 服务配置参考

### 4.1 硅基流动 SiliconFlow

如果使用硅基流动：

```yaml
llm:
  mode: real
  base-url: https://api.siliconflow.cn
  api-key: ${LLM_API_KEY:}
  model: Qwen/Qwen3.5-27B
  timeout-seconds: 120
```

注意：

```text
base-url 不带 /v1。
```

因为代码会自动拼：

```java
String url = baseUrl + "/v1/chat/completions";
```

### 4.2 DeepSeek

如果使用 DeepSeek：

```yaml
llm:
  mode: real
  base-url: https://api.deepseek.com
  api-key: ${LLM_API_KEY:}
  model: deepseek-chat
  timeout-seconds: 120
```

实际模型名以平台模型列表为准。

---

# 第二部分：让 Mock 和真实模型可以切换

---

## 5. 修改 `MockLlmClient`

打开：

```text
src/main/java/com/nana/aiarticlestudio/agent/MockLlmClient.java
```

新增 import：

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
```

把类上的：

```java
@Component
public class MockLlmClient implements LlmClient {
```

改成：

```java
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {
```

作用：

```text
只有当 application.yml 中 llm.mode=mock 时，Spring 才启用 MockLlmClient。
如果没有配置 llm.mode，也默认启用 MockLlmClient。
```

为什么要这样做？

因为 Day 6 新增的真实模型客户端：

```text
OpenAiCompatibleLlmClient
```

也会实现：

```text
LlmClient
```

如果 `MockLlmClient` 和 `OpenAiCompatibleLlmClient` 同时生效，Spring 会不知道该注入哪一个，出现类似错误：

```text
required a single bean, but 2 were found
```

所以必须通过配置控制只启用其中一个。

---

## 6. 创建 `OpenAiCompatibleLlmClient`

在：

```text
src/main/java/com/nana/aiarticlestudio/agent
```

新建：

```text
OpenAiCompatibleLlmClient.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final ObjectMapper objectMapper;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout-seconds:60}")
    private long timeoutSeconds;

    @Override
    public String chat(String prompt) {
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
                    "temperature", 0.7
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

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("大模型调用失败，状态码：" + response.statusCode()
                        + "，响应：" + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());

            JsonNode contentNode = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode() || !StringUtils.hasText(contentNode.asText())) {
                throw new RuntimeException("大模型响应中没有 content：" + response.body());
            }

            return contentNode.asText();
        } catch (Exception e) {
            throw new RuntimeException("调用大模型失败：" + e.getMessage(), e);
        }
    }
}
```

作用：

```text
这个类负责真正调用 OpenAI-compatible 格式的大模型接口。
```

---

## 7. 理解 `OpenAiCompatibleLlmClient`

### 7.1 什么时候启用？

```java
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
```

含义：

```text
只有 application.yml 中 llm.mode=real 时，这个真实模型客户端才会生效。
```

### 7.2 API Key 从哪里来？

```java
@Value("${llm.api-key}")
private String apiKey;
```

`application.yml` 中写的是：

```yaml
api-key: ${LLM_API_KEY:}
```

意思是：

```text
从系统环境变量 LLM_API_KEY 中读取 API Key。
```

这样做的好处：

```text
1. API Key 不写死在代码里
2. API Key 不写死在 yml 里
3. 避免误上传 GitHub
```

### 7.3 请求地址怎么拼？

代码中：

```java
String url = baseUrl + "/v1/chat/completions";
```

所以如果你使用 MiniMax：

```yaml
base-url: https://api.minimax.chat
```

最终请求地址是：

```text
https://api.minimax.chat/v1/chat/completions
```

---

# 第三部分：配置 API Key

---

## 8. IDEA 配置环境变量

在 IDEA 中配置环境变量：

```text
右上角运行配置下拉框
↓
Edit Configurations...
↓
选择 AiArticleStudioApplication
↓
Environment variables
↓
新增：
LLM_API_KEY=你的真实 API Key
```

注意：

```text
不要写 Bearer
不要加引号
不要把 API Key 写进代码
```

正确示例：

```text
LLM_API_KEY=sk-xxxxxxxx
```

错误示例：

```text
LLM_API_KEY=Bearer sk-xxxxxxxx
```

---

## 9. 如果 IDEA 找不到 Spring Boot 配置

有些 IDEA 版本或者项目状态下，右上角只显示：

```text
Current File
```

且 `Edit Configurations` 中没有 Spring Boot。

可以手动创建：

```text
Edit Configurations...
↓
左上角 +
↓
Application
```

然后填写：

```text
Name:
AiArticleStudioApplication
```

```text
Main class:
com.nana.aiarticlestudio.AiArticleStudioApplication
```

```text
Working directory:
选择有 pom.xml 的目录
```

判断标准：

```text
哪个目录下面有 pom.xml，Working directory 就填哪个目录。
```

然后在 `Environment variables` 中填写：

```text
LLM_API_KEY=你的 API Key
```

如果看不到 `Environment variables`，点击：

```text
Modify options
```

勾选：

```text
Environment variables
```

最后：

```text
Apply
OK
```

然后用这个配置启动后端。

---

## 10. PowerShell 临时配置环境变量

如果暂时不想在 IDEA 配置，也可以用 PowerShell 启动后端。

进入后端目录：

```powershell
cd D:\Desktop\projects\ai-article-studio\backend
```

含义：

```text
进入 Spring Boot 后端项目目录。
```

设置环境变量：

```powershell
$env:LLM_API_KEY="你的真实APIKey"
```

含义：

```text
给当前 PowerShell 窗口临时设置 LLM_API_KEY。
关闭窗口后会失效。
```

启动后端：

```powershell
mvn spring-boot:run
```

含义：

```text
用 Maven 启动 Spring Boot 项目。
```

---

# 第四部分：切换到真实模型模式

---

## 11. 修改 `application.yml`

把：

```yaml
llm:
  mode: mock
```

改成：

```yaml
llm:
  mode: real
```

MiniMax 可用配置：

```yaml
llm:
  mode: real
  base-url: https://api.minimax.chat
  api-key: ${LLM_API_KEY:}
  model: MiniMax-M2.5
  timeout-seconds: 120
```

如果使用了别的模型名，就改：

```yaml
model: 你的模型名
```

修改完配置后，必须重启后端。

---

# 第五部分：增强真实模型 JSON 解析稳定性

---

## 12. 为什么需要 JSON 提取工具？

真实模型可能不会严格返回纯 JSON。

你希望它返回：

```json
[
  {
    "title": "标题",
    "reason": "理由"
  }
]
```

但它可能返回：

```text
好的，以下是生成结果：

```json
[
  {
    "title": "标题",
    "reason": "理由"
  }
]
```
```

这样直接使用：

```java
objectMapper.readValue(json, ...)
```

就会解析失败。

所以需要加一个工具：

```text
JsonExtractUtils
```

从模型返回文本里提取真正的 JSON 数组。

---

## 13. 创建 `JsonExtractUtils`

在：

```text
src/main/java/com/nana/aiarticlestudio/util
```

新建：

```text
JsonExtractUtils.java
```

代码：

```java
package com.nana.aiarticlestudio.util;

import org.springframework.util.StringUtils;

public class JsonExtractUtils {

    private JsonExtractUtils() {
    }

    public static String extractJsonArray(String text) {
        if (!StringUtils.hasText(text)) {
            throw new RuntimeException("模型返回为空");
        }

        String cleaned = text.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring("```json".length()).trim();
        }

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length()).trim();
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        int start = cleaned.indexOf("[");
        int end = cleaned.lastIndexOf("]");

        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("模型返回中未找到 JSON 数组：" + text);
        }

        return cleaned.substring(start, end + 1);
    }
}
```

作用：

```text
从模型返回文本中提取 JSON 数组。
即使模型返回了 ```json 包裹，也能提取出真正的数组部分。
```


---

## 14. 修改 `TitleGeneratorAgent`

打开：

```text
src/main/java/com/nana/aiarticlestudio/agent/TitleGeneratorAgent.java
```

新增 import：

```java
import com.nana.aiarticlestudio.util.JsonExtractUtils;
```

把原来的：

```java
String json = llmClient.chat(prompt);

return objectMapper.readValue(json, new TypeReference<List<TitleOption>>() {
});
```

改成：

```java
String response = llmClient.chat(prompt);
String json = JsonExtractUtils.extractJsonArray(response);

return objectMapper.readValue(json, new TypeReference<List<TitleOption>>() {
});
```

作用：

```text
先从模型原始输出中提取 JSON 数组，再交给 ObjectMapper 解析。
```

---

## 15. 修改 `OutlineGeneratorAgent`

打开：

```text
src/main/java/com/nana/aiarticlestudio/agent/OutlineGeneratorAgent.java
```

新增 import：

```java
import com.nana.aiarticlestudio.util.JsonExtractUtils;
```

把原来的：

```java
String json = llmClient.chat(prompt);

return objectMapper.readValue(json, new TypeReference<List<OutlineItem>>() {
});
```

改成：

```java
String response = llmClient.chat(prompt);
String json = JsonExtractUtils.extractJsonArray(response);

return objectMapper.readValue(json, new TypeReference<List<OutlineItem>>() {
});
```

作用：

```text
增强大纲 JSON 解析的稳定性。
```

---

# 第六部分：优化 Prompt

---

## 16. 优化 `TitleGeneratorAgent` Prompt

推荐把标题生成 Prompt 改成：

```java
String prompt = """
        你是一个专业的爆款文章标题策划专家。
        请根据用户选题生成 3 个标题候选。

        用户选题：%s
        文章风格：%s

        输出要求：
        1. 必须只输出 JSON 数组
        2. 不要输出 Markdown
        3. 不要输出 ```json
        4. 不要输出任何解释性文字
        5. 数组中必须有 3 个对象
        6. 每个对象必须包含 title 和 reason 两个字段

        JSON 格式示例：
        [
          {
            "title": "标题1",
            "reason": "推荐理由1"
          },
          {
            "title": "标题2",
            "reason": "推荐理由2"
          },
          {
            "title": "标题3",
            "reason": "推荐理由3"
          }
        ]
        """.formatted(topic, style);
```

作用：

```text
尽量约束模型只返回 JSON。
```

---

## 17. 优化 `OutlineGeneratorAgent` Prompt

推荐把大纲生成 Prompt 改成：

```java
String prompt = """
        你是一个专业的文章大纲策划专家。
        请根据用户选题和已选择标题生成文章大纲。

        用户选题：%s
        已选标题：%s
        文章风格：%s

        输出要求：
        1. 必须只输出 JSON 数组
        2. 不要输出 Markdown
        3. 不要输出 ```json
        4. 不要输出任何解释性文字
        5. 数组中必须有 5 个章节对象
        6. 每个对象必须包含 heading 和 description 两个字段

        JSON 格式示例：
        [
          {
            "heading": "一、章节标题",
            "description": "章节说明"
          },
          {
            "heading": "二、章节标题",
            "description": "章节说明"
          }
        ]
        """.formatted(topic, selectedTitle, style);
```

作用：

```text
降低模型乱输出的概率。
```

---

## 18. 优化 `ContentGeneratorAgent` Prompt

打开：

```text
src/main/java/com/nana/aiarticlestudio/agent/ContentGeneratorAgent.java
```

推荐把正文生成 Prompt 改成：

```java
String prompt = """
        你是一个专业的长文写作专家。
        请根据用户选题、已选标题和文章大纲，生成一篇结构清晰的 Markdown 正文。

        用户选题：%s
        已选标题：%s
        文章风格：%s
        文章大纲：%s

        输出要求：
        1. 只输出 Markdown 正文
        2. 不要输出任何解释性文字
        3. 不要输出“以下是正文”等前置说明
        4. 不要输出 <think>、</think> 或任何思考过程
        5. 必须包含一级标题
        6. 必须包含多个二级标题
        7. 每个章节至少包含 2 段正文
        8. 语言清晰，适合普通读者阅读

        请直接输出 Markdown 正文。
        """.formatted(topic, selectedTitle, style, outline);
```

作用：

```text
让正文更适合直接保存和展示。
```

---

# 第七部分：清洗模型思考内容

---

## 19. 为什么要清洗 `<think>...</think>`？

真实模型可能返回：

```text
<think>
The user wants me to write...
</think>

# 正文标题
正文内容...
```

如果前端直接展示，就会把模型思考过程也显示出来。

所以需要在后端清洗掉：

```text
<think>...</think>
```

---

## 20. 修改 `ContentGeneratorAgent`

在 `ContentGeneratorAgent` 中，把：

```java
return llmClient.chat(prompt);
```

改成：

```java
String result = llmClient.chat(prompt);

result = result.replaceAll("(?s)<think>.*?</think>", "").trim();

return result;
```

完整逻辑类似：

```java
public String generate(String topic, String selectedTitle, String outline, String style) {
    try {
        String prompt = """
                你是一个专业的长文写作专家。
                请根据用户选题、已选标题和文章大纲，生成一篇结构清晰的 Markdown 正文。

                用户选题：%s
                已选标题：%s
                文章风格：%s
                文章大纲：%s

                输出要求：
                1. 只输出 Markdown 正文
                2. 不要输出任何解释性文字
                3. 不要输出“以下是正文”等前置说明
                4. 不要输出 <think>、</think> 或任何思考过程
                5. 必须包含一级标题
                6. 必须包含多个二级标题
                7. 每个章节至少包含 2 段正文
                8. 语言清晰，适合普通读者阅读

                请直接输出 Markdown 正文。
                """.formatted(topic, selectedTitle, style, outline);

        String result = llmClient.chat(prompt);

        result = result.replaceAll("(?s)<think>.*?</think>", "").trim();

        return result;
    } catch (Exception e) {
        throw new RuntimeException("生成正文失败：" + e.getMessage());
    }
}
```

正则解释：

```java
"(?s)<think>.*?</think>"
```

含义：

```text
(?s)：让 . 可以匹配换行
<think>：匹配开始标签
.*?：非贪婪匹配中间所有内容
</think>：匹配结束标签
```

---

# 第八部分：前端按钮加载状态修复

---

## 21. 问题现象

真实 API 接入后，大模型响应时间明显变长。你发现：

```text
1. 点击生成标题时，多个按钮一起转圈
2. 点击确认标题并生成大纲时，多个按钮一起转圈
3. 后来改成独立 loading 后，按钮又不转圈
```

原因是：

```text
模板里绑定了 titleLoading / outlineLoading / contentLoading
但方法里仍然在修改旧的 loading.value
```

所以：

```text
按钮绑定的 loading 状态没有变化，自然不会转圈。
```

---

## 22. 定义独立 loading 状态

在 `ArticleDetail.vue` 中保留：

```ts
const loading = ref(false)
```

用于页面加载详情。

新增：

```ts
const titleLoading = ref(false)
const outlineLoading = ref(false)
const contentLoading = ref(false)
```

状态含义：

| 状态 | 作用 |
|---|---|
| `loading` | 页面详情加载状态 |
| `titleLoading` | 生成标题按钮加载状态 |
| `outlineLoading` | 生成大纲按钮加载状态 |
| `contentLoading` | 生成正文按钮加载状态 |
| `streaming` | 流式生成正文按钮加载状态 |

---

## 23. 修改 `handleGenerateTitles`

完整代码：

```ts
const handleGenerateTitles = async () => {
  if (!taskId.value) {
    return
  }

  titleLoading.value = true
  try {
    const res = await generateTitles(taskId.value)

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    article.value = res.data
    message.success('标题生成成功')
  } catch (error) {
    console.error(error)
    message.error('生成标题失败')
  } finally {
    titleLoading.value = false
  }
}
```

---

## 24. 修改 `handleConfirmTitle`

完整代码：

```ts
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
      return
    }

    article.value = res.data
    message.success('大纲生成成功')
  } catch (error) {
    console.error(error)
    message.error('生成大纲失败')
  } finally {
    outlineLoading.value = false
  }
}
```

---

## 25. 修改 `handleGenerateContent`

完整代码：

```ts
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
      return
    }

    article.value = res.data
    message.success('正文生成成功')
  } catch (error) {
    console.error(error)
    message.error('生成正文失败')
  } finally {
    contentLoading.value = false
  }
}
```

---

## 26. 修改按钮绑定

顶部按钮区域：

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
</a-space>
```

标题候选卡片里的按钮：

```vue
<a-button type="primary" :loading="outlineLoading" @click="handleConfirmTitle">
  确认标题并生成大纲
</a-button>
```

大纲卡片里的按钮：

```vue
<a-button type="primary" :loading="contentLoading" @click="handleGenerateContent">
  生成正文
</a-button>

<a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
  流式生成正文
</a-button>
```

修复后效果：

```text
点击生成标题 → 只有生成标题按钮转圈
点击确认标题并生成大纲 → 只有大纲按钮转圈
点击生成正文 → 只有生成正文按钮转圈
点击流式生成正文 → 只有流式生成正文按钮转圈
```

---

# 第九部分：正文按钮位置优化

---

## 27. 问题现象

你发现：

```text
确认标题并生成大纲后，大纲下面没有生成正文按钮，只有左上角有。
```

这样不符合用户操作路径。用户完成大纲后，视线在大纲区域，自然希望下一步按钮就在大纲下面。

---

## 28. 解决方式

在文章大纲卡片里加按钮：

```vue
<a-card v-if="outlineItems.length > 0" class="card" title="文章大纲">
  <a-timeline>
    <a-timeline-item v-for="item in outlineItems" :key="item.heading">
      <h3>{{ item.heading }}</h3>
      <p>{{ item.description }}</p>
    </a-timeline-item>
  </a-timeline>

  <div class="action-row">
    <a-button type="primary" :loading="contentLoading" @click="handleGenerateContent">
      生成正文
    </a-button>

    <a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
      流式生成正文
    </a-button>
  </div>
</a-card>
```

这样流程更自然：

```text
看完大纲
↓
直接点击生成正文
```

---

# 第十部分：常见 API 问题

---

## 29. 报错：401 invalid api key

报错示例：

```text
401 invalid api key
```

原因：

```text
认证失败，平台认为你传过去的 API Key 无效。
```

常见原因：

```text
1. IDEA 环境变量没有配置成功
2. 后端没有重启
3. API Key 填错了
4. 不小心写成了 Bearer sk-xxx
5. 用了错误平台的 Key
6. base-url 与 API Key 所属平台不匹配
```

解决方式：

```text
1. 确认 IDEA 里有 LLM_API_KEY=你的Key
2. 不要加 Bearer
3. 重启后端
4. 检查 base-url 是否是当前平台的地址
```

你这次 MiniMax 的问题最终通过换成：

```yaml
base-url: https://api.minimax.chat
```

解决了。

---

## 30. 报错：LLM_API_KEY 未配置

原因：

```text
后端启动时没有读到环境变量。
```

解决：

```text
1. IDEA Run Configuration 添加 LLM_API_KEY
2. Apply / OK
3. 停止后端
4. 重新启动后端
```

或者用 PowerShell 临时启动：

```powershell
$env:LLM_API_KEY="你的真实APIKey"
mvn spring-boot:run
```

---

## 31. 报错：2 beans of LlmClient

原因：

```text
MockLlmClient 和 OpenAiCompatibleLlmClient 同时生效。
```

解决：

确认 `MockLlmClient` 有：

```java
@ConditionalOnProperty(name = "llm.mode", havingValue = "mock", matchIfMissing = true)
```

确认 `OpenAiCompatibleLlmClient` 有：

```java
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
```

确认 `application.yml` 中只启用一种：

```yaml
llm:
  mode: real
```

或者：

```yaml
llm:
  mode: mock
```

---

## 32. 生成标题 / 大纲失败，JSON 解析错误

原因：

```text
真实模型没有严格返回 JSON 数组。
```

解决：

```text
1. 优化 Prompt，要求只输出 JSON 数组
2. 使用 JsonExtractUtils.extractJsonArray(response)
3. 必要时打印模型原始返回，查看具体格式
```

临时调试代码：

```java
System.out.println("模型原始返回：" + response);
```

注意：

```text
调试时可以打印模型返回，但不要打印 API Key。
```

---

## 33. 正文前面出现 `<think>...</think>`

原因：

```text
模型把思考过程也返回到了 content 里。
```

解决：

在 Prompt 中加入：

```text
不要输出 <think>、</think> 或任何思考过程。
```

并在后端兜底清洗：

```java
result = result.replaceAll("(?s)<think>.*?</think>", "").trim();
```

---

## 34. API 太慢

真实模型生成会比 Mock 慢很多，尤其是大纲和正文。

可以做：

```text
1. timeout-seconds 改成 120
2. 前端按钮加 loading
3. 使用 SSE 展示生成进度
4. 选择速度更快的模型
5. 后续实现真正 token 级流式
```

---

# 第十一部分：测试流程

---

## 35. 后端启动前检查

确认 `application.yml`：

```yaml
llm:
  mode: real
  base-url: https://api.minimax.chat
  api-key: ${LLM_API_KEY:}
  model: MiniMax-M2.5
  timeout-seconds: 120
```

确认 IDEA 环境变量：

```text
LLM_API_KEY=你的真实APIKey
```

然后重启后端。

---

## 36. 前端测试

打开：

```text
http://localhost:5173/article/list
```

测试流程：

```text
1. 创建一个新文章任务
2. 点击查看详情
3. 点击生成标题
4. 等待真实模型返回标题候选
5. 选择标题
6. 点击确认标题并生成大纲
7. 等待真实模型返回大纲
8. 点击生成正文或流式生成正文
9. 查看正文是否正常展示
10. 刷新页面，确认正文仍然存在
```

如果这些都通过，说明 Day 6 完成。

---

# 第十二部分：数据库验收

---

## 37. 查询最新文章状态

进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

进入后执行：

```sql
SET NAMES utf8mb4;
USE ai_article_studio;
```

查询：

```sql
SELECT
  task_id,
  topic,
  phase,
  status,
  CHAR_LENGTH(title_options) AS title_len,
  CHAR_LENGTH(outline) AS outline_len,
  CHAR_LENGTH(content) AS content_len
FROM article
ORDER BY create_time DESC
LIMIT 5;
```

验收标准：

```text
title_len > 0
outline_len > 0
content_len > 0
phase = CONTENT_GENERATION
status = SUCCESS
```

查看正文预览：

```sql
SELECT
  task_id,
  LEFT(content, 500) AS content_preview
FROM article
ORDER BY create_time DESC
LIMIT 1;
```

---

# 第十三部分：Day 6 验收标准

Day 6 完成标准：

```text
1. MockLlmClient 支持通过 llm.mode=mock 启用
2. OpenAiCompatibleLlmClient 支持通过 llm.mode=real 启用
3. API Key 通过 LLM_API_KEY 环境变量读取
4. MiniMax API 可以正常调用
5. TitleGeneratorAgent 能调用真实模型生成标题
6. OutlineGeneratorAgent 能调用真实模型生成大纲
7. ContentGeneratorAgent 能调用真实模型生成正文
8. 前端按钮 loading 状态正常
9. 正文不再显示 <think> 思考内容
10. 前端完整流程可以跑通
11. 数据库 article.title_options 有真实生成标题
12. 数据库 article.outline 有真实生成大纲
13. 数据库 article.content 有真实生成正文
```

---

# 第十四部分：Day 6 完成后的项目状态

到 Day 6 后，项目已经从：

```text
Mock AI 应用
```

升级为：

```text
真实 AI 应用雏形
```

当前完整能力：

```text
Vue 前端
Spring Boot 后端
MySQL 持久化
Agent 分层
真实大模型调用
标题生成
大纲生成
正文生成
SSE 生成过程展示
Markdown 正文展示
按钮级 loading 体验
```

---

# 第十五部分：Day 7 预告

Day 7 建议优先做：

```text
Agent 执行日志
```

原因：

```text
接入真实模型后，调试 Prompt 和模型输出变得很重要。
```

Day 7 可以做：

```text
1. 把每次 Agent 输入输出写入 agent_log
2. 记录 agent_name
3. 记录 input_text
4. 记录 output_text
5. 记录 status
6. 记录 cost_ms
7. 记录 error_message
8. 在前端详情页展示 Agent 执行记录
```

另一个可选方向：

```text
真正 token 级流式输出
```

但建议先做 Agent 日志，再做真正流式。因为有了日志后，更容易排查真实模型输出问题。
