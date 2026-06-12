# AI Article Studio：Day 2 完整开发教程

> 目标：在 Day 1 的基础上，完成文章任务 CRUD 后端接口，并做出一个可用的前端任务管理页面。  
> Day 2 暂时不接 AI，重点是把「文章任务」的数据链路打通。

---

## 0. Day 2 最终成果

完成后你应该具备：

```text
1. 后端可以创建文章任务
2. 后端可以查询文章详情
3. 后端可以查询文章列表
4. 后端可以软删除文章任务
5. 前端可以创建文章任务
6. 前端可以展示文章任务列表
7. 前端可以进入文章详情页
8. 中文选题可以正常保存和展示
```

后端接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/create` | POST | 创建文章任务 |
| `/api/article/{taskId}` | GET | 查询文章详情 |
| `/api/article/list` | POST | 查询文章列表 |
| `/api/article/delete/{taskId}` | POST | 删除文章任务 |

前端页面：

| 页面 | 地址 | 作用 |
|---|---|---|
| 文章任务列表页 | `/article/list` | 创建任务、查看列表、删除任务 |
| 文章详情页 | `/article/detail/:taskId` | 查看某个文章任务详情 |

---

# 第一部分：后端开发

---

## 1. 修改 `application.yml`

在 IDEA 中打开：

```text
src/main/resources/application.yml
```

确认内容如下：

```yaml
server:
  port: 8123

spring:
  application:
    name: ai-article-studio

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:13306/ai_article_studio?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: 123456

  data:
    redis:
      host: localhost
      port: 16379

mybatis:
  mapper-locations: classpath*:mapper/*.xml
  type-aliases-package: com.nana.aiarticlestudio.model.entity
  configuration:
    map-underscore-to-camel-case: true
```

新增重点：

```yaml
configuration:
  map-underscore-to-camel-case: true
```

作用：

```text
让 MyBatis 自动把数据库字段 task_id 映射到 Java 字段 taskId。
例如：
数据库字段：task_id
Java 字段：taskId
```

---

## 2. 创建统一返回类 `BaseResponse`

在 `common` 包下新建：

```text
BaseResponse.java
```

代码：

```java
package com.nana.aiarticlestudio.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {

    private int code;

    private String message;

    private T data;

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, "ok", data);
    }

    public static <T> BaseResponse<T> fail(String message) {
        return new BaseResponse<>(500, message, null);
    }
}
```

作用：

```text
统一后端接口返回格式。
以后前端收到的数据都会长这样：
{
  "code": 0,
  "message": "ok",
  "data": ...
}
```

字段解释：

| 字段 | 含义 |
|---|---|
| `code` | 状态码，0 表示成功，500 表示失败 |
| `message` | 返回信息 |
| `data` | 真正返回的数据 |

---

## 3. 创建分页结果类 `PageResult`

在 `common` 包下新建：

```text
PageResult.java
```

代码：

```java
package com.nana.aiarticlestudio.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;

    private int pageNo;

    private int pageSize;

    private List<T> records;
}
```

作用：

```text
用于文章列表分页返回。
```

字段解释：

| 字段 | 含义 |
|---|---|
| `total` | 总条数 |
| `pageNo` | 当前页码 |
| `pageSize` | 每页条数 |
| `records` | 当前页数据 |

---

## 4. 创建文章实体类 `Article`

在 `model.entity` 包下新建：

```text
Article.java
```

代码：

```java
package com.nana.aiarticlestudio.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Article {

    private Long id;

    private String taskId;

    private String topic;

    private String style;

    private String phase;

    private String status;

    private String titleOptions;

    private String selectedTitle;

    private String outline;

    private String content;

    private String fullContent;

    private String errorMessage;

    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

作用：

```text
Article 类对应数据库里的 article 表。
数据库里一行文章任务，在 Java 里就是一个 Article 对象。
```

---

## 5. 创建请求 DTO

DTO 是 `Data Transfer Object`，用于接收前端传给后端的参数。

### 5.1 创建文章请求

在 `model.dto` 包下新建：

```text
ArticleCreateRequest.java
```

代码：

```java
package com.nana.aiarticlestudio.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArticleCreateRequest {

    @NotBlank(message = "选题不能为空")
    private String topic;

    private String style;
}
```

作用：

```text
接收前端创建文章任务时传来的参数。
topic 是用户输入的选题。
style 是文章风格，暂时可以传 TECH。
```

`@NotBlank` 的作用：

```text
要求 topic 不能为空。
如果前端没传 topic，后端会返回“选题不能为空”。
```

### 5.2 查询列表请求

在 `model.dto` 包下新建：

```text
ArticleListRequest.java
```

代码：

```java
package com.nana.aiarticlestudio.model.dto;

import lombok.Data;

@Data
public class ArticleListRequest {

    private int pageNo = 1;

    private int pageSize = 10;
}
```

作用：

```text
接收前端查询文章列表时传来的分页参数。
```

---

## 6. 创建返回 VO

VO 是 `View Object`，用于返回给前端展示。

在 `model.vo` 包下新建：

```text
ArticleVO.java
```

代码：

```java
package com.nana.aiarticlestudio.model.vo;

import com.nana.aiarticlestudio.model.entity.Article;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class ArticleVO {

    private Long id;

    private String taskId;

    private String topic;

    private String style;

    private String phase;

    private String status;

    private String titleOptions;

    private String selectedTitle;

    private String outline;

    private String content;

    private String fullContent;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static ArticleVO fromEntity(Article article) {
        if (article == null) {
            return null;
        }
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        return articleVO;
    }
}
```

作用：

```text
ArticleVO 是返回给前端看的对象。
它隐藏了 isDeleted 这类不需要给前端看的字段。
```

---

## 7. 创建 `ArticleMapper`

在 `mapper` 包下新建：

```text
ArticleMapper.java
```

代码：

```java
package com.nana.aiarticlestudio.mapper;

import com.nana.aiarticlestudio.model.entity.Article;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleMapper {

    @Insert("""
            INSERT INTO article (task_id, topic, style, phase, status)
            VALUES (#{taskId}, #{topic}, #{style}, #{phase}, #{status})
            """)
    int insert(Article article);

    @Select("""
            SELECT *
            FROM article
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            LIMIT 1
            """)
    Article selectByTaskId(String taskId);

    @Select("""
            SELECT *
            FROM article
            WHERE is_deleted = 0
            ORDER BY create_time DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<Article> list(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("""
            SELECT COUNT(*)
            FROM article
            WHERE is_deleted = 0
            """)
    long count();

    @Update("""
            UPDATE article
            SET is_deleted = 1
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int deleteByTaskId(String taskId);
}
```

作用：

```text
Mapper 是数据库访问层。
它负责执行 SQL，直接操作 article 表。
```

软删除说明：

```text
deleteByTaskId 不是直接删除数据库记录，而是把 is_deleted 改成 1。
这样方便以后恢复、审计和排查问题。
```

---

## 8. 创建 `ArticleService`

在 `service` 包下新建：

```text
ArticleService.java
```

代码：

```java
package com.nana.aiarticlestudio.service;

import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.vo.ArticleVO;

public interface ArticleService {

    String createArticle(ArticleCreateRequest request);

    ArticleVO getByTaskId(String taskId);

    PageResult<ArticleVO> listArticles(ArticleListRequest request);

    Boolean deleteByTaskId(String taskId);
}
```

作用：

```text
Service 是业务逻辑层。
Controller 不直接操作数据库，而是调用 Service。
```

---

## 9. 创建 `ArticleServiceImpl`

在 `service.impl` 包下新建：

```text
ArticleServiceImpl.java
```

代码：

```java
package com.nana.aiarticlestudio.service.impl;

import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.mapper.ArticleMapper;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.entity.Article;
import com.nana.aiarticlestudio.model.vo.ArticleVO;
import com.nana.aiarticlestudio.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;

    @Override
    public String createArticle(ArticleCreateRequest request) {
        Article article = new Article();

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String style = StringUtils.hasText(request.getStyle()) ? request.getStyle() : "TECH";

        article.setTaskId(taskId);
        article.setTopic(request.getTopic());
        article.setStyle(style);
        article.setPhase("CREATED");
        article.setStatus("INIT");

        int result = articleMapper.insert(article);
        if (result != 1) {
            throw new RuntimeException("创建文章任务失败");
        }

        return taskId;
    }

    @Override
    public ArticleVO getByTaskId(String taskId) {
        Article article = articleMapper.selectByTaskId(taskId);
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }
        return ArticleVO.fromEntity(article);
    }

    @Override
    public PageResult<ArticleVO> listArticles(ArticleListRequest request) {
        int pageNo = request.getPageNo() <= 0 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() <= 0 ? 10 : request.getPageSize();

        if (pageSize > 50) {
            pageSize = 50;
        }

        int offset = (pageNo - 1) * pageSize;

        List<ArticleVO> records = articleMapper.list(offset, pageSize)
                .stream()
                .map(ArticleVO::fromEntity)
                .toList();

        long total = articleMapper.count();

        return new PageResult<>(total, pageNo, pageSize, records);
    }

    @Override
    public Boolean deleteByTaskId(String taskId) {
        int result = articleMapper.deleteByTaskId(taskId);
        return result > 0;
    }
}
```

核心逻辑解释：

```text
UUID：生成唯一 taskId。
style：如果前端没有传，就默认 TECH。
phase = CREATED：表示任务刚创建。
status = INIT：表示任务还没有开始执行。
offset：分页查询时从第几条开始查。
```

---

## 10. 创建全局异常处理

在 `exception` 包下新建：

```text
GlobalExceptionHandler.java
```

代码：

```java
package com.nana.aiarticlestudio.exception;

import com.nana.aiarticlestudio.common.BaseResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return BaseResponse.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<?> handleException(Exception e) {
        return BaseResponse.fail(e.getMessage());
    }
}
```

作用：

```text
统一处理后端异常。
比如选题为空时，不让系统返回一大段报错，而是返回清晰的 JSON。
```

---

## 11. 创建 `ArticleController`

在 `controller` 包下新建：

```text
ArticleController.java
```

代码：

```java
package com.nana.aiarticlestudio.controller;

import com.nana.aiarticlestudio.common.BaseResponse;
import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.vo.ArticleVO;
import com.nana.aiarticlestudio.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping("/create")
    public BaseResponse<String> createArticle(@Valid @RequestBody ArticleCreateRequest request) {
        return BaseResponse.success(articleService.createArticle(request));
    }

    @GetMapping("/{taskId}")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId) {
        return BaseResponse.success(articleService.getByTaskId(taskId));
    }

    @PostMapping("/list")
    public BaseResponse<PageResult<ArticleVO>> listArticles(@RequestBody ArticleListRequest request) {
        return BaseResponse.success(articleService.listArticles(request));
    }

    @PostMapping("/delete/{taskId}")
    public BaseResponse<Boolean> deleteArticle(@PathVariable String taskId) {
        return BaseResponse.success(articleService.deleteByTaskId(taskId));
    }
}
```

作用：

```text
Controller 是接口层。
前端请求 /api/article/create、/api/article/list 等接口时，先进入 Controller。
Controller 再调用 Service 处理业务。
```

---

## 12. 重启后端

在 IDEA 底部 `Run` 窗口点击红色方块停止项目，然后重新运行：

```text
AiArticleStudioApplication
```

看到下面两行说明启动成功：

```text
Tomcat started on port 8123
Started AiArticleStudioApplication
```

---

## 13. 用 PowerShell 测试接口

打开新的 PowerShell。

### 13.1 创建文章任务

```powershell
$createRes = Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/create" -ContentType "application/json" -Body '{"topic":"AI产品经理如何提升效率","style":"TECH"}'
$createRes
```

含义：

```text
Invoke-RestMethod：发送 HTTP 请求。
-Method Post：使用 POST 请求。
-Uri：请求后端接口地址。
-ContentType "application/json"：告诉后端请求体是 JSON。
-Body：传给后端的 JSON 数据。
$createRes：把接口返回结果保存到变量里。
```

### 13.2 保存 taskId

```powershell
$taskId = $createRes.data
$taskId
```

含义：

```text
把刚才创建文章任务返回的 taskId 保存到 $taskId 变量中。
后面查详情和删除都会用它。
```

### 13.3 查询文章详情

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8123/api/article/$taskId"
```

含义：

```text
根据 taskId 查询刚才创建的文章任务详情。
```

### 13.4 查询文章列表

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/list" -ContentType "application/json" -Body '{"pageNo":1,"pageSize":10}'
```

含义：

```text
查询文章任务列表。
pageNo 表示第几页。
pageSize 表示每页多少条。
```

### 13.5 删除文章任务

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8123/api/article/delete/$taskId"
```

含义：

```text
根据 taskId 软删除文章任务。
软删除不是物理删除，只是把 is_deleted 改成 1。
```

注意：如果后面还要用这条测试数据，可以先不要删除。

---

## 14. 后端验收标准

后端做到这里就可以：

```text
1. 后端能正常启动
2. 创建文章接口返回 taskId
3. 查询详情接口能看到刚才创建的文章
4. 查询列表接口能看到文章列表
5. IDEA 里不再出现 No MyBatis mapper was found 的警告
```

---

# 第二部分：前端页面开发

---

## 15. 前端目标

前端要完成：

```text
1. 创建文章任务
2. 查看文章任务列表
3. 点击进入文章详情
4. 从详情页返回列表
```

当前页面更像：

```text
文章任务管理页 / 开发调试页
```

不是最终用户使用的完整 AI 创作界面。

---

## 16. 确认前端依赖已安装

进入前端目录：

```powershell
cd D:\Desktop\projectsi-article-studiorontend
```

执行：

```powershell
npm install axios vue-router pinia ant-design-vue
```

含义：

```text
axios：请求后端接口。
vue-router：页面路由跳转。
pinia：状态管理，后面会用。
ant-design-vue：UI 组件库。
```

---

## 17. 修改 `main.ts`

打开：

```text
frontend/src/main.ts
```

替换为：

```ts
import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(Antd)
app.use(router)

app.mount('#app')
```

作用：

```text
引入 Ant Design Vue 组件库。
引入 vue-router 路由。
把 Vue 应用挂载到页面上。
```

---

## 18. 创建路由配置

在 `src` 下创建：

```text
src/router/index.ts
```

写入：

```ts
import { createRouter, createWebHistory } from 'vue-router'
import ArticleList from '../pages/ArticleList.vue'
import ArticleDetail from '../pages/ArticleDetail.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/article/list',
    },
    {
      path: '/article/list',
      component: ArticleList,
    },
    {
      path: '/article/detail/:taskId',
      component: ArticleDetail,
    },
  ],
})

export default router
```

作用：

```text
/：默认跳转到文章列表页。
/article/list：文章任务列表页。
/article/detail/:taskId：文章详情页，taskId 是动态参数。
```

---

## 19. 创建请求工具 `request.ts`

在 `src/api` 下创建：

```text
src/api/request.ts
```

写入：

```ts
import axios from 'axios'

const request = axios.create({
  baseURL: 'http://localhost:8123',
  timeout: 30000,
})

export default request
```

作用：

```text
统一设置后端地址。
以后所有接口都会请求 http://localhost:8123。
```

---

## 20. 创建文章接口文件 `article.ts`

在 `src/api` 下创建：

```text
src/api/article.ts
```

写入：

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

export interface ArticleCreateRequest {
  topic: string
  style?: string
}

export interface ArticleListRequest {
  pageNo: number
  pageSize: number
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
```

作用：

```text
把所有文章相关接口集中管理。
页面不直接写 axios，而是调用 createArticle、listArticles、getArticle、deleteArticle。
```

---

## 21. 创建文章列表页 `ArticleList.vue`

在 `src/pages` 下创建：

```text
src/pages/ArticleList.vue
```

写入：

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  createArticle,
  deleteArticle,
  listArticles,
  type ArticleVO,
} from '../api/article'

const router = useRouter()

const topic = ref('')
const style = ref('TECH')
const loading = ref(false)
const articles = ref<ArticleVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

const loadArticles = async () => {
  loading.value = true
  try {
    const res = await listArticles({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    articles.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error(error)
    message.error('加载文章列表失败')
  } finally {
    loading.value = false
  }
}

const handleCreate = async () => {
  if (!topic.value.trim()) {
    message.warning('请输入文章选题')
    return
  }

  loading.value = true
  try {
    const res = await createArticle({
      topic: topic.value,
      style: style.value,
    })

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    message.success('创建成功')
    topic.value = ''
    await loadArticles()
  } catch (error) {
    console.error(error)
    message.error('创建文章任务失败')
  } finally {
    loading.value = false
  }
}

const handleView = (taskId: string) => {
  router.push(`/article/detail/${taskId}`)
}

const handleDelete = async (taskId: string) => {
  loading.value = true
  try {
    const res = await deleteArticle(taskId)

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    message.success('删除成功')
    await loadArticles()
  } catch (error) {
    console.error(error)
    message.error('删除失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadArticles()
})
</script>

<template>
  <div class="page">
    <h1>AI Article Studio</h1>
    <p class="subtitle">文章任务列表</p>

    <a-card title="创建文章任务" class="card">
      <div class="form">
        <a-textarea
          v-model:value="topic"
          placeholder="请输入文章选题，例如：AI产品经理如何提升效率"
          :rows="3"
        />

        <a-select v-model:value="style" style="width: 180px">
          <a-select-option value="TECH">技术风格</a-select-option>
          <a-select-option value="BUSINESS">商业风格</a-select-option>
          <a-select-option value="LIFE">生活风格</a-select-option>
        </a-select>

        <a-button type="primary" :loading="loading" @click="handleCreate">
          创建任务
        </a-button>
      </div>
    </a-card>

    <a-card title="文章任务列表" class="card">
      <a-table
        :data-source="articles"
        :loading="loading"
        :pagination="false"
        row-key="taskId"
      >
        <a-table-column title="选题" data-index="topic" />
        <a-table-column title="风格" data-index="style" />
        <a-table-column title="阶段" data-index="phase" />
        <a-table-column title="状态" data-index="status" />
        <a-table-column title="创建时间" data-index="createTime" />

        <a-table-column title="操作">
          <template #default="{ record }">
            <a-space>
              <a-button type="link" @click="handleView(record.taskId)">
                查看详情
              </a-button>
              <a-button type="link" danger @click="handleDelete(record.taskId)">
                删除
              </a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>

      <div class="total">
        共 {{ total }} 条任务
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.page {
  padding: 32px;
  background: #f5f5f5;
  min-height: 100vh;
}

.subtitle {
  color: #666;
  margin-bottom: 24px;
}

.card {
  margin-bottom: 24px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.total {
  margin-top: 16px;
  color: #666;
}
</style>
```

这个页面做了：

```text
1. 页面加载时自动请求文章列表
2. 用户输入选题后可以创建文章任务
3. 创建成功后自动刷新列表
4. 点击查看详情可以跳转到详情页
5. 点击删除可以软删除任务
```

---

## 22. 创建文章详情页 `ArticleDetail.vue`

在 `src/pages` 下创建：

```text
src/pages/ArticleDetail.vue
```

写入：

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticle, type ArticleVO } from '../api/article'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const article = ref<ArticleVO | null>(null)

const loadArticle = async () => {
  const taskId = route.params.taskId as string

  if (!taskId) {
    message.error('缺少 taskId')
    return
  }

  loading.value = true
  try {
    const res = await getArticle(taskId)

    if (res.code !== 0) {
      message.error(res.message)
      return
    }

    article.value = res.data
  } catch (error) {
    console.error(error)
    message.error('加载文章详情失败')
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
    <a-button @click="goBack">返回列表</a-button>

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

        <a-descriptions-item label="标题候选">
          {{ article.titleOptions || '暂未生成' }}
        </a-descriptions-item>

        <a-descriptions-item label="已选标题">
          {{ article.selectedTitle || '暂未选择' }}
        </a-descriptions-item>

        <a-descriptions-item label="大纲">
          {{ article.outline || '暂未生成' }}
        </a-descriptions-item>

        <a-descriptions-item label="正文">
          {{ article.content || '暂未生成' }}
        </a-descriptions-item>

        <a-descriptions-item label="完整图文">
          {{ article.fullContent || '暂未生成' }}
        </a-descriptions-item>

        <a-descriptions-item label="创建时间">
          {{ article.createTime }}
        </a-descriptions-item>

        <a-descriptions-item label="更新时间">
          {{ article.updateTime }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-empty v-else-if="!loading" description="文章任务不存在" />
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
</style>
```

---

## 23. 修改 `App.vue`

打开：

```text
src/App.vue
```

全部替换为：

```vue
<template>
  <router-view />
</template>
```

作用：

```text
App.vue 只作为路由出口。
真正展示哪个页面，由 vue-router 决定。
```

---

## 24. 启动前端

在前端目录执行：

```powershell
npm run dev
```

含义：

```text
启动 Vite 前端开发服务器。
```

浏览器打开：

```text
http://localhost:5173
```

它应该自动跳转到：

```text
http://localhost:5173/article/list
```

---

## 25. 前端测试流程

### 25.1 打开列表页

```text
http://localhost:5173/article/list
```

应该能看到：

```text
AI Article Studio
文章任务列表
创建文章任务
文章任务列表
```

### 25.2 创建文章任务

输入：

```text
AI产品经理如何提升效率
```

选择：

```text
技术风格
```

点击：

```text
创建任务
```

应该提示：

```text
创建成功
```

然后列表里出现一条数据。

### 25.3 查看详情

点击：

```text
查看详情
```

应该进入：

```text
/article/detail/某个taskId
```

详情页应该显示：

```text
任务 ID
选题
风格
阶段 CREATED
状态 INIT
```

### 25.4 返回列表

点击：

```text
返回列表
```

应该回到：

```text
/article/list
```

---

## 26. 常见问题

### 26.1 找不到 `main.ts`

如果 `frontend` 里没有 `src/main.ts`，说明前端项目没创建成功，或打开的是旧的空目录。

处理方式：

```powershell
cd D:\Desktop\projectsi-article-studio
Remove-Item -Recurse -Force frontend
npm create vite@latest frontend -- --template vue-ts
cd frontend
npm install
```

含义：

```text
Remove-Item -Recurse -Force frontend：
删除旧的 frontend 文件夹及其所有内容。

npm create vite@latest frontend -- --template vue-ts：
重新创建 Vue 3 + TypeScript 前端项目。

npm install：
安装前端项目依赖。
```

### 26.2 PowerShell 删除目录报错

如果：

```powershell
rmdir /s frontend
```

报错，是因为 PowerShell 不认 CMD 的 `/s` 写法。

PowerShell 正确写法：

```powershell
Remove-Item -Recurse -Force frontend
```

含义：

```text
删除 frontend 文件夹及其中所有内容。
```

也可以：

```powershell
cmd /c rmdir /s /q frontend
```

含义：

```text
让 PowerShell 调用 CMD 来删除 frontend 文件夹。
/s 表示删除子目录。
/q 表示不再询问确认。
```

### 26.3 `Failed to resolve import`

常见报错：

```text
Failed to resolve import "ant-design-vue"
Failed to resolve import "vue-router"
Failed to resolve import "axios"
```

原因：

```text
前端依赖没装完整，或装完后没有重启前端服务。
```

处理：

```text
1. 在前端运行窗口按 Ctrl + C 停止 Vite
2. 进入 frontend 目录
3. 执行 npm install axios vue-router pinia ant-design-vue
4. 确认文件都存在
5. 执行 npm run dev
```

需要确认存在的文件：

```text
src/router/index.ts
src/api/request.ts
src/api/article.ts
src/pages/ArticleList.vue
src/pages/ArticleDetail.vue
src/App.vue
```

### 26.4 找不到 `ArticleList.vue` 或 `ArticleDetail.vue`

检查路径必须是：

```text
src/pages/ArticleList.vue
src/pages/ArticleDetail.vue
```

注意大小写要一致。

### 26.5 Vue 文件为空

报错：

```text
At least one <template> or <script> is required in a single file component.
```

原因：

```text
Vue 文件是空文件，或内容没有保存成功。
```

解决：

```text
重新复制对应 Vue 文件代码，并保存。
```

### 26.6 中文显示成问号

如果只有表格里的选题变成问号，但按钮、标题、placeholder 中文正常，通常是 PowerShell 测接口时的编码问题。

处理：

```text
1. 在页面删除乱码数据
2. 用前端页面重新输入中文创建任务
```

如果前端创建仍乱码，再进入 MySQL 修改字符集：

```powershell
docker exec -it ai_article_mysql mysql -uroot -p123456
```

含义：

```text
进入 MySQL 容器并登录数据库。
```

然后执行：

```sql
ALTER DATABASE ai_article_studio CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE article CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE agent_log CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

作用：

```text
把数据库和两张表统一改成支持中文、emoji 的 utf8mb4 编码。
```

---

## 27. Day 2 最终验收标准

```text
1. 后端接口测试成功
2. 前端可以进入 http://localhost:5173/article/list
3. 页面能创建文章任务
4. 创建成功后列表自动刷新
5. 点击查看详情可以进入详情页
6. 详情页能显示 taskId、topic、style、phase、status
7. 点击返回列表可以回到列表页
8. 中文选题可以正常保存和展示
```

---

## 28. Day 2 完成后的项目状态

此时项目已经具备：

```text
1. Spring Boot 后端基础工程
2. MySQL 数据库
3. Redis 容器
4. Article 文章任务表
5. Agent 日志表
6. 文章任务 CRUD 接口
7. Vue 前端基础页面
8. 前后端联调能力
```

但是现在还没有 AI。

当前页面只是：

```text
文章任务管理页 / 开发调试页
```

它会为 Day 3 的 AI 生成流程做入口。

![image-20260603150323609](/Users/stephanie/Library/Application Support/typora-user-images/image-20260603150323609.png)

---

## 29. Day 3 预告

Day 3 开始接入 AI，重点是：

```text
1. 设计 Agent 接口
2. 实现标题生成 Agent
3. 实现大纲生成 Agent
4. 用户输入选题后，AI 生成标题候选
5. 用户选择标题后，AI 生成大纲
```

Day 3 后，项目会从普通 CRUD 项目，开始变成真正的 AI 应用。
