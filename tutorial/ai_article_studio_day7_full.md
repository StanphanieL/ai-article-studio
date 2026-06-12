# AI Article Studio：Day 7 完整开发教程

> Day 7 目标：新增 **Agent 执行日志系统**。每次标题生成、大纲生成、正文生成时，都把 Agent 的输入 Prompt、模型原始输出、耗时、状态、错误信息记录到数据库 `agent_log` 表，并在前端详情页展示出来。

---

## 0. Day 7 最终成果

完成 Day 7 后，每次点击：

```text
生成标题
确认标题并生成大纲
生成正文
流式生成正文
```

后端都会向 `agent_log` 表写入一条执行记录。前端详情页也会新增：

```text
Agent 执行日志
```

可以展开查看：

```text
Agent 名称
执行状态
耗时
错误信息
输入 Prompt
模型原始输出
```

---

## 1. 为什么要做 Agent 日志？

接入真实大模型后，问题会明显变多：

```text
模型返回格式不稳定
标题 JSON 解析失败
大纲字段缺失
正文前面带 <think>
接口偶尔超时
不同模型输出质量差异大
Prompt 改完不知道效果如何
```

如果没有日志，前端只会告诉你“生成失败”，但你不知道模型实际返回了什么。`agent_log` 可以帮助你排查：

```text
1. 模型是否真的被调用
2. 输入 Prompt 是否正确
3. 模型原始输出是什么
4. JSON 解析失败是因为格式错，还是模型输出截断
5. 哪个 Agent 最慢
6. 哪个 Agent 最容易失败
```

---

## 2. Day 7 新增和修改内容

新增后端文件：

```text
AgentLog.java
AgentLogVO.java
AgentLogMapper.java
AgentLogService.java
AgentLogServiceImpl.java
```

修改后端文件：

```text
TitleGeneratorAgent.java
OutlineGeneratorAgent.java
ContentGeneratorAgent.java
ArticleServiceImpl.java
ArticleController.java
JsonExtractUtils.java
```

修改前端文件：

```text
frontend/src/api/article.ts
frontend/src/pages/ArticleDetail.vue
```

新增接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/agent-logs/{taskId}` | GET | 查询某个文章任务的 Agent 执行日志 |

---

# 第一部分：确认数据库表

## 3. 确认 `agent_log` 表存在

进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

含义：进入 MySQL 容器，并强制使用 utf8mb4 字符集，避免中文乱码。

进入后执行：

```sql
SET NAMES utf8mb4;
USE ai_article_studio;
SHOW TABLES;
DESC agent_log;
```

应该能看到字段：

```text
id
task_id
agent_name
input_text
output_text
status
cost_ms
error_message
create_time
```

如果没有 `agent_log` 表，执行：

```sql
CREATE TABLE IF NOT EXISTS agent_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    agent_name VARCHAR(128) NOT NULL COMMENT '智能体名称',
    input_text MEDIUMTEXT NULL COMMENT '输入内容',
    output_text MEDIUMTEXT NULL COMMENT '输出内容',
    status VARCHAR(64) NOT NULL COMMENT '执行状态',
    cost_ms BIGINT NULL COMMENT '耗时毫秒',
    error_message TEXT NULL COMMENT '错误信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_task_id (task_id),
    INDEX idx_agent_name (agent_name)
) COMMENT '智能体执行日志表';
```

字段解释：

| 字段 | 含义 |
|---|---|
| `task_id` | 当前文章任务 ID |
| `agent_name` | 哪个 Agent 执行 |
| `input_text` | 输入 Prompt |
| `output_text` | 模型原始输出 |
| `status` | SUCCESS / FAILED |
| `cost_ms` | 执行耗时，单位毫秒 |
| `error_message` | 错误信息 |
| `create_time` | 日志创建时间 |

---

# 第二部分：后端开发

## 4. 创建 `AgentLog` 实体类

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/entity/AgentLog.java
```

代码：

```java
package com.nana.aiarticlestudio.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentLog {

    private Long id;

    private String taskId;

    private String agentName;

    private String inputText;

    private String outputText;

    private String status;

    private Long costMs;

    private String errorMessage;

    private LocalDateTime createTime;
}
```

作用：对应数据库 `agent_log` 表。

---

## 5. 创建 `AgentLogVO`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/vo/AgentLogVO.java
```

代码：

```java
package com.nana.aiarticlestudio.model.vo;

import com.nana.aiarticlestudio.model.entity.AgentLog;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class AgentLogVO {

    private Long id;

    private String taskId;

    private String agentName;

    private String inputText;

    private String outputText;

    private String status;

    private Long costMs;

    private String errorMessage;

    private LocalDateTime createTime;

    public static AgentLogVO fromEntity(AgentLog agentLog) {
        if (agentLog == null) {
            return null;
        }

        AgentLogVO vo = new AgentLogVO();
        BeanUtils.copyProperties(agentLog, vo);
        return vo;
    }
}
```

作用：返回 Agent 日志给前端展示。使用 VO 的好处是后续可以隐藏字段或做脱敏。

---

## 6. 创建 `AgentLogMapper`

路径：

```text
src/main/java/com/nana/aiarticlestudio/mapper/AgentLogMapper.java
```

代码：

```java
package com.nana.aiarticlestudio.mapper;

import com.nana.aiarticlestudio.model.entity.AgentLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentLogMapper {

    @Insert("""
            INSERT INTO agent_log (
                task_id,
                agent_name,
                input_text,
                output_text,
                status,
                cost_ms,
                error_message
            )
            VALUES (
                #{taskId},
                #{agentName},
                #{inputText},
                #{outputText},
                #{status},
                #{costMs},
                #{errorMessage}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentLog agentLog);

    @Select("""
            SELECT *
            FROM agent_log
            WHERE task_id = #{taskId}
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<AgentLog> listByTaskId(@Param("taskId") String taskId,
                                @Param("limit") int limit);
}
```

作用：

| 方法 | 作用 |
|---|---|
| `insert` | 写入一条 Agent 执行日志 |
| `listByTaskId` | 查询某个任务最近的 Agent 执行日志 |

---

## 7. 创建 `AgentLogService`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/AgentLogService.java
```

代码：

```java
package com.nana.aiarticlestudio.service;

import com.nana.aiarticlestudio.model.vo.AgentLogVO;

import java.util.List;

public interface AgentLogService {

    void saveSuccess(String taskId,
                     String agentName,
                     String inputText,
                     String outputText,
                     long costMs);

    void saveFailed(String taskId,
                    String agentName,
                    String inputText,
                    String errorMessage,
                    long costMs);

    List<AgentLogVO> listByTaskId(String taskId);
}
```

作用：统一封装 Agent 日志保存和查询逻辑。

---

## 8. 创建 `AgentLogServiceImpl`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/AgentLogServiceImpl.java
```

代码：

```java
package com.nana.aiarticlestudio.service.impl;

import com.nana.aiarticlestudio.mapper.AgentLogMapper;
import com.nana.aiarticlestudio.model.entity.AgentLog;
import com.nana.aiarticlestudio.model.vo.AgentLogVO;
import com.nana.aiarticlestudio.service.AgentLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentLogServiceImpl implements AgentLogService {

    private static final int DEFAULT_LIMIT = 50;

    private final AgentLogMapper agentLogMapper;

    @Override
    public void saveSuccess(String taskId,
                            String agentName,
                            String inputText,
                            String outputText,
                            long costMs) {
        AgentLog log = new AgentLog();
        log.setTaskId(taskId);
        log.setAgentName(agentName);
        log.setInputText(inputText);
        log.setOutputText(outputText);
        log.setStatus("SUCCESS");
        log.setCostMs(costMs);
        log.setErrorMessage(null);

        agentLogMapper.insert(log);
    }

    @Override
    public void saveFailed(String taskId,
                           String agentName,
                           String inputText,
                           String errorMessage,
                           long costMs) {
        AgentLog log = new AgentLog();
        log.setTaskId(taskId);
        log.setAgentName(agentName);
        log.setInputText(inputText);
        log.setOutputText(null);
        log.setStatus("FAILED");
        log.setCostMs(costMs);
        log.setErrorMessage(errorMessage);

        agentLogMapper.insert(log);
    }

    @Override
    public List<AgentLogVO> listByTaskId(String taskId) {
        return agentLogMapper.listByTaskId(taskId, DEFAULT_LIMIT)
                .stream()
                .map(AgentLogVO::fromEntity)
                .toList();
    }
}
```

`DEFAULT_LIMIT = 50` 表示默认只查最近 50 条日志，避免一次性返回太多。

---

# 第三部分：改造 Agent，拆分 Prompt / 原始调用 / 解析

## 9. 为什么要改造 Agent？

日志需要记录：

```text
inputText：输入 Prompt
outputText：模型原始输出
```

如果 Agent 只有一个 `generate(...)` 方法，外层 `ArticleServiceImpl` 不容易拿到中间的 prompt 和 response。

所以把 Agent 拆成：

```text
buildPrompt：构造 Prompt
callRaw：调用模型，得到原始输出
parse / clean：解析或清洗模型输出
generate：保留原来的快捷方法
```

这样既能记录日志，也不破坏旧逻辑。

---

## 10. 修改 `TitleGeneratorAgent`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/TitleGeneratorAgent.java
```

核心结构：

```java
public String buildPrompt(String topic, String style) {
    return """
            你是一个专业的爆款文章标题策划专家。
            请根据用户选题生成 3 个标题候选。

            用户选题：%s
            文章风格：%s

            输出要求：
            1. 必须只输出 JSON 数组
            2. 不要输出 Markdown
            3. 不要输出 ```json
            4. 不要输出任何解释性文字
            5. 不要输出 <think>、</think> 或任何思考过程
            6. 必须保证 JSON 数组完整闭合，最后一个字符必须是 ]
            7. 数组中必须有 3 个对象
            8. 每个对象必须包含 title 和 reason 两个字段
            """.formatted(topic, style);
}

public String callRaw(String prompt) {
    return llmClient.chat(prompt);
}

public List<TitleOption> parse(String response) {
    try {
        String json = JsonExtractUtils.extractJsonArray(response);
        return objectMapper.readValue(json, new TypeReference<List<TitleOption>>() {});
    } catch (Exception e) {
        throw new RuntimeException("解析标题失败：" + e.getMessage());
    }
}

public List<TitleOption> generate(String topic, String style) {
    String prompt = buildPrompt(topic, style);
    String response = callRaw(prompt);
    return parse(response);
}
```

---

## 11. 修改 `OutlineGeneratorAgent`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/OutlineGeneratorAgent.java
```

核心结构：

```java
public String buildPrompt(String topic, String selectedTitle, String style) {
    return """
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
            5. 不要输出 <think>、</think> 或任何思考过程
            6. 必须保证 JSON 数组完整闭合，最后一个字符必须是 ]
            7. 数组中必须有 5 个章节对象
            8. 每个对象必须包含 heading 和 description 两个字段
            """.formatted(topic, selectedTitle, style);
}

public String callRaw(String prompt) {
    return llmClient.chat(prompt);
}

public List<OutlineItem> parse(String response) {
    try {
        String json = JsonExtractUtils.extractJsonArray(response);
        return objectMapper.readValue(json, new TypeReference<List<OutlineItem>>() {});
    } catch (Exception e) {
        throw new RuntimeException("解析大纲失败：" + e.getMessage());
    }
}

public List<OutlineItem> generate(String topic, String selectedTitle, String style) {
    String prompt = buildPrompt(topic, selectedTitle, style);
    String response = callRaw(prompt);
    return parse(response);
}
```

---

## 12. 修改 `ContentGeneratorAgent`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/ContentGeneratorAgent.java
```

核心结构：

```java
public String buildPrompt(String topic, String selectedTitle, String outline, String style) {
    return """
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
}

public String callRaw(String prompt) {
    return llmClient.chat(prompt);
}

public String clean(String response) {
    if (response == null) {
        return "";
    }

    return response.replaceAll("(?s)<think>.*?</think>", "").trim();
}

public String generate(String topic, String selectedTitle, String outline, String style) {
    String prompt = buildPrompt(topic, selectedTitle, outline, style);
    String response = callRaw(prompt);
    return clean(response);
}
```

重点：正文日志建议保存模型原始 response，真正写入 `article.content` 的内容使用 `clean(response)` 后的结果。

---

# 第四部分：增强 JSON 提取工具

## 13. 修改 `JsonExtractUtils`

路径：

```text
src/main/java/com/nana/aiarticlestudio/util/JsonExtractUtils.java
```

推荐版本：

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

        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "").trim();

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
            throw new RuntimeException("模型返回中未找到完整 JSON 数组：" + text);
        }

        return cleaned.substring(start, end + 1);
    }
}
```

作用：

```text
1. 清理 <think>...</think>
2. 清理 ```json 包裹
3. 从文本中截取第一个 [ 到最后一个 ]
4. 返回 JSON 数组字符串
```

注意：如果模型返回的 JSON 本身没有闭合，仍然会失败。后续可以做 JSON 修复重试机制。

---

# 第五部分：在 ArticleServiceImpl 中写入日志

## 14. 注入 `AgentLogService`

打开：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/ArticleServiceImpl.java
```

新增 import：

```java
import com.nana.aiarticlestudio.service.AgentLogService;
```

在类字段中新增：

```java
private final AgentLogService agentLogService;
```

因为类上用了 `@RequiredArgsConstructor`，Spring 会自动注入。

---

## 15. 修改 `generateTitles`

完整推荐版本：

```java
@Override
public ArticleVO generateTitles(String taskId) {
    String prompt = null;
    long start = System.currentTimeMillis();

    try {
        Article article = articleMapper.selectByTaskId(taskId);
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        prompt = titleGeneratorAgent.buildPrompt(article.getTopic(), article.getStyle());
        String response = titleGeneratorAgent.callRaw(prompt);
        List<TitleOption> titleOptions = titleGeneratorAgent.parse(response);

        long costMs = System.currentTimeMillis() - start;
        agentLogService.saveSuccess(taskId, "TitleGeneratorAgent", prompt, response, costMs);

        String titleOptionsJson = objectMapper.writeValueAsString(titleOptions);

        int result = articleMapper.updateTitleOptions(
                taskId,
                titleOptionsJson,
                ArticlePhase.TITLE_SELECTION.name(),
                ArticleStatus.SUCCESS.name()
        );

        if (result != 1) {
            throw new RuntimeException("保存标题候选失败");
        }

        return getByTaskId(taskId);
    } catch (Exception e) {
        long costMs = System.currentTimeMillis() - start;
        agentLogService.saveFailed(taskId, "TitleGeneratorAgent", prompt, e.getMessage(), costMs);
        throw new RuntimeException("生成标题失败：" + e.getMessage());
    }
}
```

---

## 16. 修改 `confirmTitleAndGenerateOutline`

完整推荐版本：

```java
@Override
public ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request) {
    String prompt = null;
    long start = System.currentTimeMillis();

    try {
        Article article = articleMapper.selectByTaskId(request.getTaskId());
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        prompt = outlineGeneratorAgent.buildPrompt(
                article.getTopic(),
                request.getSelectedTitle(),
                article.getStyle()
        );

        String response = outlineGeneratorAgent.callRaw(prompt);
        List<OutlineItem> outline = outlineGeneratorAgent.parse(response);

        long costMs = System.currentTimeMillis() - start;
        agentLogService.saveSuccess(
                request.getTaskId(),
                "OutlineGeneratorAgent",
                prompt,
                response,
                costMs
        );

        String outlineJson = objectMapper.writeValueAsString(outline);

        int result = articleMapper.updateSelectedTitleAndOutline(
                request.getTaskId(),
                request.getSelectedTitle(),
                outlineJson,
                ArticlePhase.OUTLINE_EDITING.name(),
                ArticleStatus.SUCCESS.name()
        );

        if (result != 1) {
            throw new RuntimeException("保存大纲失败");
        }

        return getByTaskId(request.getTaskId());
    } catch (Exception e) {
        long costMs = System.currentTimeMillis() - start;
        agentLogService.saveFailed(
                request.getTaskId(),
                "OutlineGeneratorAgent",
                prompt,
                e.getMessage(),
                costMs
        );
        throw new RuntimeException("生成大纲失败：" + e.getMessage());
    }
}
```

---

## 17. 修改 `generateContent`

完整推荐版本：

```java
@Override
public ArticleVO generateContent(String taskId) {
    String prompt = null;
    long start = System.currentTimeMillis();

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

        prompt = contentGeneratorAgent.buildPrompt(
                article.getTopic(),
                article.getSelectedTitle(),
                article.getOutline(),
                article.getStyle()
        );

        String response = contentGeneratorAgent.callRaw(prompt);
        String content = contentGeneratorAgent.clean(response);

        long costMs = System.currentTimeMillis() - start;
        agentLogService.saveSuccess(taskId, "ContentGeneratorAgent", prompt, response, costMs);

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
        long costMs = System.currentTimeMillis() - start;
        agentLogService.saveFailed(taskId, "ContentGeneratorAgent", prompt, e.getMessage(), costMs);
        throw new RuntimeException("生成正文失败：" + e.getMessage());
    }
}
```

---

## 18. 修改 `streamGenerateContent`

在 Day 5 的 `streamGenerateContent` 里，原来有：

```java
String content = contentGeneratorAgent.generate(
        article.getTopic(),
        article.getSelectedTitle(),
        article.getOutline(),
        article.getStyle()
);
```

改成：

```java
String prompt = contentGeneratorAgent.buildPrompt(
        article.getTopic(),
        article.getSelectedTitle(),
        article.getOutline(),
        article.getStyle()
);

long agentStart = System.currentTimeMillis();

String response = contentGeneratorAgent.callRaw(prompt);
String content = contentGeneratorAgent.clean(response);

long costMs = System.currentTimeMillis() - agentStart;

agentLogService.saveSuccess(
        taskId,
        "ContentGeneratorAgent",
        prompt,
        response,
        costMs
);
```

如果想给流式失败也记录日志，可以在 catch 里加：

```java
agentLogService.saveFailed(
        taskId,
        "ContentGeneratorAgent",
        null,
        e.getMessage(),
        0L
);
```

---

# 第六部分：新增查询日志接口

## 19. 修改 `ArticleController`

打开：

```text
src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.vo.AgentLogVO;
import com.nana.aiarticlestudio.service.AgentLogService;

import java.util.List;
```

在类字段里新增：

```java
private final AgentLogService agentLogService;
```

如果 `ArticleController` 类上已经有 `@RequiredArgsConstructor`，Spring 会自动注入。

新增接口：

```java
@GetMapping("/agent-logs/{taskId}")
public BaseResponse<List<AgentLogVO>> listAgentLogs(@PathVariable String taskId) {
    return BaseResponse.success(agentLogService.listByTaskId(taskId));
}
```

最终新增接口：

```text
GET /api/article/agent-logs/{taskId}
```

---

# 第七部分：后端测试

## 20. 重启后端

在 IDEA 中停止后端，然后重新启动：

```text
AiArticleStudioApplication
```

如果启动报错，重点检查：

```text
AgentLogMapper 是否加了 @Mapper
AgentLogServiceImpl 是否加了 @Service
ArticleController 是否正确注入 AgentLogService
ArticleServiceImpl 是否正确注入 AgentLogService
```

---

## 21. MySQL 查询日志

通过前端执行一次完整生成流程后，进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

执行：

```sql
SET NAMES utf8mb4;
USE ai_article_studio;

SELECT
  task_id,
  agent_name,
  status,
  cost_ms,
  LEFT(error_message, 100) AS error_preview,
  create_time
FROM agent_log
ORDER BY create_time DESC
LIMIT 10;
```

如果看到：

```text
TitleGeneratorAgent
OutlineGeneratorAgent
ContentGeneratorAgent
```

说明日志已经写入成功。

查看某条日志的输入输出：

```sql
SELECT
  agent_name,
  LEFT(input_text, 500) AS input_preview,
  LEFT(output_text, 500) AS output_preview,
  status,
  cost_ms
FROM agent_log
WHERE task_id = '你的taskId'
ORDER BY create_time DESC;
```

---

## 22. 浏览器直接测试日志接口

访问：

```text
http://localhost:8123/api/article/agent-logs/你的taskId
```

如果返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "agentName": "ContentGeneratorAgent",
      "status": "SUCCESS",
      "costMs": 12345
    }
  ]
}
```

说明接口成功。

---

# 第八部分：前端展示 Agent 日志

## 23. 修改 `article.ts`

打开：

```text
frontend/src/api/article.ts
```

新增类型：

```ts
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
```

新增接口方法：

```ts
export const listAgentLogs = async (taskId: string) => {
  const res = await request.get<BaseResponse<AgentLogVO[]>>(`/api/article/agent-logs/${taskId}`)
  return res.data
}
```

注意：必须 `export`。如果 `ArticleDetail.vue` 导入了它们，但 `article.ts` 没有导出，前端会空白，并在 Console 报：

```text
does not provide an export named 'listAgentLogs'
```

---

## 24. 修改 `ArticleDetail.vue` import

从 `../api/article` 中新增导入：

```ts
listAgentLogs,
type AgentLogVO,
```

最终类似：

```ts
import {
  confirmTitle,
  generateContent,
  generateTitles,
  getArticle,
  listAgentLogs,
  streamGenerateContentUrl,
  type AgentLogVO,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
} from '../api/article'
```

---

## 25. 新增日志状态

```ts
const agentLogs = ref<AgentLogVO[]>([])
const logLoading = ref(false)
```

---

## 26. 新增加载日志方法

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

    agentLogs.value = res.data
  } catch (error) {
    console.error(error)
    message.error('加载 Agent 日志失败')
  } finally {
    logLoading.value = false
  }
}
```

---

## 27. 页面初始化时加载日志

```ts
onMounted(() => {
  loadArticle()
  loadAgentLogs()
})
```

也可以写成：

```ts
onMounted(async () => {
  await loadArticle()
  await loadAgentLogs()
})
```

注意：如果用了 `await`，`onMounted` 回调必须是 `async`。

---

## 28. 成功和失败后都刷新日志

成功后加：

```ts
await loadAgentLogs()
```

失败 catch 中也加：

```ts
await loadAgentLogs()
```

例如：

```ts
} catch (error) {
  console.error(error)
  message.error('生成大纲失败')
  await loadAgentLogs()
} finally {
  outlineLoading.value = false
}
```

你遇到的“失败日志只有下一次成功后才显示”，原因就是失败时没有调用 `loadAgentLogs()`。

---

## 29. 前端展示 Agent 日志

建议放在文章正文下面：

```vue
<a-card class="card" title="Agent 执行日志" :loading="logLoading">
  <a-empty v-if="agentLogs.length === 0" description="暂无 Agent 日志" />

  <a-collapse v-else>
    <a-collapse-panel
      v-for="log in agentLogs"
      :key="log.id"
      :header="`${log.agentName} - ${log.status} - ${log.costMs || 0}ms`"
    >
      <p>
        <strong>创建时间：</strong>
        {{ log.createTime }}
      </p>

      <p>
        <strong>错误信息：</strong>
        {{ log.errorMessage || '无' }}
      </p>

      <a-divider />

      <h4>输入 Prompt</h4>
      <pre class="log-text">{{ log.inputText || '无' }}</pre>

      <h4>模型输出</h4>
      <pre class="log-text">{{ log.outputText || '无' }}</pre>
    </a-collapse-panel>
  </a-collapse>
</a-card>
```

新增样式：

```css
.log-text {
  white-space: pre-wrap;
  word-break: break-word;
  background: #f7f7f7;
  padding: 12px;
  border-radius: 6px;
  max-height: 360px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
}
```

---

# 第九部分：完整测试流程

## 30. 重启前端

```text
Ctrl + C
```

然后：

```powershell
npm run dev
```

## 31. 前端验收流程

打开：

```text
http://localhost:5173/article/list
```

执行：

```text
1. 创建新文章任务
2. 进入详情页
3. 点击生成标题
4. 看 Agent 执行日志是否出现 TitleGeneratorAgent
5. 选择标题并生成大纲
6. 看日志是否出现 OutlineGeneratorAgent
7. 生成正文
8. 看日志是否出现 ContentGeneratorAgent
9. 展开日志，检查 inputText 和 outputText 是否正常
10. 故意触发一次失败，确认失败日志也能立即显示
```

---

# 第十部分：常见问题

## 32. 前端页面变空白

常见原因是：

```text
ArticleDetail.vue 导入了 listAgentLogs 或 AgentLogVO
但 article.ts 没有 export 它们。
```

检查 `article.ts` 是否有：

```ts
export interface AgentLogVO {
  ...
}

export const listAgentLogs = async (taskId: string) => {
  ...
}
```

## 33. 后端启动失败，说找不到 `AgentLogMapper`

检查：

```java
@Mapper
public interface AgentLogMapper {
```

是否加了 `@Mapper`。

## 34. 后端启动失败，说找不到 `AgentLogService`

检查：

```java
@Service
@RequiredArgsConstructor
public class AgentLogServiceImpl implements AgentLogService {
```

是否加了 `@Service`。

## 35. 日志没有写入数据库

检查：

```text
1. ArticleServiceImpl 是否注入了 AgentLogService
2. generateTitles / confirmTitleAndGenerateOutline / generateContent 是否调用了 saveSuccess
3. 失败 catch 中是否调用了 saveFailed
```

## 36. 前端日志为空

先浏览器直接访问：

```text
http://localhost:8123/api/article/agent-logs/你的taskId
```

如果接口有数据，说明前端展示或刷新问题。

如果接口没数据，说明后端没有写入日志。

## 37. 失败日志不立即显示

原因：

```text
catch 中没有调用 loadAgentLogs()
```

解决：

```ts
} catch (error) {
  console.error(error)
  message.error('生成失败')
  await loadAgentLogs()
}
```

## 38. 生成标题或大纲仍然偶尔失败

原因：

```text
真实模型输出格式不稳定。
```

当前已经做了：

```text
1. Prompt 禁止输出 <think>
2. JsonExtractUtils 清理 <think>
3. 从文本中提取 JSON 数组
```

但如果模型返回的 JSON 本身没有闭合，仍然会失败。后续可以做：

```text
1. JSON 修复重试机制
2. 失败后自动让模型重新输出合法 JSON
3. 使用更稳定的非推理模型
4. 改用 JSON Schema / response_format，如果模型平台支持
```

---

# 第十一部分：Day 7 验收标准

Day 7 完成标准：

```text
1. 新增 AgentLog 实体
2. 新增 AgentLogVO
3. 新增 AgentLogMapper
4. 新增 AgentLogService / AgentLogServiceImpl
5. TitleGeneratorAgent 支持 buildPrompt / callRaw / parse
6. OutlineGeneratorAgent 支持 buildPrompt / callRaw / parse
7. ContentGeneratorAgent 支持 buildPrompt / callRaw / clean
8. JsonExtractUtils 能清理 <think> 并提取 JSON 数组
9. 生成标题成功后写入 agent_log
10. 生成大纲成功后写入 agent_log
11. 生成正文成功后写入 agent_log
12. 失败时也能写入失败日志
13. 后端提供 /api/article/agent-logs/{taskId}
14. 前端详情页能展示 Agent 执行日志
15. 成功日志和失败日志都能及时刷新显示
16. 能看到 agentName、status、costMs、inputText、outputText、errorMessage
```

---

# 第十二部分：Day 7 完成后的项目状态

到 Day 7 后，项目已经具备真实 AI 应用中很关键的调试能力：

```text
用户界面
后端接口
数据库持久化
Agent 分层
真实大模型调用
SSE 模拟流式展示
Markdown 展示
Agent 执行日志
成功 / 失败日志追踪
```

这意味着后面调 Prompt、换模型、排查错误都会方便很多。

---

# Day 8 预告

Day 8 可以做两个方向：

```text
方向一：真正 token 级流式输出
```

当前 SSE 是：

```text
模型完整返回
↓
后端拆段
↓
前端逐段展示
```

Day 8 可以升级成：

```text
模型 stream=true
↓
后端边读模型输出
↓
边通过 SSE 转发给前端
↓
前端真正实时显示 token/chunk
```

另一个可选方向：

```text
方向二：JSON 修复重试机制
```

如果模型 JSON 失败比较频繁，可以先做这个，再做 token 级流式。
