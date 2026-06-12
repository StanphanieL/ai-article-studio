# AI Article Studio：Day 10 完整开发教程

> Day 10 目标：实现 **大纲编辑 + 基于用户修改后的大纲重新生成正文**。  
> 让项目从“AI 一键生成文章”升级为“AI 生成 + 用户编辑 + AI 再生成”的人机协作写作流程。

---

## 0. Day 10 最终成果

完成 Day 10 后，文章详情页的大纲区域支持：

```text
AI 生成大纲
↓
用户点击“编辑大纲”
↓
用户修改章节标题和章节说明
↓
用户新增 / 删除章节
↓
保存大纲
↓
基于修改后的大纲重新生成正文
```

当前标题仍然不能编辑。Day 10 做的是 **大纲编辑**，不是标题编辑。

---

## 1. Day 10 要解决什么问题？

Day 9 之前，文章生成流程是：

```text
确认标题
↓
AI 自动生成大纲
↓
AI 根据大纲生成正文
```

这个流程的问题是：

```text
用户只能接受 AI 生成的大纲。
如果某个章节方向不合适，用户无法调整。
```

Day 10 之后，流程变成：

```text
确认标题
↓
AI 自动生成大纲
↓
用户编辑大纲
↓
保存大纲
↓
AI 根据用户修改后的大纲重新生成正文
```

这更接近真实内容创作产品，因为真实写作往往是：

```text
AI 先给草稿
用户调整方向
AI 再继续生成
```

---

## 2. Day 10 新增和修改内容

后端新增：

```text
SaveOutlineRequest.java
```

后端修改：

```text
ArticleMapper.java
ArticleService.java
ArticleServiceImpl.java
ArticleController.java
```

前端修改：

```text
frontend/src/api/article.ts
frontend/src/pages/ArticleDetail.vue
```

新增接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/save-outline` | POST | 保存用户编辑后的文章大纲 |

---

# 第一部分：后端开发

## 3. 新增 `SaveOutlineRequest`

路径：

```text
src/main/java/com/nana/aiarticlestudio/model/dto/SaveOutlineRequest.java
```

代码：

```java
package com.nana.aiarticlestudio.model.dto;

import com.nana.aiarticlestudio.model.vo.OutlineItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SaveOutlineRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;

    @NotEmpty(message = "大纲不能为空")
    private List<OutlineItem> outline;
}
```

作用：

```text
接收前端保存大纲时提交的数据。
```

前端提交的数据格式类似：

```json
{
  "taskId": "xxx",
  "outline": [
    {
      "heading": "一、章节标题",
      "description": "章节说明"
    }
  ]
}
```

字段解释：

| 字段 | 含义 |
|---|---|
| `taskId` | 当前文章任务 ID |
| `outline` | 用户编辑后的大纲数组 |

---

## 4. 修改 `ArticleMapper`

路径：

```text
src/main/java/com/nana/aiarticlestudio/mapper/ArticleMapper.java
```

新增 import：

```java
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
```

新增方法：

```java
@Update("""
        UPDATE article
        SET outline = #{outline},
            phase = #{phase},
            status = #{status}
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int updateOutline(@Param("taskId") String taskId,
                  @Param("outline") String outline,
                  @Param("phase") String phase,
                  @Param("status") String status);
```

作用：

```text
根据 taskId 更新 article 表里的 outline 字段。
```

注意：

```text
outline 在数据库中仍然保存为 JSON 字符串。
```

---

## 5. 修改 `ArticleService`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/ArticleService.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveOutlineRequest;
```

新增方法：

```java
ArticleVO saveOutline(SaveOutlineRequest request);
```

建议放在：

```java
ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request);

ArticleVO saveOutline(SaveOutlineRequest request);

ArticleVO generateContent(String taskId);
```

---

## 6. 修改 `ArticleServiceImpl`

路径：

```text
src/main/java/com/nana/aiarticlestudio/service/impl/ArticleServiceImpl.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveOutlineRequest;
```

新增方法：

```java
@Override
public ArticleVO saveOutline(SaveOutlineRequest request) {
    try {
        Article article = articleMapper.selectByTaskId(request.getTaskId());
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        if (request.getOutline() == null || request.getOutline().isEmpty()) {
            throw new RuntimeException("大纲不能为空");
        }

        for (int i = 0; i < request.getOutline().size(); i++) {
            OutlineItem item = request.getOutline().get(i);

            if (item == null
                    || !StringUtils.hasText(item.getHeading())
                    || !StringUtils.hasText(item.getDescription())) {
                throw new RuntimeException("第 " + (i + 1) + " 个大纲项不完整");
            }
        }

        String outlineJson = objectMapper.writeValueAsString(request.getOutline());

        int result = articleMapper.updateOutline(
                request.getTaskId(),
                outlineJson,
                ArticlePhase.OUTLINE_EDITING.name(),
                ArticleStatus.SUCCESS.name()
        );

        if (result != 1) {
            throw new RuntimeException("保存大纲失败");
        }

        return getByTaskId(request.getTaskId());
    } catch (Exception e) {
        throw new RuntimeException("保存大纲失败：" + e.getMessage());
    }
}
```

作用：

```text
1. 查询文章任务是否存在
2. 校验大纲不能为空
3. 校验每个大纲项必须有 heading 和 description
4. 把大纲对象转成 JSON 字符串
5. 保存到 article.outline
6. 返回最新文章详情
```

注意：

```text
这里不强制必须 5 个章节。
```

原因：

```text
Day 9 的“必须 5 个章节”是约束 AI 自动生成阶段。
Day 10 是用户手动编辑阶段，用户可能希望 4 个章节或 6 个章节。
```

---

## 7. 修改 `ArticleController`

路径：

```text
src/main/java/com/nana/aiarticlestudio/controller/ArticleController.java
```

新增 import：

```java
import com.nana.aiarticlestudio.model.dto.SaveOutlineRequest;
```

新增接口：

```java
@PostMapping("/save-outline")
public BaseResponse<ArticleVO> saveOutline(@Valid @RequestBody SaveOutlineRequest request) {
    return BaseResponse.success(articleService.saveOutline(request));
}
```

最终新增接口：

```text
POST /api/article/save-outline
```

---

# 第二部分：后端测试

## 8. 重启后端

在 IDEA 里停止后端，然后重新运行：

```text
AiArticleStudioApplication
```

如果启动失败，重点检查：

```text
1. SaveOutlineRequest 包路径是否正确
2. ArticleService 是否新增 saveOutline
3. ArticleServiceImpl 是否实现 saveOutline
4. ArticleMapper 是否 import 了 @Update / @Param
5. ArticleController 是否 import 了 SaveOutlineRequest
```

---

## 9. 用 PowerShell 测试保存大纲接口

先准备一个已有的 `taskId`。

执行：

```powershell
$body = @{
  taskId = "你的taskId"
  outline = @(
    @{
      heading = "一、重新编辑后的第一章"
      description = "这是用户手动修改后的章节说明"
    },
    @{
      heading = "二、重新编辑后的第二章"
      description = "这是第二个章节说明"
    }
  )
} | ConvertTo-Json -Depth 5
```

含义：

```text
构造保存大纲接口需要的 JSON 请求体。
- taskId：文章任务 ID
- outline：大纲数组
- ConvertTo-Json -Depth 5：把 PowerShell 对象转成 JSON，Depth 5 表示支持较深层级的嵌套对象。
```

然后调用接口：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/save-outline" -ContentType "application/json" -Body $body
```

含义：

```text
向后端保存大纲接口发送 POST 请求。
```

如果返回：

```text
code = 0
message = ok
data.outline 有新内容
```

说明后端接口成功。

---

# 第三部分：前端开发

## 10. 修改 `article.ts`

路径：

```text
frontend/src/api/article.ts
```

新增请求类型：

```ts
export interface SaveOutlineRequest {
  taskId: string
  outline: OutlineItem[]
}
```

新增接口方法：

```ts
export const saveOutline = async (data: SaveOutlineRequest) => {
  const res = await request.post<BaseResponse<ArticleVO>>('/api/article/save-outline', data)
  return res.data
}
```

作用：

```text
前端调用后端保存大纲接口。
```

---

## 11. 修改 `ArticleDetail.vue` import

路径：

```text
frontend/src/pages/ArticleDetail.vue
```

从 `../api/article` 中新增导入：

```ts
saveOutline,
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
  saveOutline,
  streamGenerateContentUrl,
  type AgentLogVO,
  type ArticleVO,
  type OutlineItem,
  type TitleOption,
} from '../api/article'
```

---

## 12. 新增大纲编辑状态

在 `<script setup>` 的状态区新增：

```ts
const outlineEditing = ref(false)
const outlineSaving = ref(false)
const editableOutline = ref<OutlineItem[]>([])
```

含义：

| 状态 | 作用 |
|---|---|
| `outlineEditing` | 当前是否处于大纲编辑模式 |
| `outlineSaving` | 保存大纲按钮 loading |
| `editableOutline` | 当前正在编辑的大纲数组 |

---

## 13. 新增进入编辑方法

新增方法：

```ts
const handleStartEditOutline = () => {
  editableOutline.value = outlineItems.value.map((item) => ({
    heading: item.heading,
    description: item.description,
  }))

  outlineEditing.value = true
}
```

作用：

```text
把当前文章大纲复制一份到 editableOutline 中，进入编辑模式。
```

为什么要复制？

```text
避免用户编辑时直接修改 article.value.outline。
只有点击“保存大纲”后，才真正提交到后端。
```

---

## 14. 新增取消编辑方法

新增方法：

```ts
const handleCancelEditOutline = () => {
  editableOutline.value = []
  outlineEditing.value = false
}
```

作用：

```text
放弃本次编辑，恢复查看模式。
```

---

## 15. 新增章节和删除章节方法

新增章节：

```ts
const handleAddOutlineItem = () => {
  editableOutline.value.push({
    heading: '',
    description: '',
  })
}
```

删除章节：

```ts
const handleRemoveOutlineItem = (index: number) => {
  editableOutline.value.splice(index, 1)
}
```

---

## 16. 新增保存大纲方法

新增方法：

```ts
const handleSaveOutline = async () => {
  if (!taskId.value) {
    return
  }

  if (editableOutline.value.length === 0) {
    message.warning('大纲不能为空')
    return
  }

  for (let i = 0; i < editableOutline.value.length; i++) {
    const item = editableOutline.value[i]

    if (!item.heading || !item.heading.trim()) {
      message.warning(`第 ${i + 1} 个章节标题不能为空`)
      return
    }

    if (!item.description || !item.description.trim()) {
      message.warning(`第 ${i + 1} 个章节说明不能为空`)
      return
    }
  }

  outlineSaving.value = true
  try {
    const res = await saveOutline({
      taskId: taskId.value,
      outline: editableOutline.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    article.value = res.data
    outlineEditing.value = false
    editableOutline.value = []

    message.success('大纲保存成功')
  } catch (error) {
    console.error(error)
    message.error('保存大纲失败')
  } finally {
    outlineSaving.value = false
  }
}
```

作用：

```text
1. 校验大纲不能为空
2. 校验每个章节标题不能为空
3. 校验每个章节说明不能为空
4. 调用 saveOutline 接口
5. 更新 article.value
6. 退出编辑模式
```

---

# 第四部分：修改大纲卡片模板

## 17. 替换原来的文章大纲卡片

找到原来的大纲卡片，替换为：

```vue
<a-card v-if="outlineItems.length > 0 || outlineEditing" class="card" title="文章大纲">
  <template #extra>
    <a-space>
      <a-button v-if="!outlineEditing" @click="handleStartEditOutline">
        编辑大纲
      </a-button>

      <template v-else>
        <a-button @click="handleAddOutlineItem">
          新增章节
        </a-button>

        <a-button @click="handleCancelEditOutline">
          取消
        </a-button>

        <a-button type="primary" :loading="outlineSaving" @click="handleSaveOutline">
          保存大纲
        </a-button>
      </template>
    </a-space>
  </template>

  <template v-if="!outlineEditing">
    <a-timeline>
      <a-timeline-item v-for="item in outlineItems" :key="item.heading">
        <h3>{{ item.heading }}</h3>
        <p>{{ item.description }}</p>
      </a-timeline-item>
    </a-timeline>

    <div class="action-row">
      <a-button type="primary" :loading="contentLoading" @click="handleGenerateContent">
        基于当前大纲生成正文
      </a-button>

      <a-button type="primary" ghost :loading="streaming" @click="handleStreamGenerateContent">
        流式生成正文
      </a-button>

      <a-button type="primary" danger ghost :loading="realStreaming" @click="handleRealStreamGenerateContent">
        真实流式生成正文
      </a-button>
    </div>
  </template>

  <template v-else>
    <div class="outline-edit-list">
      <a-card
        v-for="(item, index) in editableOutline"
        :key="index"
        size="small"
        class="outline-edit-item"
      >
        <template #title>
          第 {{ index + 1 }} 个章节
        </template>

        <template #extra>
          <a-button danger size="small" @click="handleRemoveOutlineItem(index)">
            删除
          </a-button>
        </template>

        <a-form layout="vertical">
          <a-form-item label="章节标题">
            <a-input
              v-model:value="item.heading"
              placeholder="请输入章节标题"
            />
          </a-form-item>

          <a-form-item label="章节说明">
            <a-textarea
              v-model:value="item.description"
              placeholder="请输入章节说明"
              :rows="3"
            />
          </a-form-item>
        </a-form>
      </a-card>
    </div>
  </template>
</a-card>
```

---

## 18. 模板逻辑解释

这句：

```vue
<a-card v-if="outlineItems.length > 0 || outlineEditing" class="card" title="文章大纲">
```

含义：

```text
只要已经有大纲，或者正在编辑大纲，就显示大纲卡片。
```

这部分：

```vue
<a-button v-if="!outlineEditing" @click="handleStartEditOutline">
  编辑大纲
</a-button>
```

含义：

```text
查看模式下显示“编辑大纲”按钮。
```

编辑模式下显示：

```text
新增章节 / 取消 / 保存大纲
```

---

## 19. 新增样式

在 `<style scoped>` 末尾新增：

```css
.outline-edit-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.outline-edit-item {
  border: 1px solid #f0f0f0;
}

.action-row {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
```

如果之前已经有 `.action-row`，不要重复写，只保留一份即可。

---

# 第五部分：完整测试流程

## 20. 重启前端

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
启动 Vue 前端开发服务器。
```

---

## 21. 测试大纲编辑

打开：

```text
http://localhost:5173/article/list
```

测试流程：

```text
1. 创建一个新文章任务
2. 进入详情页
3. 点击生成标题
4. 选择标题
5. 点击确认标题并生成大纲
6. 在大纲卡片右上角点击“编辑大纲”
7. 修改第一个章节标题
8. 修改第一个章节说明
9. 点击“新增章节”
10. 输入新增章节标题和说明
11. 删除一个章节
12. 点击“保存大纲”
13. 页面回到查看模式
14. 刷新页面，确认修改后的大纲仍然存在
```

---

## 22. 测试基于新大纲生成正文

保存大纲后，点击：

```text
基于当前大纲生成正文
```

或者：

```text
真实流式生成正文
```

验收：

```text
1. 正文可以正常生成
2. 正文内容会围绕用户修改后的大纲展开
3. 数据库 article.content 有新正文
4. Agent 日志正常写入
```

---

# 第六部分：数据库验收

## 23. 检查 outline 是否被更新

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
  selected_title,
  LEFT(outline, 1000) AS outline_preview,
  update_time
FROM article
ORDER BY update_time DESC
LIMIT 3;
```

验收标准：

```text
outline_preview 中能看到你手动修改后的章节标题和章节说明。
```

---

# 第七部分：常见问题

## 24. 保存大纲时报 400 或接口失败

常见原因：

```text
1. SaveOutlineRequest 字段名和前端不一致
2. 前端传的是 outlines，但后端接收 outline
3. 请求体没有 taskId
4. outline 是空数组
```

确认前端传的是：

```ts
{
  taskId: taskId.value,
  outline: editableOutline.value,
}
```

不是：

```ts
{
  taskId: taskId.value,
  outlines: editableOutline.value,
}
```

---

## 25. 保存成功，但刷新后还是旧大纲

可能原因：

```text
1. ArticleMapper.updateOutline 没有真正更新 outline 字段
2. article.value 没有更新成 res.data
3. 数据库连接的不是当前项目数据库
```

检查 `handleSaveOutline` 中是否有：

```ts
article.value = res.data
```

检查 MySQL：

```sql
SELECT LEFT(outline, 1000)
FROM article
WHERE task_id = '你的taskId';
```

---

## 26. 编辑时一改输入框，查看模式也变了

原因：

```text
你可能直接引用了 outlineItems.value，没有复制。
```

应该用：

```ts
editableOutline.value = outlineItems.value.map((item) => ({
  heading: item.heading,
  description: item.description,
}))
```

不要写成：

```ts
editableOutline.value = outlineItems.value
```

---

## 27. 新增章节后保存失败

原因：

```text
新增章节的 heading 或 description 为空。
```

解决：

```text
保存前填好章节标题和章节说明。
```

这是正常校验。

---

## 28. 删除到 0 个章节后保存失败

这是正常的。

因为后端和前端都要求：

```text
大纲不能为空。
```

---

## 29. 保存大纲后正文没有自动更新

这是正常的。

Day 10 的逻辑是：

```text
保存大纲
↓
用户手动点击“基于当前大纲生成正文”
```

不会自动重新生成正文。

原因：

```text
重新生成正文会消耗模型 API。
用户可能只是想先保存大纲，不一定马上生成正文。
```

---

## 30. 标题现在能不能编辑？

不能。

当前标题流程仍然是：

```text
AI 生成 3 个标题候选
↓
用户只能选择其中一个
↓
selectedTitle 保存到数据库
```

Day 10 做的是大纲编辑，没有做标题编辑。

如果后续要支持标题编辑，可以新增：

```text
编辑 selectedTitle
保存标题
基于修改后的标题重新生成大纲
```

---

# 第八部分：Day 10 验收标准

Day 10 完成标准：

```text
1. 新增 SaveOutlineRequest
2. ArticleMapper 新增 updateOutline
3. ArticleService 新增 saveOutline
4. ArticleServiceImpl 实现 saveOutline
5. ArticleController 新增 /api/article/save-outline
6. article.ts 新增 saveOutline API
7. ArticleDetail.vue 支持编辑大纲
8. 支持新增章节
9. 支持删除章节
10. 支持保存大纲
11. 保存后刷新页面，大纲仍然存在
12. 可以基于修改后的大纲重新生成正文
```

---

# 第九部分：Day 10 完成后的项目状态

到 Day 10 后，项目从：

```text
AI 自动生成文章
```

升级成：

```text
AI 生成 + 用户编辑 + AI 再生成
```

真实内容创作产品通常不是完全自动化，而是：

```text
AI 先给草稿
用户调整方向
AI 再继续生成
```

这就是典型的人机协作写作流程。

---

# Day 11 预告

Day 11 建议做：

```text
正文编辑 + 保存正文版本
```

也就是让用户可以：

```text
1. 编辑 AI 生成的正文
2. 保存编辑后的正文
3. 后续可扩展版本历史
```

完成 Day 11 后，项目会更像一个真正可用的 AI 写作工具。
