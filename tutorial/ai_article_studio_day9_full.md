# AI Article Studio：Day 9 完整开发教程

> Day 9 目标：新增 **JSON 修复重试机制**。  
> 解决真实模型在生成标题、大纲时不严格返回 JSON，导致解析失败的问题。

---

## 0. Day 9 最终成果

Day 9 完成后，标题和大纲生成流程会从：

```text
调用模型
↓
解析 JSON
↓
解析失败就直接报错
```

升级为：

```text
调用模型
↓
尝试解析 JSON
↓
如果解析成功：正常保存
↓
如果解析失败：自动调用 JsonRepairAgent
↓
让模型把原始输出修复成合法 JSON
↓
再次解析
↓
成功：保存结果
↓
失败：返回错误并写入失败日志
```

最终效果：

```text
真实模型偶尔输出不规范时，系统能自动兜底修复一次。
```

---

## 1. Day 9 要解决的问题

你之前已经遇到过这些情况：

```text
1. 模型输出 <think>...</think>
2. JSON 数组不完整
3. 大纲要求 5 个章节，但模型返回 7 个
4. 模型返回解释性文字
5. JSON 解析失败后前端直接报错
```

没有 Day 9 之前，用户体验是：

```text
点击生成
↓
等很久
↓
失败
↓
用户只能再点一次
```

Day 9 完成后，系统会自动多做一步：

```text
第一次解析失败
↓
系统自动修复 JSON
↓
尽量成功返回
```

---

## 2. Day 9 新增和修改内容

新增后端文件：

```text
JsonRepairType.java
JsonRepairAgent.java
```

修改后端文件：

```text
TitleGeneratorAgent.java
OutlineGeneratorAgent.java
ArticleServiceImpl.java
```

核心新增能力：

```text
1. 标题 JSON 解析失败后自动修复一次
2. 大纲 JSON 解析失败后自动修复一次
3. 标题数量不是 3 个时触发修复
4. 大纲章节不是 5 个时触发修复
5. 修复失败后仍然写入失败日志
```

---

# 第一部分：设计思路

## 3. 为什么不只靠 JsonExtractUtils？

Day 7 已经有：

```text
JsonExtractUtils
```

它适合处理简单格式问题：

```text
去掉 <think>
去掉 ```json
截取 [ ... ]
```

但它不适合处理更复杂的问题：

```text
1. JSON 缺少 ]
2. JSON 缺少 }
3. 字段名不规范
4. 多返回了章节
5. 少返回了标题
6. 大纲数量不符合要求
```

这些问题靠本地规则很难稳定修复。

所以 Day 9 用一个新的 Agent 来修复：

```text
原始模型输出
↓
JsonRepairAgent
↓
修复成合法 JSON 数组
↓
重新解析
```

这本质上是：

```text
用模型修复模型的输出
```

---

## 4. Day 9 的最小可用设计

为了避免改动太大，Day 9 不新增数据库字段，只做最小稳定版本。

不新增：

```text
repair_used
repair_output
```

只保留现有日志字段：

```text
input_text
output_text
status
error_message
```

当前判断是否触发过修复的方法是：

```text
如果 agent_log.output_text 里原始输出不是合法 JSON，
但任务最终 SUCCESS，
说明 parseWithRepair 起作用了。
```

后续可以再增强日志字段。

---

# 第二部分：新增修复类型枚举

## 5. 创建 `JsonRepairType`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/enums/JsonRepairType.java
```

代码：

```java
package com.nana.aiarticlestudio.model.enums;

public enum JsonRepairType {

    TITLE_OPTIONS,

    OUTLINE
}
```

作用：

```text
区分当前要修复的是标题 JSON，还是大纲 JSON。
```

类型解释：

| 类型 | 含义 |
|---|---|
| `TITLE_OPTIONS` | 修复标题候选 JSON |
| `OUTLINE` | 修复文章大纲 JSON |

---

# 第三部分：新增 JsonRepairAgent

## 6. 创建 `JsonRepairAgent`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/JsonRepairAgent.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

import com.nana.aiarticlestudio.model.enums.JsonRepairType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonRepairAgent {

    private final LlmClient llmClient;

    public String repair(JsonRepairType type, String rawOutput) {
        String prompt = buildPrompt(type, rawOutput);
        return llmClient.chat(prompt);
    }

    public String buildPrompt(JsonRepairType type, String rawOutput) {
        if (type == JsonRepairType.TITLE_OPTIONS) {
            return buildTitleRepairPrompt(rawOutput);
        }

        if (type == JsonRepairType.OUTLINE) {
            return buildOutlineRepairPrompt(rawOutput);
        }

        throw new RuntimeException("不支持的 JSON 修复类型：" + type);
    }

    private String buildTitleRepairPrompt(String rawOutput) {
        return """
                你是一个严格的 JSON 修复器。
                下面是一段模型生成的标题候选内容，但它可能不是合法 JSON，可能包含思考过程、Markdown、解释文字、残缺 JSON 或多余内容。

                你的任务：
                只把它修复为合法 JSON 数组。

                修复要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 不要输出 <think>、</think> 或任何思考过程
                6. JSON 数组必须完整闭合，最后一个字符必须是 ]
                7. 数组中必须正好有 3 个对象
                8. 每个对象必须包含 title 和 reason 两个字段
                9. 如果原始内容超过 3 个标题，只保留最好的 3 个
                10. 如果原始内容少于 3 个标题，请根据上下文补足到 3 个
                11. 不要改变字段名

                合法格式示例：
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

                原始内容如下：
                %s
                """.formatted(rawOutput);
    }

    private String buildOutlineRepairPrompt(String rawOutput) {
        return """
                你是一个严格的 JSON 修复器。
                下面是一段模型生成的文章大纲内容，但它可能不是合法 JSON，可能包含思考过程、Markdown、解释文字、残缺 JSON、章节数量不对或多余内容。

                你的任务：
                只把它修复为合法 JSON 数组。

                修复要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 不要输出 <think>、</think> 或任何思考过程
                6. JSON 数组必须完整闭合，最后一个字符必须是 ]
                7. 数组中必须正好有 5 个对象
                8. 每个对象必须包含 heading 和 description 两个字段
                9. 如果原始内容超过 5 个章节，请合并或保留最关键的 5 个
                10. 如果原始内容少于 5 个章节，请根据上下文补足到 5 个
                11. 不要改变字段名

                合法格式示例：
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

                原始内容如下：
                %s
                """.formatted(rawOutput);
    }
}
```

作用：

```text
JsonRepairAgent 专门负责把模型不稳定输出修复成合法 JSON。
```

---

# 第四部分：修改 TitleGeneratorAgent

## 7. 新增依赖

打开：

```text
src/main/java/com/nana/aiarticlestudio/agent/TitleGeneratorAgent.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.enums.JsonRepairType;
```

新增字段：

```java
private final JsonRepairAgent jsonRepairAgent;
```

因为类上有：

```java
@RequiredArgsConstructor
```

Spring 会自动注入。

---

## 8. 新增 `parseWithRepair`

在 `TitleGeneratorAgent` 中新增：

```java
public List<TitleOption> parseWithRepair(String response) {
    try {
        List<TitleOption> result = parse(response);
        validateTitleOptions(result);
        return result;
    } catch (Exception firstException) {
        try {
            String repaired = jsonRepairAgent.repair(
                    JsonRepairType.TITLE_OPTIONS,
                    response
            );

            List<TitleOption> repairedResult = parse(repaired);
            validateTitleOptions(repairedResult);
            return repairedResult;
        } catch (Exception secondException) {
            throw new RuntimeException(
                    "解析标题失败，自动修复后仍失败。首次错误："
                            + firstException.getMessage()
                            + "；修复后错误："
                            + secondException.getMessage()
            );
        }
    }
}
```

作用：

```text
先正常解析。
如果失败，自动调用 JsonRepairAgent 修复一次。
修复后再解析。
如果还是失败，才最终报错。
```

---

## 9. 新增标题校验方法

在 `TitleGeneratorAgent` 中新增：

```java
private void validateTitleOptions(List<TitleOption> titleOptions) {
    if (titleOptions == null || titleOptions.size() != 3) {
        throw new RuntimeException("标题候选数量必须等于 3，当前数量："
                + (titleOptions == null ? 0 : titleOptions.size()));
    }

    for (TitleOption option : titleOptions) {
        if (option == null
                || !org.springframework.util.StringUtils.hasText(option.getTitle())
                || !org.springframework.util.StringUtils.hasText(option.getReason())) {
            throw new RuntimeException("标题候选字段不完整，必须包含 title 和 reason");
        }
    }
}
```

作用：

```text
1. 如果标题不是 3 个，触发修复
2. 如果 title 或 reason 缺失，触发修复
```

为什么数量不对也要触发修复？

因为模型可能返回 4 个标题或 2 个标题，虽然 JSON 语法合法，但不符合业务要求。

---

## 10. 修改 `generate`

把 `generate` 改成使用 `parseWithRepair`：

```java
public List<TitleOption> generate(String topic, String style) {
    String prompt = buildPrompt(topic, style);
    String response = callRaw(prompt);
    return parseWithRepair(response);
}
```

不要继续使用：

```java
return parse(response);
```

这样以后任何地方调用 `generate` 都有自动修复能力。

---

# 第五部分：修改 OutlineGeneratorAgent

## 11. 新增依赖

打开：

```text
src/main/java/com/nana/aiarticlestudio/agent/OutlineGeneratorAgent.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.enums.JsonRepairType;
```

新增字段：

```java
private final JsonRepairAgent jsonRepairAgent;
```

---

## 12. 新增 `parseWithRepair`

在 `OutlineGeneratorAgent` 中新增：

```java
public List<OutlineItem> parseWithRepair(String response) {
    try {
        List<OutlineItem> result = parse(response);
        validateOutline(result);
        return result;
    } catch (Exception firstException) {
        try {
            String repaired = jsonRepairAgent.repair(
                    JsonRepairType.OUTLINE,
                    response
            );

            List<OutlineItem> repairedResult = parse(repaired);
            validateOutline(repairedResult);
            return repairedResult;
        } catch (Exception secondException) {
            throw new RuntimeException(
                    "解析大纲失败，自动修复后仍失败。首次错误："
                            + firstException.getMessage()
                            + "；修复后错误："
                            + secondException.getMessage()
            );
        }
    }
}
```

---

## 13. 新增大纲校验方法

在 `OutlineGeneratorAgent` 中新增：

```java
private void validateOutline(List<OutlineItem> outline) {
    if (outline == null || outline.size() != 5) {
        throw new RuntimeException("大纲章节数量必须等于 5，当前数量："
                + (outline == null ? 0 : outline.size()));
    }

    for (OutlineItem item : outline) {
        if (item == null
                || !org.springframework.util.StringUtils.hasText(item.getHeading())
                || !org.springframework.util.StringUtils.hasText(item.getDescription())) {
            throw new RuntimeException("大纲字段不完整，必须包含 heading 和 description");
        }
    }
}
```

作用：

```text
1. 如果大纲不是 5 个章节，触发修复
2. 如果 heading 或 description 缺失，触发修复
```

---

## 14. 修改 `generate`

把 `generate` 改成：

```java
public List<OutlineItem> generate(String topic, String selectedTitle, String style) {
    String prompt = buildPrompt(topic, selectedTitle, style);
    String response = callRaw(prompt);
    return parseWithRepair(response);
}
```

---

# 第六部分：修改 ArticleServiceImpl

## 15. 修改 `generateTitles`

Day 7 中 `ArticleServiceImpl` 里可能是：

```java
String response = titleGeneratorAgent.callRaw(prompt);

List<TitleOption> titleOptions = titleGeneratorAgent.parse(response);
```

现在改成：

```java
String response = titleGeneratorAgent.callRaw(prompt);

List<TitleOption> titleOptions = titleGeneratorAgent.parseWithRepair(response);
```

修改后核心片段：

```java
String response = titleGeneratorAgent.callRaw(prompt);

List<TitleOption> titleOptions = titleGeneratorAgent.parseWithRepair(response);

long costMs = System.currentTimeMillis() - start;
agentLogService.saveSuccess(
        taskId,
        "TitleGeneratorAgent",
        prompt,
        response,
        costMs
);
```

注意：

```text
这里日志保存的 outputText 仍然是第一次模型原始 response。
```

这样方便排查：

```text
原始模型到底输出了什么
为什么需要修复
```

---

## 16. 修改 `confirmTitleAndGenerateOutline`

Day 7 中可能是：

```java
String response = outlineGeneratorAgent.callRaw(prompt);

List<OutlineItem> outline = outlineGeneratorAgent.parse(response);
```

现在改成：

```java
String response = outlineGeneratorAgent.callRaw(prompt);

List<OutlineItem> outline = outlineGeneratorAgent.parseWithRepair(response);
```

修改后核心片段：

```java
String response = outlineGeneratorAgent.callRaw(prompt);

List<OutlineItem> outline = outlineGeneratorAgent.parseWithRepair(response);

long costMs = System.currentTimeMillis() - start;
agentLogService.saveSuccess(
        request.getTaskId(),
        "OutlineGeneratorAgent",
        prompt,
        response,
        costMs
);
```

---

# 第七部分：关于日志的设计说明

## 17. 为什么日志里还是原始 outputText？

Day 9 虽然会自动修复 JSON，但日志中仍然建议保存：

```text
第一次模型原始输出
```

而不是修复后的 JSON。

原因是：

```text
日志的核心价值是调试。
你需要知道模型第一次到底输出错在哪里。
```

所以你可能看到：

```text
outputText 看起来不是合法 JSON
但 status = SUCCESS
```

这不是 bug，而是说明：

```text
parseWithRepair 自动修复成功了。
```

---

## 18. 为什么不新增 repair_used / repair_output？

这是 Day 9 的简化设计。

如果要更专业，可以给 `agent_log` 表新增字段：

```sql
ALTER TABLE agent_log
ADD COLUMN repair_used TINYINT NOT NULL DEFAULT 0 COMMENT '是否使用JSON修复',
ADD COLUMN repair_output MEDIUMTEXT NULL COMMENT '修复后的输出';
```

但这会涉及：

```text
1. 数据库表结构修改
2. AgentLog 实体修改
3. Mapper 修改
4. Service 修改
5. 前端日志展示修改
```

为了 Day 9 不过度复杂，先不加。

---

# 第八部分：后端测试

## 19. 重启后端

在 IDEA 中停止后端，然后重新运行：

```text
AiArticleStudioApplication
```

如果启动失败，重点检查：

```text
1. JsonRepairAgent 是否加了 @Component
2. TitleGeneratorAgent 是否注入了 JsonRepairAgent
3. OutlineGeneratorAgent 是否注入了 JsonRepairAgent
4. JsonRepairType 路径是否正确
5. 是否有 import 错误
```

---

## 20. 正常生成测试

打开前端：

```text
http://localhost:5173/article/list
```

测试：

```text
1. 创建文章任务：怎么提高睡眠质量
2. 进入详情页
3. 点击生成标题
4. 如果模型第一次输出正常，应该直接成功
5. 选择标题
6. 点击确认标题并生成大纲
7. 如果模型第一次输出正常，应该直接成功
```

---

## 21. 触发修复测试

要验证修复机制是否真的生效，可以临时把 Prompt 改得更容易失败。

例如在 `TitleGeneratorAgent` 的 Prompt 里临时去掉：

```text
必须只输出 JSON 数组
```

或者测试多次，等模型自然输出不规范。

如果日志中看到：

```text
outputText 不是合法 JSON
但前端最终成功显示了标题 / 大纲
```

说明自动修复生效。

---

# 第九部分：数据库验收

## 22. 查询 Agent 日志

进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

执行：

```sql
SET NAMES utf8mb4;
USE ai_article_studio;

SELECT
  agent_name,
  status,
  cost_ms,
  LEFT(output_text, 500) AS output_preview,
  LEFT(error_message, 300) AS error_preview,
  create_time
FROM agent_log
ORDER BY create_time DESC
LIMIT 10;
```

验收点：

```text
1. 生成成功时 status = SUCCESS
2. 即使 output_text 原始格式不太干净，也可能最终成功
3. 如果修复后仍失败，status = FAILED，并记录错误原因
```

---

## 23. 检查标题数量和大纲数量

查询最新文章：

```sql
SELECT
  task_id,
  CHAR_LENGTH(title_options) AS title_len,
  CHAR_LENGTH(outline) AS outline_len,
  LEFT(title_options, 500) AS title_preview,
  LEFT(outline, 500) AS outline_preview
FROM article
ORDER BY update_time DESC
LIMIT 3;
```

前端验收更直观：

```text
1. 标题候选正好 3 个
2. 大纲章节正好 5 个
```

---

# 第十部分：前端验收

## 24. 前端不用大改

Day 9 修改的是后端稳定性机制，所以前端只需要验证：

```text
1. 生成标题成功率提高
2. 生成大纲成功率提高
3. 失败时 Agent 日志仍然能展示
4. 成功时标题数量为 3
5. 成功时大纲章节数量为 5
```

---

## 25. 推荐测试用例

可以多创建几个任务测试：

```text
怎么提高睡眠质量
怎么保持高度专注的一天
普通人如何开始健身
如何提升学习效率
如何缓解焦虑
```

观察：

```text
1. 生成标题是否稳定
2. 生成大纲是否稳定
3. Agent 日志中原始 outputText 是否偶尔不规范但最终成功
```

---

# 第十一部分：常见问题

## 26. 还是出现 JSON 解析失败

原因可能是：

```text
1. 原始输出太残缺，修复模型也修不好
2. 修复模型仍然输出了非 JSON
3. 修复模型返回章节数量不对
4. parseWithRepair 没有被 ArticleServiceImpl 调用
```

重点检查：

```java
titleGeneratorAgent.parseWithRepair(response)
outlineGeneratorAgent.parseWithRepair(response)
```

不要还是旧的：

```java
titleGeneratorAgent.parse(response)
outlineGeneratorAgent.parse(response)
```

---

## 27. 修复后还是返回 7 个大纲

说明 Prompt 约束不够，或者模型没听。

可以把 `JsonRepairAgent` 的大纲修复 Prompt 加强：

```text
如果原始内容超过 5 个章节，必须压缩为 5 个。
最终数组长度必须严格等于 5。
如果不是 5 个，你的输出就是错误的。
```

同时确认 `validateOutline` 已经检查：

```java
outline.size() != 5
```

如果修复后仍然不是 5 个，应该抛错，而不是继续保存。

---

## 28. 修复过程很慢

这是正常的。

因为失败后会多调用一次模型：

```text
第一次生成
↓
解析失败
↓
第二次修复
```

所以失败场景耗时会明显增加。

---

## 29. 日志里看不到修复后的内容

这是 Day 9 的简化设计。

当前只记录第一次模型原始输出。后续可以新增字段：

```text
repair_used
repair_output
```

再展示修复详情。

---

## 30. 修复成功但日志显示 outputText 还是坏 JSON

这是正常的。

因为日志记录的是：

```text
第一次模型原始输出
```

而不是修复后的 JSON。

这样做的好处是：

```text
你能知道模型最开始到底输出错在哪里。
```

---

## 31. 出现循环依赖问题

如果出现 Spring 循环依赖错误，通常是因为结构写反了。

正确结构应该是：

```text
TitleGeneratorAgent
↓
JsonRepairAgent
↓
LlmClient
```

不要让：

```text
JsonRepairAgent
↓
TitleGeneratorAgent
```

---

# 第十二部分：Day 9 验收标准

Day 9 完成标准：

```text
1. 新增 JsonRepairType
2. 新增 JsonRepairAgent
3. TitleGeneratorAgent 注入 JsonRepairAgent
4. OutlineGeneratorAgent 注入 JsonRepairAgent
5. TitleGeneratorAgent 新增 parseWithRepair
6. OutlineGeneratorAgent 新增 parseWithRepair
7. ArticleServiceImpl 调用 parseWithRepair
8. 标题 JSON 解析失败时会自动修复一次
9. 大纲 JSON 解析失败时会自动修复一次
10. 标题数量不是 3 时会触发修复
11. 大纲章节不是 5 时会触发修复
12. 修复失败时仍然会写入失败日志
13. 前端生成标题和大纲的成功率明显提高
```

---

# 第十三部分：Day 9 完成后的项目状态

到 Day 9 后，项目具备了更强的真实模型稳定性：

```text
真实大模型调用
标题 / 大纲结构化输出
JSON 自动修复
Agent 日志追踪
真实流式正文生成
数据库持久化
前端实时展示
```

这一步非常适合写进简历项目亮点：

```text
设计 JSON 修复重试机制，提高大模型结构化输出稳定性。
```

---

# Day 10 预告

Day 10 建议做：

```text
大纲编辑 + 基于新大纲重新生成正文
```

原因是现在项目已经能自动生成，但还缺少“人机协作”。

Day 10 后，流程会变成：

```text
AI 生成大纲
↓
用户编辑大纲
↓
基于用户修改后的大纲生成正文
```

这会让项目更像真实内容创作产品，而不是单纯的一键生成工具。
