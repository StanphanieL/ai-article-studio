# AI Article Studio：原计划 Day 13 完整开发教程

> Day 13 目标：实现 **Agent 日志优化**。  
> 让 Agent 执行日志从“简单查看”升级为“方便调试、复盘和项目展示”的可观测面板。

---

## 0. Day 13 最终成果

完成 Day 13 后，文章详情页的 Agent 日志支持：

```text
1. 手动刷新日志
2. 按状态筛选日志：全部 / 成功 / 失败
3. 显示成功日志数量和失败日志数量
4. SUCCESS / FAILED 用不同颜色标签展示
5. 日志标题展示 Agent 名称、状态、耗时、创建时间
6. 支持复制 Prompt
7. 支持复制模型输出
8. 支持复制错误信息
9. 长日志内容不会撑爆页面
10. Agent 执行完成后自动刷新日志
```

Day 13 不修改后端，主要修改：

```text
frontend/src/pages/ArticleDetail.vue
```

---

## 1. 当前日志的问题

原来的日志区域能查看日志，但调试体验不够好：

```text
1. 没有手动刷新按钮
2. 不能只看失败日志
3. SUCCESS / FAILED 不够直观
4. Prompt 和模型输出不能一键复制
5. 日志标题信息不够清楚
6. 查失败原因不方便
```

Day 13 的目标就是补齐这些能力。

---

# 第一部分：新增日志筛选状态

## 2. 新增 `logStatusFilter`

打开：

```text
frontend/src/pages/ArticleDetail.vue
```

找到状态区：

```ts
const agentLogs = ref<AgentLogVO[]>([])
const logLoading = ref(false)
```

在下面新增：

```ts
const logStatusFilter = ref<'ALL' | 'SUCCESS' | 'FAILED'>('ALL')
```

含义：

| 值 | 含义 |
|---|---|
| `ALL` | 查看全部日志 |
| `SUCCESS` | 只看成功日志 |
| `FAILED` | 只看失败日志 |

---

## 3. 新增筛选后的日志 computed

在 computed 区域新增：

```ts
const filteredAgentLogs = computed(() => {
  if (logStatusFilter.value === 'ALL') {
    return agentLogs.value
  }

  return agentLogs.value.filter((log) => log.status === logStatusFilter.value)
})
```

作用：

```text
根据当前筛选条件，返回真正要展示在页面上的日志列表。
```

---

## 4. 新增日志数量统计

继续在 computed 区域新增：

```ts
const successLogCount = computed(() => {
  return agentLogs.value.filter((log) => log.status === 'SUCCESS').length
})

const failedLogCount = computed(() => {
  return agentLogs.value.filter((log) => log.status === 'FAILED').length
})
```

作用：

```text
显示成功日志和失败日志数量。
```

---

# 第二部分：新增复制与状态辅助方法

## 5. 新增通用复制方法

在方法区新增：

```ts
const handleCopyText = async (text?: string, successMessage = '复制成功') => {
  if (!text || !text.trim()) {
    message.warning('暂无内容可复制')
    return
  }

  try {
    await navigator.clipboard.writeText(text)
    message.success(successMessage)
  } catch (error) {
    console.error(error)
    message.error('复制失败，请检查浏览器权限')
  }
}
```

作用：

```text
复用复制逻辑，支持复制 Prompt、模型输出、错误信息。
```

---

## 6. 新增日志状态颜色函数

新增：

```ts
const getLogStatusColor = (status: string) => {
  if (status === 'SUCCESS') {
    return 'green'
  }

  if (status === 'FAILED') {
    return 'red'
  }

  return 'default'
}
```

作用：

```text
SUCCESS 显示绿色标签；
FAILED 显示红色标签；
其他状态显示默认标签。
```

---

## 7. 新增 Agent 名称展示函数

新增：

```ts
const getAgentDisplayName = (agentName: string) => {
  const map: Record<string, string> = {
    TitleGeneratorAgent: '标题生成 Agent',
    OutlineGeneratorAgent: '大纲生成 Agent',
    ContentGeneratorAgent: '正文生成 Agent',
    JsonRepairAgent: 'JSON 修复 Agent',
  }

  return map[agentName] || agentName || 'UnknownAgent'
}
```

作用：

```text
把技术化 Agent 类名转换成更容易理解的展示名称。
```

---

# 第三部分：替换 Agent 日志卡片

## 8. 替换原来的日志卡片

找到原来的日志卡片：

```vue
<a-card class="card" title="Agent 执行日志" :loading="logLoading">
  ...
</a-card>
```

整体替换为：

```vue
<a-card class="card" :loading="logLoading">
  <template #title>
    Agent 执行日志
  </template>

  <template #extra>
    <a-space>
      <a-radio-group v-model:value="logStatusFilter" button-style="solid">
        <a-radio-button value="ALL">
          全部 {{ agentLogs.length }}
        </a-radio-button>

        <a-radio-button value="SUCCESS">
          成功 {{ successLogCount }}
        </a-radio-button>

        <a-radio-button value="FAILED">
          失败 {{ failedLogCount }}
        </a-radio-button>
      </a-radio-group>

      <a-button :loading="logLoading" @click="loadAgentLogs">
        刷新日志
      </a-button>
    </a-space>
  </template>

  <a-empty
    v-if="filteredAgentLogs.length === 0"
    description="暂无符合条件的 Agent 日志"
  />

  <a-collapse v-else>
    <a-collapse-panel
      v-for="log in filteredAgentLogs"
      :key="log.id"
    >
      <template #header>
        <div class="log-panel-header">
          <a-space>
            <span class="log-agent-name">
              {{ getAgentDisplayName(log.agentName) }}
            </span>

            <a-tag :color="getLogStatusColor(log.status)">
              {{ log.status }}
            </a-tag>

            <span class="log-cost">
              {{ log.costMs || 0 }}ms
            </span>

            <span class="log-time">
              {{ log.createTime }}
            </span>
          </a-space>
        </div>
      </template>

      <div class="log-meta">
        <p>
          <strong>Agent：</strong>
          {{ log.agentName }}
        </p>

        <p>
          <strong>执行状态：</strong>
          <a-tag :color="getLogStatusColor(log.status)">
            {{ log.status }}
          </a-tag>
        </p>

        <p>
          <strong>耗时：</strong>
          {{ log.costMs || 0 }}ms
        </p>

        <p>
          <strong>创建时间：</strong>
          {{ log.createTime }}
        </p>

        <p v-if="log.errorMessage">
          <strong>错误信息：</strong>
          <span class="error-text">{{ log.errorMessage }}</span>
        </p>
      </div>

      <a-divider />

      <div class="log-section">
        <div class="log-section-header">
          <h4>输入 Prompt</h4>

          <a-button
            size="small"
            @click.stop="handleCopyText(log.inputText, 'Prompt 已复制')"
          >
            复制 Prompt
          </a-button>
        </div>

        <pre class="log-text">{{ log.inputText || '无' }}</pre>
      </div>

      <div class="log-section">
        <div class="log-section-header">
          <h4>模型输出</h4>

          <a-button
            size="small"
            @click.stop="handleCopyText(log.outputText, '模型输出已复制')"
          >
            复制输出
          </a-button>
        </div>

        <pre class="log-text">{{ log.outputText || '无' }}</pre>
      </div>

      <div v-if="log.errorMessage" class="log-section">
        <div class="log-section-header">
          <h4>错误信息</h4>

          <a-button
            size="small"
            danger
            @click.stop="handleCopyText(log.errorMessage, '错误信息已复制')"
          >
            复制错误
          </a-button>
        </div>

        <pre class="log-text error-log-text">{{ log.errorMessage }}</pre>
      </div>
    </a-collapse-panel>
  </a-collapse>
</a-card>
```

---

# 第四部分：新增日志样式

## 9. 新增 CSS

在 `<style scoped>` 末尾新增：

```css
.log-panel-header {
  display: flex;
  align-items: center;
  width: 100%;
}

.log-agent-name {
  font-weight: 600;
}

.log-cost {
  color: #666;
  font-size: 13px;
}

.log-time {
  color: #999;
  font-size: 13px;
}

.log-meta {
  color: #333;
  font-size: 14px;
}

.log-meta p {
  margin: 0 0 8px;
}

.log-section {
  margin-top: 16px;
}

.log-section-header {
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-section-header h4 {
  margin: 0;
}

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

.error-text {
  color: #cf1322;
}

.error-log-text {
  background: #fff1f0;
  color: #a8071a;
}
```

如果之前已经有 `.log-text`，用这一版覆盖旧的即可。

---

# 第五部分：优化日志加载逻辑

## 10. 优化 `loadAgentLogs`

建议把：

```ts
agentLogs.value = res.data
```

改成：

```ts
agentLogs.value = res.data || []
```

完整推荐版本：

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

作用：

```text
防止后端 data 偶尔为空时，前端 agentLogs 变成 undefined。
```

---

## 11. 确认 Agent 执行后自动刷新日志

需要确认这些函数执行完成后都有：

```ts
await loadAgentLogs()
```

建议检查：

```text
1. handleGenerateTitles
2. handleConfirmTitle
3. handleGenerateContent
4. handleStreamGenerateContent done
5. handleStreamGenerateContent fail
6. handleRealStreamGenerateContent done
7. handleRealStreamGenerateContent fail
8. handleRealStreamGenerateContent onerror
```

---

# 第六部分：修复 await 报错

## 12. 问题现象

补 Day13 时出现前端编译错误：

```text
Unexpected reserved word 'await'
```

报错位置类似：

```ts
await loadAgentLogs()
```

原因：

```text
在一个不是 async 的函数里使用了 await。
```

---

## 13. 修复 `fail` 监听

如果原来写成：

```ts
eventSource.addEventListener('fail', (event) => {
  const data = parseSseData(event as MessageEvent)

  if (data.message) {
    streamLogs.value.push(data.message)
    message.error(data.message)
  } else {
    message.error('流式生成失败')
    await loadAgentLogs()
  }

  closedByDone = true
  eventSource.close()
  streaming.value = false
})
```

要改成：

```ts
eventSource.addEventListener('fail', async (event) => {
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

  await loadAgentLogs()
})
```

---

## 14. 修复 `onerror`

如果 `eventSource.onerror` 中也写了：

```ts
await loadAgentLogs()
```

也要改成：

```ts
eventSource.onerror = async () => {
  if (!closedByDone) {
    message.error('SSE 连接异常')
    streamLogs.value.push('SSE 连接异常')
    await loadAgentLogs()
  }

  eventSource.close()
  streaming.value = false
}
```

结论：

```text
有 await 的函数，外层必须加 async。
```

---

# 第七部分：测试流程

## 15. 重启前端

如果前端正在运行，按：

```text
Ctrl + C
```

然后执行：

```powershell
npm run dev
```

命令含义：

```text
启动 Vue 前端开发服务器。
```

---

## 16. 测试日志刷新按钮

测试：

```text
1. 进入某个文章详情页
2. 点击“生成标题”
3. 等待生成完成
4. 查看 Agent 执行日志是否自动出现
5. 点击“刷新日志”
6. 日志正常刷新，不报错
```

---

## 17. 测试状态筛选

测试：

```text
1. 点击“全部”
2. 应该看到所有日志
3. 点击“成功”
4. 只看到 SUCCESS 日志
5. 点击“失败”
6. 只看到 FAILED 日志
```

如果当前没有失败日志，“失败”为空是正常的。

---

## 18. 测试复制 Prompt / 输出

测试：

```text
1. 展开一条 Agent 日志
2. 点击“复制 Prompt”
3. 粘贴到记事本
4. 确认内容是输入 Prompt
5. 点击“复制输出”
6. 粘贴确认是模型输出
```

如果某条日志有错误信息，再点击“复制错误”验证。

---

## 19. 测试长日志展示

测试：

```text
1. 展开一条 Prompt 很长的日志
2. 查看 Prompt 内容区是否内部滚动
3. 页面不应该被撑得特别长
```

这是由 CSS 控制的：

```css
max-height: 360px;
overflow: auto;
```

---

# 第八部分：常见问题

## 20. 点击复制没有反应

可能是浏览器剪贴板权限问题。

当前开发环境：

```text
http://localhost:5173
```

一般没问题。失败时看浏览器 Console 是否有权限报错。

---

## 21. 筛选失败日志后为空

可能原因：

```text
当前确实没有 FAILED 日志。
```

先看“全部”里有没有红色 FAILED 标签。

---

## 22. 状态标签颜色不对

检查：

```ts
const getLogStatusColor = (status: string) => {
  if (status === 'SUCCESS') {
    return 'green'
  }

  if (status === 'FAILED') {
    return 'red'
  }

  return 'default'
}
```

以及模板：

```vue
<a-tag :color="getLogStatusColor(log.status)">
  {{ log.status }}
</a-tag>
```

---

## 23. 复制按钮点击后折叠面板也被触发

按钮上要加：

```vue
@click.stop="..."
```

`.stop` 的作用是：

```text
阻止点击事件继续冒泡，避免触发折叠面板展开或收起。
```

---

## 24. 出现 `Unexpected reserved word 'await'`

原因：

```text
在非 async 函数里用了 await。
```

解决：

```text
给包含 await 的回调函数前面加 async。
```

例如：

```ts
eventSource.addEventListener('fail', async (event) => {
  await loadAgentLogs()
})
```

---

# 第九部分：Day 13 验收标准

原计划 Day13 完成标准：

```text
1. Agent 日志卡片有“刷新日志”按钮
2. 支持按 ALL / SUCCESS / FAILED 筛选
3. 显示成功日志数量
4. 显示失败日志数量
5. SUCCESS 用绿色标签展示
6. FAILED 用红色标签展示
7. 日志标题展示 Agent 名称、状态、耗时、时间
8. 支持复制 Prompt
9. 支持复制模型输出
10. 支持复制错误信息
11. 长日志内容不会撑爆页面
12. Agent 执行完成后日志能自动刷新
13. fail / onerror 中 await 使用正确，不再编译报错
```

---

# 第十部分：Day 13 完成后的项目状态

现在项目具备更强的调试和展示能力：

```text
AI 生成标题 / 大纲 / 正文
JSON 修复重试
正文编辑与 Markdown 预览
复制与导出 Markdown
Agent 日志追踪
Agent 日志筛选
Prompt / 输出复制
失败日志快速定位
```

这对秋招项目很重要，因为它说明你不是只做了“调用模型”，还做了：

```text
可观测性
可调试性
失败排查
工程化展示
```

---

# 下一步

补完原计划 Day13 后，进入真正的：

```text
Day14：错误处理与任务状态优化
```
