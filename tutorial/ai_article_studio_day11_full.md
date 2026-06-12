# AI Article Studio：Day 11 完整开发教程

> Day 11 目标：实现 **标题编辑 + 正文编辑 + 保存正文**。  
> 让项目从“AI 生成后只能查看”升级为“用户可以继续编辑和保存”的人机协作写作工具。

---

## 0. Day 11 最终成果

完成 Day 11 后，文章详情页支持：

```text
1. 用户可以编辑已选择标题 selectedTitle
2. 保存标题后，可以基于新标题重新生成大纲
3. 用户可以编辑 AI 生成的正文 content
4. 保存正文后，刷新页面仍然保留用户修改后的正文
```

完整创作流程变成：

```text
AI 生成标题候选
↓
用户选择标题
↓
用户可手动修改标题
↓
AI 生成大纲
↓
用户编辑大纲
↓
AI 生成正文
↓
用户编辑正文
↓
保存最终正文
```

---

## 1. Day 11 要做什么？

今天主要做 2 个功能：

```text
功能一：标题编辑
功能二：正文编辑
```

新增 / 修改文件：

```text
后端：
- SaveTitleRequest.java
- SaveContentRequest.java
- ArticleMapper.java
- ArticleService.java
- ArticleServiceImpl.java
- ArticleController.java

前端：
- article.ts
- ArticleDetail.vue
```

新增接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/save-title` | POST | 保存用户编辑后的标题 |
| `/api/article/save-content` | POST | 保存用户编辑后的正文 |
```


# 第一部分：后端开发 —— 标题编辑

## 2. 新增 `SaveTitleRequest`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/dto/SaveTitleRequest.java
```

代码：

```java
package com.nana.aiarticlestudio.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveTitleRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;

    @NotBlank(message = "标题不能为空")
    private String selectedTitle;
}
```

作用：接收前端保存标题时提交的数据。

前端请求体类似：

```json
{
  "taskId": "xxx",
  "selectedTitle": "用户修改后的标题"
}
```

---

## 3. 修改 `ArticleMapper`：新增保存标题方法

路径：

```text
src/main/java/com/nana/aiarticlestudio/mapper/ArticleMapper.java
```

新增 import，如果已有则不用重复加：

```java
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
```

新增方法：

```java
@Update("""
        UPDATE article
        SET selected_title = #{selectedTitle},
            phase = #{phase},
            status = #{status}
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateSelectedTitle(@Param("taskId") String taskId,
                        @Param("selectedTitle") String selectedTitle,
                        @Param("phase") String phase,
                        @Param("status") String status);
```

作用：保存用户编辑后的 `selectedTitle`。

注意：这里只更新 `selected_title`，不自动清空 `outline`。用户可能只是轻微修改标题，不一定希望原来的大纲被清掉。

---

## 4. 修改 `ArticleService`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveTitleRequest;
```

新增方法：

```java
ArticleVO saveTitle(SaveTitleRequest request);
```

建议放在：

```java
ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request);

ArticleVO saveTitle(SaveTitleRequest request);

ArticleVO saveOutline(SaveOutlineRequest request);
```

---

## 5. 修改 `ArticleServiceImpl`：实现保存标题

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/ArticleServiceImpl.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveTitleRequest;
```

新增方法：

```java
@Override
public ArticleVO saveTitle(SaveTitleRequest request) {
    try {
        Article article = articleMapper.selectByTaskId(request.getTaskId());
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        if (!StringUtils.hasText(request.getSelectedTitle())) {
            throw new RuntimeException("标题不能为空");
        }

        int result = articleMapper.updateSelectedTitle(
                request.getTaskId(),
                request.getSelectedTitle().trim(),
                ArticlePhase.OUTLINE_EDITING.name(),
                ArticleStatus.SUCCESS.name()
        );

        if (result != 1) {
            throw new RuntimeException("保存标题失败");
        }

        return getByTaskId(request.getTaskId());
    } catch (Exception e) {
        throw new RuntimeException("保存标题失败：" + e.getMessage());
    }
}
```

作用：

```text
1. 检查文章任务是否存在
2. 检查标题不能为空
3. 保存 selectedTitle
4. 返回最新文章详情
```

为什么 phase 用 `OUTLINE_EDITING`？因为标题确认或编辑后，下一步通常是基于标题生成 / 编辑大纲。

---

## 6. 修改 `ArticleController`：新增保存标题接口

路径：

```text
src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveTitleRequest;
```

新增接口：

```java
@PostMapping("/save-title")
public BaseResponse<ArticleVO> saveTitle(@Valid @RequestBody SaveTitleRequest request) {
    return BaseResponse.success(articleService.saveTitle(request));
}
```

最终新增接口：

```text
POST /api/article/save-title
```


# 第二部分：后端开发 —— 正文编辑

## 7. 新增 `SaveContentRequest`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/dto/SaveContentRequest.java
```

代码：

```java
package com.nana.aiarticlestudio.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveContentRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;

    @NotBlank(message = "正文不能为空")
    private String content;
}
```

作用：接收前端保存正文时提交的数据。

前端请求体类似：

```json
{
  "taskId": "xxx",
  "content": "# 用户编辑后的正文"
}
```

---

## 8. 复用 `ArticleMapper.updateContent`

前面已经有：

```java
int updateContent(String taskId, String content, String phase, String status);
```

Day 11 保存正文可以直接复用它，不需要新增 Mapper 方法。

原因：AI 生成正文和用户保存正文，本质上都是更新 `article.content`。

---

## 9. 修改 `ArticleService`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveContentRequest;
```

新增方法：

```java
ArticleVO saveContent(SaveContentRequest request);
```

建议放在：

```java
ArticleVO generateContent(String taskId);

ArticleVO saveContent(SaveContentRequest request);
```

---

## 10. 修改 `ArticleServiceImpl`：实现保存正文

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveContentRequest;
```

新增方法：

```java
@Override
public ArticleVO saveContent(SaveContentRequest request) {
    try {
        Article article = articleMapper.selectByTaskId(request.getTaskId());
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        if (!StringUtils.hasText(request.getContent())) {
            throw new RuntimeException("正文不能为空");
        }

        int result = articleMapper.updateContent(
                request.getTaskId(),
                request.getContent(),
                ArticlePhase.CONTENT_GENERATION.name(),
                ArticleStatus.SUCCESS.name()
        );

        if (result != 1) {
            throw new RuntimeException("保存正文失败");
        }

        return getByTaskId(request.getTaskId());
    } catch (Exception e) {
        throw new RuntimeException("保存正文失败：" + e.getMessage());
    }
}
```

作用：

```text
1. 检查文章任务是否存在
2. 检查正文不能为空
3. 保存 content
4. 返回最新文章详情
```

---

## 11. 修改 `ArticleController`：新增保存正文接口

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveContentRequest;
```

新增接口：

```java
@PostMapping("/save-content")
public BaseResponse<ArticleVO> saveContent(@Valid @RequestBody SaveContentRequest request) {
    return BaseResponse.success(articleService.saveContent(request));
}
```

最终新增接口：

```text
POST /api/article/save-content
```


# 第三部分：后端测试

## 12. 重启后端

在 IDEA 中停止后端，然后重新运行：

```text
AiArticleStudioApplication
```

如果启动失败，重点检查：

```text
1. SaveTitleRequest / SaveContentRequest 包路径是否正确
2. ArticleService 是否新增 saveTitle / saveContent
3. ArticleServiceImpl 是否实现方法
4. ArticleMapper 是否新增 updateSelectedTitle
5. ArticleController 是否新增接口
```

---

## 13. PowerShell 测试保存标题

执行：

```powershell
$body = @{
  taskId = "你的taskId"
  selectedTitle = "这是我手动修改后的标题"
} | ConvertTo-Json
```

含义：构造保存标题接口的 JSON 请求体。

调用接口：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/save-title" -ContentType "application/json" -Body $body
```

含义：向后端发送保存标题请求。

验收：

```text
返回 code = 0
data.selectedTitle 是新标题
```

---

## 14. PowerShell 测试保存正文

执行：

```powershell
$body = @{
  taskId = "你的taskId"
  content = "# 手动编辑后的正文`n`n这是我保存后的正文内容。"
} | ConvertTo-Json
```

含义：

```text
构造保存正文接口的 JSON 请求体。
`n 是 PowerShell 中的换行符。
```

调用接口：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/save-content" -ContentType "application/json" -Body $body
```

含义：向后端发送保存正文请求。

验收：

```text
返回 code = 0
data.content 是新正文
```


# 第四部分：前端开发 —— API

## 15. 修改 `article.ts`

路径：

```text
frontend/src/api/article.ts
```

新增类型：

```ts
export interface SaveTitleRequest {
  taskId: string
  selectedTitle: string
}

export interface SaveContentRequest {
  taskId: string
  content: string
}
```

新增接口方法：

```ts
export const saveTitle = async (data: SaveTitleRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/save-title', data)
  return res.data
}

export const saveContent = async (data: SaveContentRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/save-content', data)
  return res.data
}
```

作用：前端调用后端保存标题和保存正文接口。

---

# 第五部分：前端开发 —— 标题编辑

## 16. 修改 `ArticleDetail.vue` import

路径：

```text
frontend/src/pages/ArticleDetail.vue
```

从 `../api/article` 中新增导入：

```ts
saveTitle,
saveContent,
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
  saveContent,
  saveOutline,
  saveTitle,
  streamGenerateContentUrl,
  type AgentLogVO,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
} from '../api/article'
```

---

## 17. 新增标题编辑状态

在状态区新增：

```ts
const titleEditing = ref(false)
const titleSaving = ref(false)
const editableTitle = ref('')
```

含义：

| 状态 | 作用 |
|---|---|
| `titleEditing` | 是否正在编辑标题 |
| `titleSaving` | 保存标题按钮 loading |
| `editableTitle` | 用户正在编辑的标题文本 |

---

## 18. 新增标题编辑方法

```ts
const handleStartEditTitle = () => {
  editableTitle.value = article.value?.selectedTitle || ''
  titleEditing.value = true
}
```

作用：把当前 `selectedTitle` 复制到 `editableTitle`，并进入编辑模式。

---

## 19. 新增取消标题编辑方法

```ts
const handleCancelEditTitle = () => {
  editableTitle.value = ''
  titleEditing.value = false
}
```

作用：放弃本次标题编辑。

---

## 20. 新增保存标题方法

```ts
const handleSaveTitle = async () => {
  if (!taskId.value) {
    return
  }

  if (!editableTitle.value.trim()) {
    message.warning('标题不能为空')
    return
  }

  titleSaving.value = true
  try {
    const res = await saveTitle({
      taskId: taskId.value,
      selectedTitle: editableTitle.value.trim(),
    })

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    article.value = res.data
    selectedTitle.value = res.data.selectedTitle || ''
    titleEditing.value = false
    editableTitle.value = ''

    message.success('标题保存成功')
  } catch (error) {
    console.error(error)
    message.error('保存标题失败')
  } finally {
    titleSaving.value = false
  }
}
```

作用：

```text
1. 校验标题不能为空
2. 调用保存标题接口
3. 更新 article.value
4. 同步 selectedTitle
5. 退出编辑模式
```

重点：

```ts
selectedTitle.value = res.data.selectedTitle || ''
```

如果不写，可能会出现标题保存成功，但页面当前选中的标题状态没有同步。

---

## 21. 新增标题展示卡片

建议放在标题候选卡片和大纲卡片之间。

模板：

```vue
<a-card v-if="article?.selectedTitle" class="card" title="已选标题">
  <template #extra>
    <a-space>
      <a-button v-if="!titleEditing" @click="handleStartEditTitle">
        编辑标题
      </a-button>

      <template v-else>
        <a-button @click="handleCancelEditTitle">
          取消
        </a-button>

        <a-button type="primary" :loading="titleSaving" @click="handleSaveTitle">
          保存标题
        </a-button>
      </template>
    </a-space>
  </template>

  <template v-if="!titleEditing">
    <h2>{{ article.selectedTitle }}</h2>
    <p class="hint-text">
      如果修改标题，后续可以基于新标题重新生成大纲。
    </p>
  </template>

  <template v-else>
    <a-input
      v-model:value="editableTitle"
      placeholder="请输入标题"
      size="large"
    />
  </template>
</a-card>
```

作用：用户可以看到当前已选标题，并手动编辑保存。


# 第六部分：前端开发 —— 正文编辑

## 22. 新增正文编辑状态

在状态区新增：

```ts
const contentEditing = ref(false)
const contentSaving = ref(false)
const editableContent = ref('')
```

含义：

| 状态 | 作用 |
|---|---|
| `contentEditing` | 是否正在编辑正文 |
| `contentSaving` | 保存正文按钮 loading |
| `editableContent` | 用户正在编辑的正文 Markdown |

---

## 23. 新增正文编辑方法

```ts
const handleStartEditContent = () => {
  editableContent.value = article.value?.content || ''
  contentEditing.value = true
}
```

作用：把当前正文复制到 `editableContent`，并进入编辑模式。

---

## 24. 新增取消正文编辑方法

```ts
const handleCancelEditContent = () => {
  editableContent.value = ''
  contentEditing.value = false
}
```

作用：放弃本次正文编辑。

---

## 25. 新增保存正文方法

```ts
const handleSaveContent = async () => {
  if (!taskId.value) {
    return
  }

  if (!editableContent.value.trim()) {
    message.warning('正文不能为空')
    return
  }

  contentSaving.value = true
  try {
    const res = await saveContent({
      taskId: taskId.value,
      content: editableContent.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    article.value = res.data
    contentEditing.value = false
    editableContent.value = ''

    message.success('正文保存成功')
  } catch (error) {
    console.error(error)
    message.error('保存正文失败')
  } finally {
    contentSaving.value = false
  }
}
```

作用：

```text
1. 校验正文不能为空
2. 调用保存正文接口
3. 更新 article.value
4. 退出编辑模式
```

---

## 26. 修改正文卡片模板

找到原来的正文展示卡片，可能类似：

```vue
<a-card v-if="article?.content || streamContent" class="card" title="文章正文">
  <div class="markdown-content">
    ...
  </div>
</a-card>
```

改成：

```vue
<a-card v-if="article?.content || streamContent || contentEditing" class="card" title="文章正文">
  <template #extra>
    <a-space>
      <a-button
        v-if="!contentEditing && (article?.content || streamContent)"
        @click="handleStartEditContent"
      >
        编辑正文
      </a-button>

      <template v-if="contentEditing">
        <a-button @click="handleCancelEditContent">
          取消
        </a-button>

        <a-button type="primary" :loading="contentSaving" @click="handleSaveContent">
          保存正文
        </a-button>
      </template>
    </a-space>
  </template>

  <template v-if="!contentEditing">
    <div class="markdown-content">
      <pre>{{ article?.content || streamContent }}</pre>
    </div>
  </template>

  <template v-else>
    <a-textarea
      v-model:value="editableContent"
      placeholder="请输入或编辑正文 Markdown"
      :rows="18"
    />
  </template>
</a-card>
```

作用：

```text
1. 非编辑模式展示正文
2. 点击编辑正文后显示 textarea
3. 保存后写入数据库
```


# 第七部分：前端测试流程

## 27. 重启前端

如果前端正在运行，按：

```text
Ctrl + C
```

然后执行：

```powershell
npm run dev
```

含义：启动 Vue 前端开发服务器。

---

## 28. 测试标题编辑

打开：

```text
http://localhost:5173/article/list
```

测试流程：

```text
1. 创建文章任务
2. 生成标题
3. 选择一个标题并生成大纲
4. 在“已选标题”卡片点击“编辑标题”
5. 修改标题
6. 点击“保存标题”
7. 刷新页面
8. 确认标题仍然是修改后的标题
```

继续测试：

```text
9. 点击“确认标题并生成大纲”
10. 查看新大纲是否围绕修改后的标题生成
```

---

## 29. 测试正文编辑

测试流程：

```text
1. 生成正文
2. 在“文章正文”卡片点击“编辑正文”
3. 修改正文内容
4. 点击“保存正文”
5. 刷新页面
6. 确认正文仍然是修改后的内容
```

---

# 第八部分：数据库验收

## 30. 检查标题和正文是否更新

进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

含义：进入 MySQL 容器，并使用 utf8mb4 字符集。

执行：

```sql
SET NAMES utf8mb4;
USE ai_article_studio;

SELECT
  task_id,
  selected_title,
  LEFT(content, 500) AS content_preview,
  update_time
FROM article
ORDER BY update_time DESC
LIMIT 3;
```

验收标准：

```text
selected_title 是你手动编辑后的标题
content_preview 中能看到你手动编辑后的正文
```


# 第九部分：常见问题

## 31. 标题保存成功，但页面选中标题没变

原因可能是没有同步：

```ts
selectedTitle.value = res.data.selectedTitle || ''
```

保存标题成功后要有：

```ts
article.value = res.data
selectedTitle.value = res.data.selectedTitle || ''
```

---

## 32. 修改标题后，大纲没有自动变化

这是正常的。

Day 11 的逻辑是：

```text
保存标题
↓
用户手动点击“确认标题并生成大纲”
```

不会自动重新生成大纲。

原因：重新生成大纲会消耗模型 API。用户可能只是想先保存标题，不一定马上生成大纲。

---

## 33. 正文保存成功，但刷新后还是旧正文

检查：

```text
1. saveContent 接口是否成功返回
2. ArticleServiceImpl 是否调用 updateContent
3. article.value 是否更新为 res.data
4. 数据库 content 是否真的更新
```

MySQL 检查：

```sql
SELECT LEFT(content, 500)
FROM article
WHERE task_id = '你的taskId';
```

---

## 34. 编辑正文时格式看起来不是 Markdown 渲染

当前 Day 11 只是保存 Markdown 文本，没有做 Markdown 渲染优化。

如果正文展示用的是：

```vue
<pre>{{ article?.content || streamContent }}</pre>
```

它会以纯文本形式展示。

后续可以接入 Markdown 渲染库，例如：

```text
markdown-it
```

但 Day 11 先不做，避免分散重点。

---

## 35. 保存标题后，旧大纲还在

这是正常的。

Day 11 不会自动清空旧大纲。

原因：用户可能只是轻微修改标题，不一定希望大纲被清掉。

后续可以提示：

```text
标题已修改，建议重新生成大纲
```

---

## 36. 编辑正文后 Agent 日志没有新增

这是正常的。

Day 11 的正文编辑是：

```text
用户手动保存正文
```

不是 Agent 生成正文，所以默认不写入 `agent_log`。

如果后续想记录用户编辑行为，可以新增：

```text
operation_log
```

或者在 `agent_log` 里加一种：

```text
ManualEdit
```

当前 Day 11 不做。

---

# 第十部分：Day 11 验收标准

Day 11 完成标准：

```text
1. 新增 SaveTitleRequest
2. 新增 SaveContentRequest
3. ArticleMapper 新增 updateSelectedTitle
4. ArticleService 新增 saveTitle / saveContent
5. ArticleServiceImpl 实现 saveTitle / saveContent
6. ArticleController 新增 /save-title 和 /save-content
7. article.ts 新增 saveTitle / saveContent
8. 前端支持编辑标题
9. 前端支持保存标题
10. 前端支持编辑正文
11. 前端支持保存正文
12. 刷新页面后标题仍然保留
13. 刷新页面后正文仍然保留
14. 可以基于修改后的标题重新生成大纲
15. 可以基于修改后的大纲重新生成正文
```

---

# 第十一部分：Day 11 完成后的项目状态

到 Day 11 后，项目已经具备完整的人机协作写作链路：

```text
AI 生成标题
用户编辑标题
AI 生成大纲
用户编辑大纲
AI 生成正文
用户编辑正文
保存最终内容
```

这已经比普通 Demo 更接近真实 AI 写作产品。

---

# Day 12 预告

Day 12 建议做：

```text
导出 Markdown 文件
```

让用户可以把最终文章导出成 `.md` 文件。

后续还可以继续扩展：

```text
导出 Word
文章版本历史
Markdown 渲染预览
复制全文
```

Day 12 做完后，项目的“作品完整度”会明显提高。
