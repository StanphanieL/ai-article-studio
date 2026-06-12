# AI Article Studio Day16：多来源图片结果生成与工作流接入

> 本文为更正后的 Day16 总结。  
> Day16 的范围包括：Pexels 搜图、Gemini 生图接口、SiliconFlow 生图接口、图片结果统一封装、前后端来源选择、图片本地持久化，以及将图片结果生成接入“继续下一步”工作流。

---

# 1. Day16 最终目标

在 Day15 中，系统已经能够生成文章配图提示词：

```text
正文 content
        ↓
ImagePromptAgent
        ↓
image_prompts
```

Day16 要继续完成：

```text
image_prompts
        ↓
ImageResultAgent
        ↓
真实图片或 AI 生成图片
        ↓
image_results
        ↓
前端图片结果展示
```

最终系统支持四种图片来源：

```text
AUTO
PEXELS
GOOGLE_AI
SILICONFLOW
```

整体架构：

```text
ImageResultAgent
├─ PEXELS
│  └─ PexelsImageClient
├─ GOOGLE_AI
│  └─ GoogleImagenClient
├─ SILICONFLOW
│  └─ SiliconFlowImageClient
└─ AUTO
   └─ Pexels → SiliconFlow → fallback
```

当前各服务职责：

```text
MiniMax
    负责标题、大纲、正文、配图提示词等文本生成

Pexels
    负责搜索真实摄影图片

Gemini
    保留 AI 生图接口，后续继续处理额度与代理问题

SiliconFlow
    当前主要 AI 生图服务

fallback
    当自动流程中的图片服务都失败时提供占位图
```

---

# 2. Day16 完成后的完整流程

```text
创建任务
→ 生成标题
→ 选择标题
→ 生成大纲
→ 生成正文
→ 生成配图提示词
→ 选择图片来源
→ 生成图片结果
→ 保存 image_results
→ 前端展示图片
```

“继续下一步”也能够自动推进到图片结果阶段：

```text
content 为空
    → 生成正文

content 已存在、image_prompts 为空
    → 生成配图提示词

image_prompts 已存在、image_results 为空
    → 生成图片结果

image_results 已存在
    → 当前阶段完成
```

---

# 3. 环境变量配置

在 IDEA 中打开：

```text
Run
→ Edit Configurations
→ AiArticleStudioApplication
→ Environment variables
```

配置以下环境变量：

```text
LLM_API_KEY=你的MiniMaxKey;PEXELS_API_KEY=你的PexelsKey;GOOGLE_AI_API_KEY=你的GeminiKey;SILICONFLOW_API_KEY=你的SiliconFlowKey
```

各变量职责：

```text
LLM_API_KEY
    MiniMax 文本模型 API Key

PEXELS_API_KEY
    Pexels 图片搜索 API Key

GOOGLE_AI_API_KEY
    Google AI Studio / Gemini API Key

SILICONFLOW_API_KEY
    SiliconFlow 生图 API Key
```

注意事项：

```text
1. 每组变量使用英文分号 ; 分隔
2. 不要使用中文分号
3. 不要换行
4. 不要在等号两边添加多余空格
5. 修改后必须重启 Spring Boot
6. 不要将完整 API Key 提交到 GitHub
7. SiliconFlow Key 不要复用 MiniMax 的 LLM_API_KEY
```

---

# 4. application.yml 配置

打开：

```text
backend/src/main/resources/application.yml
```

在原有配置基础上加入图片服务配置。

参考结构：

```yaml
llm:
  base-url: https://api.minimax.chat
  api-key: ${LLM_API_KEY:}
  model: 你的MiniMax模型名

app:
  backend-base-url: http://localhost:8123

  pexels:
    api-key: ${PEXELS_API_KEY:}

  google:
    api-key: ${GOOGLE_AI_API_KEY:}
    image-model: gemini-2.5-flash-image

  siliconflow:
    api-key: ${SILICONFLOW_API_KEY:}
    base-url: https://api.siliconflow.cn/v1
    image-model: Kwai-Kolors/Kolors
    image-size: 1024x1024
    batch-size: 1
    num-inference-steps: 20
    guidance-scale: 7.5
    negative-prompt: low quality, blurry, distorted, deformed, watermark, text artifacts

  image:
    local-dir: uploads/images
```

注意：

```text
1. MiniMax 仍然是文本模型
2. Pexels、Gemini、SiliconFlow 是三条独立图片通道
3. 不要删除 google 配置
4. 不要创建两个 app: 节点
5. 所有 app.* 配置必须位于同一个 app: 下
```

---

# 5. 数据库增加 image_results

如果之前还没有增加该字段，进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p
```

命令含义：

```text
进入 ai_article_mysql 容器中的 MySQL 客户端，
并使用 utf8mb4 字符集连接。
```

执行：

```sql
USE ai_article_studio;

ALTER TABLE article
ADD COLUMN image_results MEDIUMTEXT NULL
COMMENT '图片结果 JSON'
AFTER image_prompts;
```

检查：

```sql
DESC article;
```

应看到：

```text
image_results
```

---

# 6. 修改 Article 实体

打开：

```text
backend/src/main/java/com/nana/aiarticlestudio/model/entity/Article.java
```

新增：

```java
private String imageResults;
```

---

# 7. 修改 ArticleVO

打开：

```text
backend/src/main/java/com/nana/aiarticlestudio/model/vo/ArticleVO.java
```

新增：

```java
private String imageResults;
```

实体与 VO 字段名称保持一致：

```text
数据库：image_results
Java：imageResults
```

---

# 8. 新增 ImageResultOption

创建：

```text
backend/src/main/java/com/nana/aiarticlestudio/model/vo/ImageResultOption.java
```

完整代码：

```java
package com.nana.aiarticlestudio.model.vo;

import lombok.Data;

@Data
public class ImageResultOption {

    /**
     * 图片标题。
     */
    private String imageTitle;

    /**
     * 图片使用场景。
     */
    private String usageScene;

    /**
     * 前端展示的图片 URL。
     */
    private String imageUrl;

    /**
     * 中文配图提示词。
     */
    private String promptZh;

    /**
     * 英文配图提示词。
     */
    private String promptEn;

    /**
     * PEXELS / GOOGLE_AI / SILICONFLOW / FALLBACK。
     */
    private String source;

    /**
     * 图片原始来源地址。
     */
    private String sourceUrl;

    /**
     * 图片作者或生成平台。
     */
    private String author;

    /**
     * 作者或平台地址。
     */
    private String authorUrl;
}
```

统一结果结构的意义：

```text
无论图片来自 Pexels、Gemini 还是 SiliconFlow，
Service 和前端都只处理 ImageResultOption。
```

---

# 9. 修改 ArticleMapper

打开：

```text
backend/src/main/java/com/nana/aiarticlestudio/mapper/ArticleMapper.java
```

新增：

```java
@Update("""
        UPDATE article
        SET image_results = #{imageResults},
            phase = #{phase},
            status = #{status},
            error_message = NULL
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateImageResults(
        @Param("taskId") String taskId,
        @Param("imageResults") String imageResults,
        @Param("phase") String phase,
        @Param("status") String status
);
```

作用：

```text
将图片结果 JSON 保存到 article.image_results，
并同步更新任务阶段、状态和错误信息。
```

---

# 10. 配置本地图片访问

Gemini 和 SiliconFlow 生成的图片需要保存到本地：

```text
backend/uploads/images
```

创建或检查：

```text
backend/src/main/java/com/nana/aiarticlestudio/config/WebMvcConfig.java
```

代码：

```java
package com.nana.aiarticlestudio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.image.local-dir:uploads/images}")
    private String imageLocalDir;

    @Override
    public void addResourceHandlers(
            ResourceHandlerRegistry registry
    ) {
        Path imagePath = Paths.get(imageLocalDir)
                .toAbsolutePath()
                .normalize();

        registry.addResourceHandler(
                        "/uploads/images/**"
                )
                .addResourceLocations(
                        imagePath.toUri().toString() + "/"
                );
    }
}
```

本地文件：

```text
backend/uploads/images/example.png
```

将映射为：

```text
http://localhost:8123/uploads/images/example.png
```

---

# 11. PexelsImageClient

创建或检查：

```text
backend/src/main/java/com/nana/aiarticlestudio/agent/PexelsImageClient.java
```

Pexels 的职责：

```text
根据配图提示词搜索真实图片
→ 解析图片 URL
→ 保存作者和来源信息
→ 返回 ImageResultOption
```

参考实现：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class PexelsImageClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.pexels.api-key:}")
    private String apiKey;

    public PexelsImageClient(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public ImageResultOption searchPhoto(
            ImagePromptOption promptOption
    ) throws Exception {

        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException(
                    "PEXELS_API_KEY 未配置"
            );
        }

        String query = buildQuery(promptOption);

        String encodedQuery = URLEncoder.encode(
                query,
                StandardCharsets.UTF_8
        );

        String url =
                "https://api.pexels.com/v1/search"
                        + "?query=" + encodedQuery
                        + "&per_page=1"
                        + "&orientation=landscape";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Authorization",
                        apiKey
                )
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Pexels 请求失败，状态码："
                            + response.statusCode()
                            + "，响应："
                            + response.body()
            );
        }

        JsonNode root =
                objectMapper.readTree(response.body());

        JsonNode photos =
                root.path("photos");

        if (!photos.isArray()
                || photos.isEmpty()) {
            throw new RuntimeException(
                    "Pexels 未搜索到可用图片"
            );
        }

        JsonNode photo = photos.get(0);

        String imageUrl =
                photo.path("src")
                        .path("large2x")
                        .asText();

        if (!StringUtils.hasText(imageUrl)) {
            imageUrl = photo.path("src")
                    .path("large")
                    .asText();
        }

        ImageResultOption result =
                new ImageResultOption();

        result.setImageTitle(
                promptOption.getImageTitle()
        );
        result.setUsageScene(
                promptOption.getUsageScene()
        );
        result.setPromptZh(
                promptOption.getPromptZh()
        );
        result.setPromptEn(
                promptOption.getPromptEn()
        );

        result.setImageUrl(imageUrl);
        result.setSource("PEXELS");
        result.setSourceUrl(
                photo.path("url").asText()
        );
        result.setAuthor(
                photo.path("photographer").asText()
        );
        result.setAuthorUrl(
                photo.path("photographer_url").asText()
        );

        return result;
    }

    private String buildQuery(
            ImagePromptOption promptOption
    ) {
        if (StringUtils.hasText(
                promptOption.getPromptEn()
        )) {
            return promptOption.getPromptEn();
        }

        if (StringUtils.hasText(
                promptOption.getPromptZh()
        )) {
            return promptOption.getPromptZh();
        }

        return promptOption.getImageTitle();
    }
}
```

说明：

```text
Pexels 图片本身由远端托管，
第一版可以直接保存其图片 URL。

同时应保留：
作者、作者主页、图片来源页面。
```

---

# 12. GoogleImagenClient

Gemini 生图接口保留，不删除。

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/agent/GoogleImagenClient.java
```

当前项目使用的调用形式：

```text
POST
https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
```

其中模型配置来自：

```yaml
app:
  google:
    image-model: gemini-2.5-flash-image
```

参考实现：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class GoogleImagenClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.google.api-key:}")
    private String apiKey;

    @Value("${app.google.image-model:gemini-2.5-flash-image}")
    private String imageModel;

    @Value("${app.image.local-dir:uploads/images}")
    private String imageLocalDir;

    @Value("${app.backend-base-url:http://localhost:8123}")
    private String backendBaseUrl;

    public GoogleImagenClient(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public ImageResultOption generateImage(
            ImagePromptOption promptOption
    ) throws Exception {

        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException(
                    "GOOGLE_AI_API_KEY 未配置"
            );
        }

        String prompt = buildPrompt(promptOption);

        String url =
                "https://generativelanguage.googleapis.com"
                        + "/v1beta/models/"
                        + imageModel
                        + ":generateContent";

        Map<String, Object> requestBody =
                Map.of(
                        "contents",
                        new Object[]{
                                Map.of(
                                        "parts",
                                        new Object[]{
                                                Map.of(
                                                        "text",
                                                        prompt
                                                )
                                        }
                                )
                        }
                );

        String requestJson =
                objectMapper.writeValueAsString(requestBody);

        System.out.println(
                "准备请求 Gemini 生图接口：" + url
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header(
                        "x-goog-api-key",
                        apiKey
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                requestJson
                        )
                )
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            System.out.println(
                    "Gemini image request error class = "
                            + e.getClass().getName()
            );
            System.out.println(
                    "Gemini image request error message = "
                            + e.getMessage()
            );
            e.printStackTrace();
            throw e;
        }

        System.out.println(
                "Gemini image status = "
                        + response.statusCode()
        );
        System.out.println(
                "Gemini image body = "
                        + response.body()
        );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Google Imagen 请求失败，状态码："
                            + response.statusCode()
                            + "，响应："
                            + response.body()
            );
        }

        String base64Image =
                extractBase64(response.body());

        String localImageUrl =
                saveBase64Image(base64Image);

        ImageResultOption result =
                new ImageResultOption();

        result.setImageTitle(
                promptOption.getImageTitle()
        );
        result.setUsageScene(
                promptOption.getUsageScene()
        );
        result.setPromptZh(
                promptOption.getPromptZh()
        );
        result.setPromptEn(
                promptOption.getPromptEn()
        );
        result.setImageUrl(localImageUrl);
        result.setSource("GOOGLE_AI");
        result.setSourceUrl("");
        result.setAuthor(
                "Google Gemini / " + imageModel
        );
        result.setAuthorUrl("");

        return result;
    }

    private String buildPrompt(
            ImagePromptOption promptOption
    ) {
        if (StringUtils.hasText(
                promptOption.getPromptEn()
        )) {
            return promptOption.getPromptEn();
        }

        if (StringUtils.hasText(
                promptOption.getPromptZh()
        )) {
            return promptOption.getPromptZh();
        }

        return promptOption.getImageTitle();
    }

    private String extractBase64(
            String responseBody
    ) throws Exception {

        JsonNode root =
                objectMapper.readTree(responseBody);

        JsonNode parts =
                root.at(
                        "/candidates/0/content/parts"
                );

        if (parts.isArray()) {
            for (JsonNode part : parts) {
                JsonNode inlineData =
                        part.path("inlineData");

                if (!inlineData.isMissingNode()) {
                    String data =
                            inlineData.path("data")
                                    .asText();

                    if (StringUtils.hasText(data)) {
                        return data;
                    }
                }

                JsonNode inlineDataSnake =
                        part.path("inline_data");

                if (!inlineDataSnake.isMissingNode()) {
                    String data =
                            inlineDataSnake.path("data")
                                    .asText();

                    if (StringUtils.hasText(data)) {
                        return data;
                    }
                }
            }
        }

        throw new RuntimeException(
                "Gemini 返回中未找到图片数据"
        );
    }

    private String saveBase64Image(
            String base64Image
    ) throws Exception {

        byte[] bytes =
                Base64.getDecoder()
                        .decode(base64Image);

        Path directory =
                Paths.get(imageLocalDir)
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(directory);

        String fileName =
                "gemini-"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        + ".png";

        Files.write(
                directory.resolve(fileName),
                bytes
        );

        return removeTrailingSlash(backendBaseUrl)
                + "/uploads/images/"
                + fileName;
    }

    private String removeTrailingSlash(
            String value
    ) {
        String result = value.trim();

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }
}
```

## 12.1 Gemini 调试中遇到的问题

### 429 quota exceeded

典型错误：

```text
RESOURCE_EXHAUSTED
Quota exceeded
limit: 0
```

说明：

```text
请求已经到达 Google，
但当前项目对该模型没有可用额度。
```

这不是代码结构错误。

### ConnectException

典型错误：

```text
java.net.ConnectException
```

说明：

```text
Java 后端没有成功连接 Google 服务，
通常与代理有关。
```

### SSLHandshakeException

典型错误：

```text
SSLHandshakeException:
Remote host terminated the handshake
```

说明：

```text
Java 已尝试建立 HTTPS 连接，
但 TLS 握手被代理或远端中断。
```

Gemini 当前处理策略：

```text
保留 GOOGLE_AI 接口和代码，
暂时不放入 AUTO 主流程，
后续有额度和网络条件后继续测试。
```

---

# 13. SiliconFlowImageClient

SiliconFlow 不是替换 Gemini，而是新增独立通道。

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/agent/SiliconFlowImageClient.java
```

SiliconFlow 官方接口：

```text
POST https://api.siliconflow.cn/v1/images/generations
```

核心职责：

```text
调用 SiliconFlow
→ 获取 images[0].url
→ 立即下载临时图片
→ 保存到 uploads/images
→ 返回本地 URL
```

完整实现：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class SiliconFlowImageClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.siliconflow.api-key:}")
    private String apiKey;

    @Value("${app.siliconflow.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${app.siliconflow.image-model:Kwai-Kolors/Kolors}")
    private String imageModel;

    @Value("${app.siliconflow.image-size:1024x1024}")
    private String imageSize;

    @Value("${app.siliconflow.batch-size:1}")
    private Integer batchSize;

    @Value("${app.siliconflow.num-inference-steps:20}")
    private Integer numInferenceSteps;

    @Value("${app.siliconflow.guidance-scale:7.5}")
    private Double guidanceScale;

    @Value("${app.siliconflow.negative-prompt:}")
    private String negativePrompt;

    @Value("${app.image.local-dir:uploads/images}")
    private String imageLocalDir;

    @Value("${app.backend-base-url:http://localhost:8123}")
    private String backendBaseUrl;

    public SiliconFlowImageClient(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(
                        HttpClient.Redirect.NORMAL
                )
                .version(
                        HttpClient.Version.HTTP_1_1
                )
                .build();
    }

    public ImageResultOption generateImage(
            ImagePromptOption promptOption
    ) throws Exception {

        validateConfig();

        String prompt =
                buildPrompt(promptOption);

        String endpoint =
                removeTrailingSlash(baseUrl)
                        + "/images/generations";

        Map<String, Object> requestBody =
                new LinkedHashMap<>();

        requestBody.put(
                "model",
                imageModel
        );
        requestBody.put(
                "prompt",
                prompt
        );
        requestBody.put(
                "image_size",
                imageSize
        );
        requestBody.put(
                "batch_size",
                batchSize
        );
        requestBody.put(
                "num_inference_steps",
                numInferenceSteps
        );
        requestBody.put(
                "guidance_scale",
                guidanceScale
        );

        if (StringUtils.hasText(
                negativePrompt
        )) {
            requestBody.put(
                    "negative_prompt",
                    negativePrompt
            );
        }

        String requestJson =
                objectMapper.writeValueAsString(
                        requestBody
                );

        System.out.println(
                "准备请求 SiliconFlow 生图接口："
                        + endpoint
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMinutes(3))
                .header(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                requestJson
                        )
                )
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        String traceId =
                response.headers()
                        .firstValue(
                                "x-siliconcloud-trace-id"
                        )
                        .orElse("");

        System.out.println(
                "SiliconFlow status = "
                        + response.statusCode()
        );
        System.out.println(
                "SiliconFlow traceId = "
                        + traceId
        );
        System.out.println(
                "SiliconFlow body = "
                        + response.body()
        );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new RuntimeException(
                    "SiliconFlow 生图请求失败，状态码："
                            + response.statusCode()
                            + "，traceId："
                            + traceId
                            + "，响应："
                            + response.body()
            );
        }

        String temporaryImageUrl =
                extractImageUrl(
                        response.body()
                );

        String localImageUrl =
                downloadAndSaveImage(
                        temporaryImageUrl
                );

        ImageResultOption result =
                new ImageResultOption();

        result.setImageTitle(
                promptOption.getImageTitle()
        );
        result.setUsageScene(
                promptOption.getUsageScene()
        );
        result.setPromptZh(
                promptOption.getPromptZh()
        );
        result.setPromptEn(
                promptOption.getPromptEn()
        );

        result.setImageUrl(localImageUrl);
        result.setSource("SILICONFLOW");
        result.setSourceUrl("");
        result.setAuthor(
                "SiliconFlow / " + imageModel
        );
        result.setAuthorUrl("");

        return result;
    }

    private void validateConfig() {
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException(
                    "未读取到 SILICONFLOW_API_KEY"
            );
        }
    }

    private String buildPrompt(
            ImagePromptOption promptOption
    ) {
        if (StringUtils.hasText(
                promptOption.getPromptEn()
        )) {
            return promptOption
                    .getPromptEn()
                    .trim();
        }

        if (StringUtils.hasText(
                promptOption.getPromptZh()
        )) {
            return promptOption
                    .getPromptZh()
                    .trim();
        }

        if (StringUtils.hasText(
                promptOption.getImageTitle()
        )) {
            return promptOption
                    .getImageTitle()
                    .trim();
        }

        throw new RuntimeException(
                "没有可用于生图的提示词"
        );
    }

    private String extractImageUrl(
            String responseBody
    ) throws Exception {

        JsonNode root =
                objectMapper.readTree(
                        responseBody
                );

        JsonNode images =
                root.path("images");

        if (!images.isArray()
                || images.isEmpty()) {
            throw new RuntimeException(
                    "SiliconFlow 响应中没有 images 数组"
            );
        }

        String imageUrl =
                images.get(0)
                        .path("url")
                        .asText();

        if (!StringUtils.hasText(imageUrl)) {
            throw new RuntimeException(
                    "SiliconFlow 响应中没有 images[0].url"
            );
        }

        return imageUrl;
    }

    private String downloadAndSaveImage(
            String temporaryImageUrl
    ) throws Exception {

        HttpRequest downloadRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        temporaryImageUrl
                                )
                        )
                        .timeout(
                                Duration.ofMinutes(2)
                        )
                        .GET()
                        .build();

        HttpResponse<byte[]> imageResponse =
                httpClient.send(
                        downloadRequest,
                        HttpResponse.BodyHandlers
                                .ofByteArray()
                );

        if (imageResponse.statusCode() < 200
                || imageResponse.statusCode() >= 300) {

            throw new RuntimeException(
                    "下载 SiliconFlow 图片失败，状态码："
                            + imageResponse.statusCode()
            );
        }

        String contentType =
                imageResponse.headers()
                        .firstValue("Content-Type")
                        .orElse("image/png");

        String extension =
                resolveExtension(contentType);

        Path imageDirectory =
                Paths.get(imageLocalDir)
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(
                imageDirectory
        );

        String fileName =
                "siliconflow-"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        + extension;

        Path imagePath =
                imageDirectory.resolve(
                        fileName
                );

        Files.write(
                imagePath,
                imageResponse.body(),
                StandardOpenOption.CREATE_NEW
        );

        return removeTrailingSlash(
                backendBaseUrl
        ) + "/uploads/images/" + fileName;
    }

    private String resolveExtension(
            String contentType
    ) {
        String normalized =
                contentType.toLowerCase(
                        Locale.ROOT
                );

        if (normalized.contains("jpeg")
                || normalized.contains("jpg")) {
            return ".jpg";
        }

        if (normalized.contains("webp")) {
            return ".webp";
        }

        return ".png";
    }

    private String removeTrailingSlash(
            String value
    ) {
        String result = value.trim();

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }
}
```

为什么必须下载到本地：

```text
SiliconFlow 返回的是临时图片 URL，
过期后前端将无法继续显示。

因此必须：
远程临时 URL
→ 下载
→ 保存本地
→ 数据库保存本地 URL
```

---

# 14. 新增 ImageResultAgent

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/agent/ImageResultAgent.java
```

职责：

```text
解析 image_prompts
→ 根据 provider 路由图片服务
→ 统一返回 ImageResultOption 列表
```

完整参考代码：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ImageResultAgent {

    private final ObjectMapper objectMapper;

    private final PexelsImageClient
            pexelsImageClient;

    private final GoogleImagenClient
            googleImagenClient;

    private final SiliconFlowImageClient
            siliconFlowImageClient;

    public List<ImageResultOption> generate(
            String imagePromptsJson,
            String provider
    ) throws Exception {

        if (!StringUtils.hasText(
                imagePromptsJson
        )) {
            throw new RuntimeException(
                    "配图提示词为空"
            );
        }

        List<ImagePromptOption> prompts =
                objectMapper.readValue(
                        imagePromptsJson,
                        new TypeReference<
                                List<ImagePromptOption>
                                >() {
                        }
                );

        List<ImageResultOption> results =
                new ArrayList<>();

        for (int i = 0;
             i < prompts.size();
             i++) {

            ImageResultOption result =
                    generateOne(
                            prompts.get(i),
                            provider,
                            i
                    );

            results.add(result);
        }

        return results;
    }

    private ImageResultOption generateOne(
            ImagePromptOption prompt,
            String provider,
            int index
    ) throws Exception {

        String normalizedProvider =
                StringUtils.hasText(provider)
                        ? provider
                        .trim()
                        .toUpperCase()
                        : "AUTO";

        if ("PEXELS".equals(
                normalizedProvider
        )) {
            return pexelsImageClient
                    .searchPhoto(prompt);
        }

        if ("GOOGLE_AI".equals(
                normalizedProvider
        )) {
            return googleImagenClient
                    .generateImage(prompt);
        }

        if ("SILICONFLOW".equals(
                normalizedProvider
        )) {
            return siliconFlowImageClient
                    .generateImage(prompt);
        }

        if ("AUTO".equals(
                normalizedProvider
        )) {
            try {
                return pexelsImageClient
                        .searchPhoto(prompt);
            } catch (Exception pexelsException) {

                System.out.println(
                        "Pexels 失败，切换 SiliconFlow："
                                + pexelsException
                                .getMessage()
                );

                try {
                    return siliconFlowImageClient
                            .generateImage(prompt);
                } catch (
                        Exception siliconException
                ) {
                    System.out.println(
                            "SiliconFlow 失败，使用 fallback："
                                    + siliconException
                                    .getMessage()
                    );

                    return fallback(
                            prompt,
                            index
                    );
                }
            }
        }

        throw new RuntimeException(
                "不支持的图片来源："
                        + provider
        );
    }

    private ImageResultOption fallback(
            ImagePromptOption prompt,
            int index
    ) {
        ImageResultOption result =
                new ImageResultOption();

        result.setImageTitle(
                prompt.getImageTitle()
        );
        result.setUsageScene(
                prompt.getUsageScene()
        );
        result.setPromptZh(
                prompt.getPromptZh()
        );
        result.setPromptEn(
                prompt.getPromptEn()
        );

        result.setImageUrl(
                "https://picsum.photos/seed/"
                        + "ai-article-"
                        + index
                        + "/900/500"
        );

        result.setSource("FALLBACK");
        result.setSourceUrl("");
        result.setAuthor(
                "Picsum Placeholder"
        );
        result.setAuthorUrl("");

        return result;
    }
}
```

当前 AUTO 策略：

```text
Pexels
    ↓ 失败
SiliconFlow
    ↓ 失败
fallback
```

Gemini 保留为手动选择：

```text
provider=GOOGLE_AI
```

后续 Gemini 可用后，可以调整为：

```text
Pexels
→ SiliconFlow
→ Gemini
→ fallback
```

---

# 15. 修改 ArticleService

打开：

```text
backend/src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

新增：

```java
ArticleVO generateImageResults(
        String taskId,
        String provider
);
```

---

# 16. 修改 ArticleServiceImpl

确保已经注入：

```java
private final ImageResultAgent imageResultAgent;
```

新增方法：

```java
@Override
public ArticleVO generateImageResults(
        String taskId,
        String provider
) {
    long start =
            System.currentTimeMillis();

    String inputText = null;
    String outputText = null;

    try {
        Article article =
                articleMapper.selectByTaskId(
                        taskId
                );

        if (article == null) {
            throw new RuntimeException(
                    "文章任务不存在"
            );
        }

        if (!StringUtils.hasText(
                article.getImagePrompts()
        )) {
            throw new RuntimeException(
                    "请先生成配图提示词"
            );
        }

        inputText =
                "provider="
                        + provider
                        + "\n\n"
                        + article.getImagePrompts();

        List<ImageResultOption> imageResults =
                imageResultAgent.generate(
                        article.getImagePrompts(),
                        provider
                );

        String imageResultsJson =
                objectMapper.writeValueAsString(
                        imageResults
                );

        outputText = imageResultsJson;

        int updated =
                articleMapper.updateImageResults(
                        taskId,
                        imageResultsJson,
                        ArticlePhase
                                .CONTENT_GENERATION
                                .name(),
                        ArticleStatus
                                .SUCCESS
                                .name()
                );

        if (updated != 1) {
            throw new RuntimeException(
                    "保存图片结果失败"
            );
        }

        long costMs =
                System.currentTimeMillis()
                        - start;

        agentLogService.saveSuccess(
                taskId,
                "ImageResultAgent",
                inputText,
                outputText,
                costMs
        );

        return getByTaskId(taskId);

    } catch (Exception e) {
        long costMs =
                System.currentTimeMillis()
                        - start;

        agentLogService.saveFailed(
                taskId,
                "ImageResultAgent",
                inputText,
                e.getMessage(),
                costMs
        );

        articleMapper.updateFailedStatus(
                taskId,
                ArticlePhase
                        .CONTENT_GENERATION
                        .name(),
                ArticleStatus
                        .FAILED
                        .name(),
                e.getMessage()
        );

        throw new RuntimeException(
                "生成图片结果失败："
                        + e.getMessage(),
                e
        );
    }
}
```

---

# 17. 修改 ArticleController

打开：

```text
backend/src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增：

```java
@PostMapping(
        "/generate-image-results/{taskId}"
)
public BaseResponse<ArticleVO>
generateImageResults(
        @PathVariable String taskId,
        @RequestParam(
                defaultValue = "AUTO"
        ) String provider
) {
    return BaseResponse.success(
            articleService
                    .generateImageResults(
                            taskId,
                            provider
                    )
    );
}
```

接口：

```text
POST /api/article/generate-image-results/{taskId}?provider=AUTO
POST /api/article/generate-image-results/{taskId}?provider=PEXELS
POST /api/article/generate-image-results/{taskId}?provider=GOOGLE_AI
POST /api/article/generate-image-results/{taskId}?provider=SILICONFLOW
```

---

# 18. 修改前端 API 类型

打开：

```text
frontend/src/api/article.ts
```

在 ArticleVO 中新增：

```ts
imageResults?: string
```

新增类型：

```ts
export type ImageProvider =
  | 'AUTO'
  | 'PEXELS'
  | 'GOOGLE_AI'
  | 'SILICONFLOW'
```

新增图片结果类型：

```ts
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
```

新增请求：

```ts
export const generateImageResults = async (
  taskId: string,
  provider: ImageProvider = 'AUTO'
) => {
  const res = await request.post<
    BaseResponse<ArticleVO>
  >(
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
```

---

# 19. 修改前端文章详情页

新增状态：

```ts
const imageResultLoading = ref(false)

const imageProvider =
  ref<ImageProvider>('AUTO')
```

解析图片结果：

```ts
const imageResultOptions = computed(() => {
  return parseJsonArray<ImageResultOption>(
    article.value?.imageResults
  )
})
```

生成方法：

```ts
const handleGenerateImageResults =
  async () => {

  if (!taskId.value) {
    return
  }

  if (!article.value?.imagePrompts) {
    message.warning(
      '请先生成配图提示词'
    )
    return
  }

  imageResultLoading.value = true

  try {
    const res =
      await generateImageResults(
        taskId.value,
        imageProvider.value
      )

    if (res.code !== 0) {
      message.error(res.message)
      await loadArticle()
      await loadAgentLogs()
      return
    }

    article.value = res.data

    message.success(
      '图片结果生成成功'
    )

    await loadAgentLogs()
  } catch (error) {
    console.error(error)

    message.error(
      '生成图片结果失败'
    )

    await loadArticle()
    await loadAgentLogs()
  } finally {
    imageResultLoading.value = false
  }
}
```

---

# 20. 前端图片来源选择器

在页面中增加：

```vue
<a-radio-group
  v-model:value="imageProvider"
  button-style="solid"
>
  <a-radio-button value="AUTO">
    自动
  </a-radio-button>

  <a-radio-button value="PEXELS">
    Pexels
  </a-radio-button>

  <a-radio-button value="SILICONFLOW">
    SiliconFlow 生图
  </a-radio-button>

  <a-radio-button value="GOOGLE_AI">
    Gemini 生图
  </a-radio-button>
</a-radio-group>

<a-button
  :loading="imageResultLoading"
  @click="handleGenerateImageResults"
>
  生成图片结果
</a-button>
```

注意：

```text
SiliconFlow 是新增项，
Gemini 仍然保留。
```

---

# 21. 前端图片结果展示

参考：

```vue
<a-card
  v-if="imageResultOptions.length > 0"
  class="card"
  title="图片结果"
>
  <a-row :gutter="[16, 16]">
    <a-col
      v-for="(item, index)
      in imageResultOptions"
      :key="index"
      :xs="24"
      :md="8"
    >
      <a-card hoverable>
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

        <a-tag>
          {{ item.source }}
        </a-tag>

        <div v-if="item.author">
          图片作者/平台：
          {{ item.author }}
        </div>

        <div v-if="item.sourceUrl">
          <a
            :href="item.sourceUrl"
            target="_blank"
          >
            查看来源
          </a>
        </div>
      </a-card>
    </a-col>
  </a-row>
</a-card>
```

---

# 22. 接入 continueWorkflow

在：

```text
ArticleServiceImpl.continueWorkflow()
```

的 `CONTENT_GENERATION` 阶段加入：

```java
if (ArticlePhase.CONTENT_GENERATION
        .name()
        .equals(phase)) {

    if (!StringUtils.hasText(
            article.getContent()
    )) {
        return generateContent(taskId);
    }

    if (!StringUtils.hasText(
            article.getImagePrompts()
    )) {
        return generateImagePrompts(
                taskId
        );
    }

    if (!StringUtils.hasText(
            article.getImageResults()
    )) {
        return generateImageResults(
                taskId,
                "AUTO"
        );
    }

    throw new RuntimeException(
            "当前文章主线流程已完成"
    );
}
```

AUTO 当前会使用：

```text
Pexels
→ SiliconFlow
→ fallback
```

这样 Gemini 的额度或网络问题不会阻塞自动工作流。

---

# 23. 更新前端继续下一步文案

在：

```ts
nextWorkflowText
```

中增加：

```ts
if (
  article.value.phase
    === 'CONTENT_GENERATION'
) {
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

  return '主线流程已完成'
}
```

更新禁用条件：

```ts
if (
  article.value.phase
    === 'CONTENT_GENERATION'
  && article.value.content
  && article.value.imagePrompts
  && article.value.imageResults
) {
  return true
}
```

---

# 24. 启动项目

启动 Docker 服务：

```powershell
docker compose up -d
```

命令含义：

```text
后台启动 MySQL 和 Redis 容器。
```

在 IDEA 中完整重启 Spring Boot。

启动前端：

```powershell
npm run dev
```

命令含义：

```text
启动 Vite 前端开发服务器。
```

---

# 25. 验收 Pexels

页面操作：

```text
选择 Pexels
→ 点击生成图片结果
```

预期：

```text
source = PEXELS
能够显示真实图片
能够显示摄影师信息
能够打开图片来源页面
数据库 image_results 保存成功
Agent 日志记录成功
```

---

# 26. 验收 Gemini

页面操作：

```text
选择 Gemini 生图
→ 点击生成图片结果
```

可能结果：

```text
200
    成功生成并保存图片

429
    当前模型免费额度为 0 或已耗尽

ConnectException
    Java 连接 Google 失败

SSLHandshakeException
    代理或 TLS 握手失败
```

Gemini 当前验收目标：

```text
接口代码、provider 路由和前端入口保留；
若受额度或网络影响，不阻塞 AUTO 主流程。
```

---

# 27. 验收 SiliconFlow

页面操作：

```text
选择 SiliconFlow 生图
→ 点击生成图片结果
```

成功日志：

```text
准备请求 SiliconFlow 生图接口：
https://api.siliconflow.cn/v1/images/generations

SiliconFlow status = 200
SiliconFlow traceId = ...
SiliconFlow body = ...
```

检查本地目录：

```text
backend/uploads/images
```

应出现：

```text
siliconflow-xxxxxxxx.png
```

直接访问：

```text
http://localhost:8123/uploads/images/siliconflow-xxxxxxxx.png
```

能够打开即表示：

```text
API 调用成功
图片下载成功
本地保存成功
静态资源映射成功
```

---

# 28. 验收 AUTO

选择：

```text
自动
```

当前流程：

```text
Pexels 成功
    → 使用 Pexels

Pexels 失败
    → 调用 SiliconFlow

SiliconFlow 失败
    → 使用 fallback
```

AUTO 不调用 Gemini，避免当前 Google 额度或网络问题导致主流程失败。

---

# 29. 数据库验收

查询：

```sql
SELECT
    task_id,
    phase,
    status,
    LEFT(image_results, 800)
        AS image_results_preview
FROM article
ORDER BY id DESC
LIMIT 5;
```

预期：

```text
image_results 不为空
status = SUCCESS
```

其中 source 可能为：

```text
PEXELS
GOOGLE_AI
SILICONFLOW
FALLBACK
```

---

# 30. Agent 日志验收

执行：

```sql
SELECT
    task_id,
    agent_name,
    status,
    cost_ms,
    LEFT(input_text, 300),
    LEFT(output_text, 500),
    error_message
FROM agent_log
WHERE agent_name = 'ImageResultAgent'
ORDER BY id DESC
LIMIT 10;
```

成功记录：

```text
agent_name = ImageResultAgent
status = SUCCESS
output_text = 图片结果 JSON
```

失败记录：

```text
status = FAILED
error_message = 具体 API 错误
```

---

# 31. 常见问题

## 31.1 Pexels 401

检查：

```text
PEXELS_API_KEY 是否正确
Authorization 是否直接使用 API Key
修改后是否重启后端
```

## 31.2 SiliconFlow 401

检查：

```text
是否使用独立的 SILICONFLOW_API_KEY
是否错误使用 MiniMax Key
请求头是否为 Bearer + 空格 + Key
```

## 31.3 SiliconFlow 400

确认 Kolors 参数：

```yaml
image-model: Kwai-Kolors/Kolors
image-size: 1024x1024
batch-size: 1
num-inference-steps: 20
guidance-scale: 7.5
```

## 31.4 SiliconFlow 429

可能原因：

```text
余额不足
请求频率过高
模型限流
```

查看：

```text
status
traceId
body
```

## 31.5 Gemini 429 limit: 0

说明：

```text
当前 Google 项目对该模型没有免费额度。
```

不是 ImageResultAgent 路由错误。

## 31.6 Gemini SSLHandshakeException

建议：

```text
1. 检查代理端口
2. 不要给整个应用长期设置错误的全局代理
3. 必要时只在 GoogleImagenClient 中配置代理
4. 可强制 HttpClient 使用 HTTP/1.1
```

## 31.7 图片生成成功但前端不显示

检查：

```text
1. image_results 中 imageUrl 是否正确
2. uploads/images 是否有本地文件
3. 本地图片 URL 能否直接打开
4. WebMvcConfig 是否生效
5. backend-base-url 是否为 http://localhost:8123
```

## 31.8 点击 SiliconFlow 却调用 Gemini

检查前端：

```vue
value="SILICONFLOW"
```

检查后端：

```java
if ("SILICONFLOW".equals(
        normalizedProvider
)) {
    return siliconFlowImageClient
            .generateImage(prompt);
}
```

在浏览器 Network 中确认：

```text
provider=SILICONFLOW
```

---

# 32. Day16 最终项目结构

```text
backend
├─ agent
│  ├─ ImagePromptAgent.java
│  ├─ ImageResultAgent.java
│  ├─ PexelsImageClient.java
│  ├─ GoogleImagenClient.java
│  └─ SiliconFlowImageClient.java
├─ config
│  └─ WebMvcConfig.java
├─ model
│  ├─ entity
│  │  └─ Article.java
│  └─ vo
│     ├─ ArticleVO.java
│     ├─ ImagePromptOption.java
│     └─ ImageResultOption.java
├─ mapper
│  └─ ArticleMapper.java
├─ service
│  ├─ ArticleService.java
│  └─ impl
│     └─ ArticleServiceImpl.java
└─ controller
   └─ ArticleController.java

frontend
├─ src/api/article.ts
└─ 文章详情页组件
```

---

# 33. Day16 最终架构图

```text
                    image_prompts
                          │
                          ▼
                  ImageResultAgent
                          │
          ┌───────────────┼────────────────┐
          │               │                │
          ▼               ▼                ▼
 PexelsImageClient  GoogleImagenClient  SiliconFlowImageClient
          │               │                │
          ▼               ▼                ▼
      真实图片        Gemini 生图       SiliconFlow 生图
          │               │                │
          └───────────────┼────────────────┘
                          │
                          ▼
                 ImageResultOption[]
                          │
                          ▼
                article.image_results
                          │
                          ▼
                    前端图片卡片
```

AUTO：

```text
Pexels
    │
    ├─ 成功 → 返回
    │
    └─ 失败
          ↓
     SiliconFlow
          │
          ├─ 成功 → 返回
          │
          └─ 失败
                ↓
             fallback
```

---

# 34. Day16 验收清单

```text
[√] MiniMax 继续负责文本生成
[√] PEXELS_API_KEY 独立配置
[√] GOOGLE_AI_API_KEY 独立配置
[√] SILICONFLOW_API_KEY 独立配置
[√] article 表增加 image_results
[√] Article 增加 imageResults
[√] ArticleVO 增加 imageResults
[√] 新增 ImageResultOption
[√] 新增或保留 WebMvcConfig
[√] PexelsImageClient 可用
[√] GoogleImagenClient 保留
[√] SiliconFlowImageClient 可用
[√] ImageResultAgent 支持 PEXELS
[√] ImageResultAgent 支持 GOOGLE_AI
[√] ImageResultAgent 支持 SILICONFLOW
[√] ImageResultAgent 支持 AUTO
[√] AUTO 使用 Pexels → SiliconFlow → fallback
[√] Service 支持 generateImageResults
[√] Controller 支持 provider 参数
[√] 前端增加四种图片来源
[√] 前端能够展示图片结果
[√] 图片结果保存到 image_results
[√] SiliconFlow 临时图片下载到本地
[√] Agent 日志记录 ImageResultAgent
[√] continueWorkflow 接入图片结果生成
```

---

# 35. Day16 完成后的能力总结

Day16 不只是接入了三个图片 API，而是完成了一套统一图片能力：

```text
统一输入
    image_prompts

统一路由
    ImageResultAgent

统一供应商适配
    Pexels / Gemini / SiliconFlow

统一输出
    ImageResultOption

统一持久化
    article.image_results

统一展示
    前端图片结果卡片

统一观测
    agent_log

统一降级
    AUTO fallback
```

这使项目从单一文本生成系统，升级为支持多供应商、多模态输出和失败降级的 Agent 工作流系统。
