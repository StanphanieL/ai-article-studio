# AI Article Studio：Day 3 完整开发教程

> 目标：把 Day 2 的普通文章任务 CRUD 项目，升级成一个初步的 Agentic AI 创作流程。Day 3 暂时不接真实大模型 API，先用 `MockLlmClient` 模拟模型返回，把标题生成和大纲生成的完整链路跑通。

---

## 0. Day 3 最终成果

完成 Day 3 后，流程变成：

```text
创建文章任务
↓
进入文章详情页
↓
点击“生成标题”
↓
系统生成 3 个标题候选
↓
用户选择一个标题
↓
点击“确认标题并生成大纲”
↓
系统生成文章大纲
↓
详情页展示标题候选、已选标题、大纲
```

新增后端接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/generate-titles/{taskId}` | POST | 根据选题生成标题候选 |
| `/api/article/confirm-title` | POST | 用户确认标题，并生成大纲 |

新增核心后端类：

| 类名 | 作用 |
|---|---|
| `ArticlePhase` | 定义文章当前业务阶段 |
| `ArticleStatus` | 定义任务执行状态 |
| `TitleOption` | 标题候选数据结构 |
| `OutlineItem` | 大纲章节数据结构 |
| `ConfirmTitleRequest` | 确认标题请求对象 |
| `LlmClient` | 大模型调用抽象接口 |
| `MockLlmClient` | 模拟大模型返回 |
| `TitleGeneratorAgent` | 标题生成 Agent |
| `OutlineGeneratorAgent` | 大纲生成 Agent |

---

# 第一部分：设计思路

## 1. 为什么 Day 3 不直接接真实大模型？

一上来接真实模型，很容易同时遇到 API Key、网络、模型返回格式、Prompt 调试和业务流程问题。Day 3 先用：

```text
LlmClient 接口
↓
MockLlmClient 模拟模型返回
↓
Agent 流程、数据库保存、前端展示全部跑通
↓
后面只替换 LlmClient 实现，就可以接真实大模型
```

这样可以先把产品和工程链路跑通，后续再接真实模型。

## 2. Day 3 后端调用链路

```text
ArticleController
↓
ArticleService
↓
ArticleServiceImpl
↓
TitleGeneratorAgent / OutlineGeneratorAgent
↓
LlmClient
↓
MockLlmClient
```

数据库变化：

```text
生成标题：
title_options 保存标题候选 JSON
phase 改为 TITLE_SELECTION
status 改为 SUCCESS
```

```text
确认标题并生成大纲：
selected_title 保存用户选择的标题
outline 保存大纲 JSON
phase 改为 OUTLINE_EDITING
status 改为 SUCCESS
```

---

# 第二部分：后端开发

## 3. 创建文章阶段枚举 `ArticlePhase`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/enums/ArticlePhase.java
```

代码：

```java
package com.nana.aiarticlestudio.model.enums;

public enum ArticlePhase {

    CREATED,

    TITLE_SELECTION,

    OUTLINE_EDITING,

    CONTENT_GENERATION,

    COMPLETED,

    FAILED
}
```

作用：定义文章任务当前处于哪个业务阶段。

| 阶段 | 含义 |
|---|---|
| `CREATED` | 任务刚创建 |
| `TITLE_SELECTION` | 标题候选已生成，等待用户选择标题 |
| `OUTLINE_EDITING` | 大纲已生成，等待用户确认或编辑 |
| `CONTENT_GENERATION` | 正文生成中 |
| `COMPLETED` | 任务完成 |
| `FAILED` | 任务失败 |

---

## 4. 创建任务状态枚举 `ArticleStatus`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/enums/ArticleStatus.java
```

代码：

```java
package com.nana.aiarticlestudio.model.enums;

public enum ArticleStatus {

    INIT,

    RUNNING,

    SUCCESS,

    FAILED
}
```

`phase` 和 `status` 的区别：

```text
phase 更偏业务阶段，例如标题选择、大纲编辑。
status 更偏执行状态，例如运行中、成功、失败。
```

---

## 5. 创建标题候选模型 `TitleOption`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/vo/TitleOption.java
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
public class TitleOption {

    private String title;

    private String reason;
}
```

作用：表示一个标题候选。

```json
{
  "title": "AI 产品经理如何用工具提升 10 倍效率",
  "reason": "突出效率提升，适合职场和技术读者"
}
```

---

## 6. 创建大纲项模型 `OutlineItem`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/vo/OutlineItem.java
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
public class OutlineItem {

    private String heading;

    private String description;
}
```

作用：表示文章大纲中的一个章节。

---

## 7. 创建确认标题请求 DTO `ConfirmTitleRequest`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/dto/ConfirmTitleRequest.java
```

代码：

```java
package com.nana.aiarticlestudio.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmTitleRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;

    @NotBlank(message = "标题不能为空")
    private String selectedTitle;
}
```

作用：前端选择标题后，把 `taskId` 和 `selectedTitle` 传给后端。

常见报错：

```text
java: 找不到符号
符号: 类 ConfirmTitleRequest
位置: 类 com.nana.aiarticlestudio.controller.ArticleController
```

解决：确认文件已创建，并在 `ArticleController.java` 顶部导入：

```java
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
```

---

## 8. 创建 LLM 客户端接口 `LlmClient`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/LlmClient.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

public interface LlmClient {

    String chat(String prompt);
}
```

作用：统一大模型调用入口。Agent 不直接依赖具体模型厂商，只依赖 `LlmClient` 接口。

---

## 9. 创建模拟模型客户端 `MockLlmClient`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/MockLlmClient.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

import org.springframework.stereotype.Component;

@Component
public class MockLlmClient implements LlmClient {

    @Override
    public String chat(String prompt) {
        if (prompt.contains("生成 3 个标题")) {
            return """
                    [
                      {
                        "title": "AI 产品经理如何用工具提升 10 倍效率",
                        "reason": "突出效率提升，适合职场和技术读者"
                      },
                      {
                        "title": "从需求到落地：AI 产品经理的高效工作法",
                        "reason": "强调完整工作链路，适合项目经验分享"
                      },
                      {
                        "title": "普通人也能学会的 AI 产品经理提效指南",
                        "reason": "表达更轻量，适合入门读者"
                      }
                    ]
                    """;
        }

        if (prompt.contains("生成文章大纲")) {
            return """
                    [
                      {
                        "heading": "一、为什么 AI 产品经理需要系统化提效",
                        "description": "说明 AI 产品经理面对需求分析、原型设计、数据分析和跨团队沟通时，容易被碎片任务消耗。"
                      },
                      {
                        "heading": "二、用 AI 辅助需求分析",
                        "description": "介绍如何用 AI 梳理用户痛点、竞品信息和功能优先级。"
                      },
                      {
                        "heading": "三、用 AI 提升原型和文档效率",
                        "description": "说明如何让 AI 辅助生成 PRD、用户故事、页面文案和交互说明。"
                      },
                      {
                        "heading": "四、用 AI 做会议总结和任务追踪",
                        "description": "介绍如何把会议纪要、行动项和项目状态自动结构化。"
                      },
                      {
                        "heading": "五、如何避免过度依赖 AI",
                        "description": "强调产品判断、业务理解和用户洞察仍然需要人来负责。"
                      }
                    ]
                    """;
        }

        return "[]";
    }
}
```

作用：模拟大模型返回。Day 3 不需要 API Key，也能把 Agent 流程、数据库保存、前端展示全部跑通。

---

## 10. 创建标题生成 Agent `TitleGeneratorAgent`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/TitleGeneratorAgent.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.TitleOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TitleGeneratorAgent {

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper;

    public List<TitleOption> generate(String topic, String style) {
        try {
            String prompt = """
                    你是一个专业的爆款文章标题策划专家。
                    请根据用户选题生成 3 个标题候选。

                    用户选题：%s
                    文章风格：%s

                    要求：
                    1. 生成 3 个标题
                    2. 每个标题包含 title 和 reason
                    3. 必须返回 JSON 数组
                    4. 不要输出任何解释性文字

                    请生成 3 个标题。
                    """.formatted(topic, style);

            String json = llmClient.chat(prompt);

            return objectMapper.readValue(json, new TypeReference<List<TitleOption>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("生成标题失败：" + e.getMessage());
        }
    }
}
```

核心流程：

```text
拼 prompt
↓
调用 llmClient.chat(prompt)
↓
得到 JSON 字符串
↓
用 ObjectMapper 解析成 List<TitleOption>
```

---

## 11. 创建大纲生成 Agent `OutlineGeneratorAgent`

路径：

```text
src/main/java/com/nana/aiarticlestudio/agent/OutlineGeneratorAgent.java
```

代码：

```java
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.OutlineItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutlineGeneratorAgent {

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper;

    public List<OutlineItem> generate(String topic, String selectedTitle, String style) {
        try {
            String prompt = """
                    你是一个专业的文章大纲策划专家。
                    请根据用户选题和已选择标题生成文章大纲。

                    用户选题：%s
                    已选标题：%s
                    文章风格：%s

                    要求：
                    1. 生成 5 个章节
                    2. 每个章节包含 heading 和 description
                    3. 必须返回 JSON 数组
                    4. 不要输出任何解释性文字

                    请生成文章大纲。
                    """.formatted(topic, selectedTitle, style);

            String json = llmClient.chat(prompt);

            return objectMapper.readValue(json, new TypeReference<List<OutlineItem>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("生成大纲失败：" + e.getMessage());
        }
    }
}
```

---

## 12. 修改 `ArticleMapper`

打开：

```text
src/main/java/com/nana/aiarticlestudio/mapper/ArticleMapper.java
```

新增两个方法：

```java
@Update("""
        UPDATE article
        SET title_options = #{titleOptions},
            phase = #{phase},
            status = #{status}
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateTitleOptions(@Param("taskId") String taskId,
                       @Param("titleOptions") String titleOptions,
                       @Param("phase") String phase,
                       @Param("status") String status);

@Update("""
        UPDATE article
        SET selected_title = #{selectedTitle},
            outline = #{outline},
            phase = #{phase},
            status = #{status}
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateSelectedTitleAndOutline(@Param("taskId") String taskId,
                                  @Param("selectedTitle") String selectedTitle,
                                  @Param("outline") String outline,
                                  @Param("phase") String phase,
                                  @Param("status") String status);
```

作用：

```text
updateTitleOptions：保存标题候选 JSON。
updateSelectedTitleAndOutline：保存已选标题和大纲 JSON。
```

---

## 13. 修改 `ArticleService`

打开：

```text
src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

完整代码改成：

```java
package com.nana.aiarticlestudio.service;

import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
import com.nana.aiarticlestudio.model.vo.ArticleVO;

public interface ArticleService {

    String createArticle(ArticleCreateRequest request);

    ArticleVO getByTaskId(String taskId);

    PageResult<ArticleVO> listArticles(ArticleListRequest request);

    Boolean deleteByTaskId(String taskId);

    ArticleVO generateTitles(String taskId);

    ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request);
}
```

---

## 14. 修改 `ArticleServiceImpl`

打开：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/ArticleServiceImpl.java
```

新增 import：

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.agent.OutlineGeneratorAgent;
import com.nana.aiarticlestudio.agent.TitleGeneratorAgent;
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
import com.nana.aiarticlestudio.model.enums.ArticlePhase;
import com.nana.aiarticlestudio.model.enums.ArticleStatus;
import com.nana.aiarticlestudio.model.vo.OutlineItem;
import com.nana.aiarticlestudio.model.vo.TitleOption;
```

在类中新增依赖：

```java
private final TitleGeneratorAgent titleGeneratorAgent;

private final OutlineGeneratorAgent outlineGeneratorAgent;

private final ObjectMapper objectMapper;
```

把 `createArticle` 里的：

```java
article.setPhase("CREATED");
article.setStatus("INIT");
```

改成：

```java
article.setPhase(ArticlePhase.CREATED.name());
article.setStatus(ArticleStatus.INIT.name());
```

在类末尾新增两个方法：

```java
@Override
public ArticleVO generateTitles(String taskId) {
    try {
        Article article = articleMapper.selectByTaskId(taskId);
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        List<TitleOption> titleOptions = titleGeneratorAgent.generate(
                article.getTopic(),
                article.getStyle()
        );

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
        throw new RuntimeException("生成标题失败：" + e.getMessage());
    }
}

@Override
public ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request) {
    try {
        Article article = articleMapper.selectByTaskId(request.getTaskId());
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        List<OutlineItem> outline = outlineGeneratorAgent.generate(
                article.getTopic(),
                request.getSelectedTitle(),
                article.getStyle()
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
        throw new RuntimeException("生成大纲失败：" + e.getMessage());
    }
}
```

---

## 15. 修改 `ArticleController`

打开：

```text
src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
```

新增两个接口：

```java
@PostMapping("/generate-titles/{taskId}")
public BaseResponse<ArticleVO> generateTitles(@PathVariable String taskId) {
    return BaseResponse.success(articleService.generateTitles(taskId));
}

@PostMapping("/confirm-title")
public BaseResponse<ArticleVO> confirmTitle(@Valid @RequestBody ConfirmTitleRequest request) {
    return BaseResponse.success(articleService.confirmTitleAndGenerateOutline(request));
}
```

最终接口包括：

```text
POST /api/article/create
GET  /api/article/{taskId}
POST /api/article/list
POST /api/article/delete/{taskId}
POST /api/article/generate-titles/{taskId}
POST /api/article/confirm-title
```

---

## 16. 修改 `pom.xml` 编码配置

如果出现标题或大纲乱码，例如：

```text
äº§åç»ç...
```

打开 `pom.xml`，找到：

```xml
<properties>
    <java.version>21</java.version>
</properties>
```

改成：

```xml
<properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <maven.compiler.encoding>UTF-8</maven.compiler.encoding>
</properties>
```

注意：

```xml
<?xml version="1.0" encoding="UTF-8"?>
```

只表示 `pom.xml` 文件本身是 UTF-8，不等于 Java 源码编译时一定按 UTF-8 读取。

修改后：

```text
1. IDEA 右侧 Maven 面板点击刷新
2. 打开 MockLlmClient.java，确认中文显示正常
3. Build → Rebuild Project
4. 重启后端
5. 删除旧乱码数据，用前端重新创建任务测试
```

---

# 第三部分：后端测试

## 17. 重启后端

在 IDEA 底部 `Run` 窗口点击红色方块停止后端，然后重新运行：

```text
AiArticleStudioApplication
```

看到下面两行说明启动成功：

```text
Tomcat started on port 8123
Started AiArticleStudioApplication
```

---

## 18. PowerShell 测试生成标题

打开新的 PowerShell。

创建任务：

```powershell
$createRes = Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/create" -ContentType "application/json" -Body '{"topic":"AI产品经理如何提升效率","style":"TECH"}'
$taskId = $createRes.data
$taskId
```

含义：创建一条文章任务，并把返回的 `taskId` 保存到 `$taskId` 变量里。

生成标题：

```powershell
$titleRes = Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/generate-titles/$taskId"
$titleRes.data
```

你应该看到：

```text
phase: TITLE_SELECTION
status: SUCCESS
titleOptions: 一段 JSON 字符串
```

注意：PowerShell 直接发送中文 JSON 时，有可能出现 `topic: AI????`。中文主流程建议用前端页面测试。

---

## 19. PowerShell 测试确认标题并生成大纲

执行：

```powershell
$body = @{
  taskId = $taskId
  selectedTitle = "AI 产品经理如何用工具提升 10 倍效率"
} | ConvertTo-Json

$outlineRes = Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/confirm-title" -ContentType "application/json" -Body $body
$outlineRes.data
```

你应该看到：

```text
phase: OUTLINE_EDITING
status: SUCCESS
selectedTitle: AI 产品经理如何用工具提升 10 倍效率
outline: 一段 JSON 字符串
```

---

# 第四部分：前端改造

## 20. 修改前端 `article.ts`

打开：

```text
frontend/src/api/article.ts
```

整体替换成：

```ts
import request from './request'

export interface BaseResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}

export interface ArticleVO {
  id: number
  taskId: string
  topic: string
  style: string
  phase: string
  status: string
  titleOptions?: string
  selectedTitle?: string
  outline?: string
  content?: string
  fullContent?: string
  errorMessage?: string
  createTime: string
  updateTime: string
}

export interface TitleOption {
  title: string
  reason: string
}

export interface OutlineItem {
  heading: string
  description: string
}

export interface ArticleCreateRequest {
  topic: string
  style?: string
}

export interface ArticleListRequest {
  pageNo: number
  pageSize: number
}

export interface ConfirmTitleRequest {
  taskId: string
  selectedTitle: string
}

export const createArticle = async (data: ArticleCreateRequest) => {
  const res = await request.post<BaseResponse<string>>('/api/article/create', data)
  return res.data
}

export const listArticles = async (data: ArticleListRequest) => {
  const res = await request.post<BaseResponse<PageResult<ArticleVO>>>('/api/article/list', data)
  return res.data
}

export const getArticle = async (taskId: string) => {
  const res = await request.get<BaseResponse<ArticleVO>>(`/api/article/${taskId}`)
  return res.data
}

export const deleteArticle = async (taskId: string) => {
  const res = await request.post<BaseResponse<boolean>>(`/api/article/delete/${taskId}`)
  return res.data
}

export const generateTitles = async (taskId: string) => {
  const res = await request.post<BaseResponse<ArticleVO>>(`/api/article/generate-titles/${taskId}`)
  return res.data
}

export const confirmTitle = async (data: ConfirmTitleRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/confirm-title', data)
  return res.data
}
```

---

## 21. 替换 `ArticleDetail.vue`

打开：

```text
frontend/src/pages/ArticleDetail.vue
```

整体替换成：

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  confirmTitle,
  generateTitles,
  getArticle,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
} from '../api/article'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const article = ref<ArticleVO | null>(null)
const selectedTitle = ref('')

const taskId = computed(() => route.params.taskId as string)

const parseJsonArray = <T,>(jsonText?: string): T[] => {
  if (!jsonText) {
    return []
  }

  try {
    return JSON.parse(jsonText) as T[]
  } catch (error) {
    console.error(error)
    return []
  }
}

const titleOptions = computed(() => {
  return parseJsonArray<TitleOption>(article.value?.titleOptions)
})

const outlineItems = computed(() => {
  return parseJsonArray<OutlineItem>(article.value?.outline)
})

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

const handleGenerateTitles = async () => {
  if (!taskId.value) {
    return
  }

  loading.value = true
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
    loading.value = false
  }
}

const handleConfirmTitle = async () => {
  if (!selectedTitle.value) {
    message.warning('请先选择一个标题')
    return
  }

  loading.value = true
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
    loading.value = false
  }
}

const goBack = () => {
  router.push('/article/list')
}

onMounted(() => {
  loadArticle()
})
</script>

<template>
  <div class="page">
    <a-space>
      <a-button @click="goBack">返回列表</a-button>
      <a-button type="primary" :loading="loading" @click="handleGenerateTitles">
        生成标题
      </a-button>
    </a-space>

    <a-card v-if="article" class="card" :loading="loading">
      <template #title>
        文章任务详情
      </template>

      <a-descriptions bordered :column="1">
        <a-descriptions-item label="任务 ID">
          {{ article.taskId }}
        </a-descriptions-item>

        <a-descriptions-item label="选题">
          {{ article.topic }}
        </a-descriptions-item>

        <a-descriptions-item label="风格">
          {{ article.style }}
        </a-descriptions-item>

        <a-descriptions-item label="阶段">
          {{ article.phase }}
        </a-descriptions-item>

        <a-descriptions-item label="状态">
          {{ article.status }}
        </a-descriptions-item>

        <a-descriptions-item label="已选标题">
          {{ article.selectedTitle || '暂未选择' }}
        </a-descriptions-item>

        <a-descriptions-item label="创建时间">
          {{ article.createTime }}
        </a-descriptions-item>

        <a-descriptions-item label="更新时间">
          {{ article.updateTime }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card v-if="titleOptions.length > 0" class="card" title="标题候选">
      <a-radio-group v-model:value="selectedTitle" class="title-list">
        <a-radio
          v-for="item in titleOptions"
          :key="item.title"
          :value="item.title"
          class="title-option"
        >
          <div class="title-text">{{ item.title }}</div>
          <div class="reason-text">{{ item.reason }}</div>
        </a-radio>
      </a-radio-group>

      <div class="action-row">
        <a-button type="primary" :loading="loading" @click="handleConfirmTitle">
          确认标题并生成大纲
        </a-button>
      </div>
    </a-card>

    <a-card v-if="outlineItems.length > 0" class="card" title="文章大纲">
      <a-timeline>
        <a-timeline-item v-for="item in outlineItems" :key="item.heading">
          <h3>{{ item.heading }}</h3>
          <p>{{ item.description }}</p>
        </a-timeline-item>
      </a-timeline>
    </a-card>

    <a-empty v-if="!article && !loading" description="文章任务不存在" />
  </div>
</template>

<style scoped>
.page {
  padding: 32px;
  background: #f5f5f5;
  min-height: 100vh;
}

.card {
  margin-top: 24px;
}

.title-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.title-option {
  padding: 12px;
  border: 1px solid #eee;
  border-radius: 8px;
  width: 100%;
}

.title-text {
  font-weight: 600;
  margin-bottom: 4px;
}

.reason-text {
  color: #666;
  font-size: 14px;
}

.action-row {
  margin-top: 24px;
}
</style>
```

---

# 第五部分：前端测试

## 22. 重启前端

如果前端正在运行，按：

```text
Ctrl + C
```

停止。

然后执行：

```powershell
npm run dev
```

含义：重新启动前端开发服务器。

---

## 23. 完整测试流程

先确认后端还在运行：

```text
http://localhost:8123/api/health
```

返回：

```text
ok
```

然后打开前端：

```text
http://localhost:5173/article/list
```

测试：

```text
1. 创建一条文章任务
2. 点击查看详情
3. 点击生成标题
4. 看到 3 个标题候选
5. 选择一个标题
6. 点击确认标题并生成大纲
7. 看到文章大纲
```

成功后详情页应该看到：

```text
phase: OUTLINE_EDITING
status: SUCCESS
已选标题：你选择的标题
文章大纲：5 个章节
```

---

# 第六部分：常见问题

## 24. 找不到 `ConfirmTitleRequest`

原因通常是：

```text
1. ConfirmTitleRequest.java 没创建
2. ArticleController 没 import
3. ConfirmTitleRequest.java 的 package 写错
```

解决：

```java
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
```

## 25. PowerShell 测试中文变成问号

现象：

```text
topic: AI??????????
```

原因：PowerShell 直接发送中文 JSON 时，可能在请求过程中把中文编码成问号。

解决：中文主流程建议用前端页面测试。前端创建中文任务可以正常保存和展示。

## 26. 标题或大纲出现 `äº§å...` 乱码

解决步骤：

```text
1. IDEA 检查 File → Settings → Editor → File Encodings，确认都是 UTF-8
2. pom.xml 中加入编译编码配置
3. IDEA 右侧 Maven 面板刷新
4. Build → Rebuild Project
5. 重启后端
6. 删除旧乱码数据，用前端重新创建任务测试
```

pom.xml 配置：

```xml
<properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <maven.compiler.encoding>UTF-8</maven.compiler.encoding>
</properties>
```

---

# 第七部分：Day 3 验收标准

Day 3 完成标准：

```text
1. 后端新增两个 Agent：TitleGeneratorAgent、OutlineGeneratorAgent
2. 后端新增 LlmClient 抽象
3. 后端可以生成标题候选，并保存到 title_options
4. 后端可以确认标题，并生成 outline
5. 前端详情页可以点击生成标题
6. 前端可以选择标题
7. 前端可以生成并展示大纲
8. article 表中的 phase 能从 CREATED 变成 TITLE_SELECTION，再变成 OUTLINE_EDITING
```

如果页面能显示标题候选和文章大纲，说明 Day 3 已经完成。

![image-20260603155722678](/Users/stephanie/Library/Application Support/typora-user-images/image-20260603155722678.png)

---

# 第八部分：Day 3 暂时不做的事

Day 3 暂时不做：

```text
不接真实大模型 API
不做正文生成
不做 SSE 流式输出
不做大纲编辑
不做图片生成
```

今天重点是跑通：

```text
Agent 分层
模型调用抽象
标题生成流程
大纲生成流程
前端交互闭环
```

---

# 第九部分：Day 4 预告

Day 4 会继续做：

```text
1. 用户确认大纲
2. 正文生成 Agent
3. 根据大纲生成文章正文
4. 数据库保存 content
5. 前端展示 Markdown 正文
```

Day 4 完成后，项目会从“标题 + 大纲生成器”升级成“完整文章生成器”的第一版。
