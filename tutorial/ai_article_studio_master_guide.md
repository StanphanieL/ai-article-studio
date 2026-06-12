# AI Article Studio 总教程：技术路线、项目结构与文件说明

> 适合对象：刚开始学习前后端项目、Spring Boot、Vue、Docker、Agentic AI 应用开发的初学者。  
> 当前项目状态：已完成 Day 1 到 Day 5。项目已经具备数据库、后端接口、前端页面、Mock AI Agent、Markdown 正文生成、SSE 流式输出能力。  
> 项目定位：一个用于学习 AI 应用开发流程的「多 Agent 图文创作平台」雏形。

---

# 0. 项目一句话介绍

`AI Article Studio` 是一个简化版 AI 文章创作系统。

用户可以：

```text
输入文章选题
↓
创建文章任务
↓
生成标题候选
↓
选择标题
↓
生成文章大纲
↓
生成文章正文
↓
通过 SSE 实时查看生成过程
↓
最终把正文保存到数据库
```

当前阶段还没有接真实大模型 API，而是使用：

```text
MockLlmClient
```

模拟大模型返回，先把产品流程、后端分层、前端交互、数据库保存、SSE 流式通信全部跑通。

---

# 1. 当前技术路线总览

## 1.1 总体技术栈

| 模块 | 技术 | 作用 |
|---|---|---|
| 前端 | Vue 3 | 构建前端页面 |
| 前端构建工具 | Vite | 快速启动和构建 Vue 项目 |
| 前端语言 | TypeScript | 给 JavaScript 加类型，提高可维护性 |
| 前端 UI | Ant Design Vue | 快速搭建表单、表格、按钮、卡片、时间线 |
| 前端请求 | Axios | 调用后端 HTTP 接口 |
| 前端路由 | Vue Router | 管理列表页、详情页等页面跳转 |
| Markdown 渲染 | markdown-it | 把 Markdown 正文渲染成 HTML |
| 后端 | Spring Boot 3.x | Java 后端框架 |
| 后端语言 | Java 21 | 后端开发语言 |
| 数据库访问 | MyBatis | 执行 SQL，操作 MySQL |
| 数据库 | MySQL 8.0 | 保存文章任务、标题、大纲、正文 |
| 缓存 / 后续扩展 | Redis 7 | 当前先搭好，后续可做任务状态缓存 |
| 容器 | Docker Desktop | 用容器启动 MySQL 和 Redis |
| 流式通信 | SSE / SseEmitter | 后端实时向前端推送生成进度 |
| AI 抽象 | LlmClient | 统一大模型调用入口 |
| Mock AI | MockLlmClient | 模拟大模型返回，避免一开始就被 API Key 卡住 |

---

## 1.2 为什么这样选技术？

### 为什么后端用 Spring Boot？

Spring Boot 适合做 Java Web 后端，主要负责：

```text
1. 提供 HTTP 接口
2. 连接 MySQL
3. 连接 Redis
4. 管理业务逻辑
5. 管理配置文件
6. 提供 SSE 流式接口
```

你可以把 Spring Boot 理解成：

```text
前端和数据库之间的中间层。
```

前端不会直接操作数据库，而是：

```text
前端页面
↓
调用 Spring Boot 接口
↓
Spring Boot 执行业务逻辑
↓
MyBatis 操作 MySQL
↓
返回结果给前端
```

---

### 为什么前端用 Vue 3？

Vue 3 适合快速搭建交互页面。

在本项目中，Vue 主要负责：

```text
1. 显示文章任务列表
2. 提供创建任务输入框
3. 展示文章详情
4. 展示标题候选
5. 支持用户选择标题
6. 展示文章大纲
7. 展示 Markdown 正文
8. 接收 SSE 流式消息
```

---

### 为什么使用 Docker？

Docker 用来启动 MySQL 和 Redis。

好处是：

```text
1. 不用在 Windows 上手动安装 MySQL
2. 不用担心本机环境污染
3. 项目环境更容易复现
4. 删除容器也不影响代码
5. 后续部署更接近真实生产环境
```

本项目中 Docker 目前启动两个容器：

```text
ai_article_mysql
ai_article_redis
```

---

### 为什么先用 MockLlmClient？

因为真实大模型接入会引入很多额外复杂度：

```text
1. API Key
2. 网络问题
3. 模型供应商选择
4. 模型返回格式不稳定
5. Prompt 调试
6. Token 流式输出
```

所以前五天先用 Mock：

```text
MockLlmClient
```

模拟大模型结果，先验证：

```text
1. Agent 分层是否合理
2. 前后端链路是否打通
3. 数据库是否能保存结果
4. 前端能否展示标题、大纲、正文
5. SSE 能否实时推送进度
```

后续 Day 6 再替换成真实大模型即可。

---

# 2. 项目整体目录结构

当前项目根目录：

```text
ai-article-studio
├── backend
├── frontend
└── deploy
```

含义：

| 目录 | 作用 |
|---|---|
| `backend` | Spring Boot 后端项目 |
| `frontend` | Vue 3 前端项目 |
| `deploy` | Docker Compose 等部署配置 |

---

## 2.1 deploy 目录

```text
deploy
└── docker-compose.yml
```

### `docker-compose.yml`

作用：

```text
启动 MySQL 和 Redis 容器。
```

当前内容大致是：

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: ai_article_mysql
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: ai_article_studio
      TZ: Asia/Shanghai
    ports:
      - "13306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command:
      --default-authentication-plugin=mysql_native_password
      --character-set-server=utf8mb4
      --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:7
    container_name: ai_article_redis
    ports:
      - "16379:6379"

volumes:
  mysql_data:
```

关键配置解释：

| 配置 | 含义 |
|---|---|
| `mysql:8.0` | 使用 MySQL 8.0 镜像 |
| `redis:7` | 使用 Redis 7 镜像 |
| `container_name` | 容器名称 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_DATABASE` | 容器启动时创建的数据库 |
| `13306:3306` | 本机 13306 端口映射到容器 MySQL 3306 |
| `16379:6379` | 本机 16379 端口映射到容器 Redis 6379 |
| `mysql_data` | MySQL 数据持久化卷 |
| `utf8mb4` | 支持中文和 emoji |

常用命令：

```powershell
docker compose up -d
```

含义：

```text
根据 docker-compose.yml 启动 MySQL 和 Redis，并在后台运行。
```

```powershell
docker ps
```

含义：

```text
查看当前正在运行的 Docker 容器。
```

```powershell
docker exec -it ai_article_mysql mysql -uroot -p123456
```

含义：

```text
进入 MySQL 容器，并用 root 用户登录 MySQL。
```

---

# 3. 数据库设计

当前数据库：

```text
ai_article_studio
```

当前主要表：

```text
article
agent_log
```

---

## 3.1 article 表

`article` 表是当前项目最核心的表。

它保存一条文章创作任务的完整状态。

建表 SQL：

```sql
CREATE TABLE IF NOT EXISTS article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    topic VARCHAR(512) NOT NULL COMMENT '用户输入的选题',
    style VARCHAR(64) DEFAULT 'TECH' COMMENT '文章风格',
    phase VARCHAR(64) NOT NULL DEFAULT 'CREATED' COMMENT '当前阶段',
    status VARCHAR(64) NOT NULL DEFAULT 'INIT' COMMENT '任务状态',

    title_options JSON NULL COMMENT '标题候选列表',
    selected_title VARCHAR(512) NULL COMMENT '用户选择的标题',
    outline JSON NULL COMMENT '文章大纲',
    content MEDIUMTEXT NULL COMMENT '正文内容',
    full_content MEDIUMTEXT NULL COMMENT '图文合成后的完整内容',

    error_message TEXT NULL COMMENT '错误信息',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_create_time (create_time)
) COMMENT '文章任务表';
```

字段解释：

| 字段 | 含义 |
|---|---|
| `id` | 数据库自增主键 |
| `task_id` | 业务任务 ID，前端详情页使用它 |
| `topic` | 用户输入的文章选题 |
| `style` | 文章风格，比如 TECH、BUSINESS、LIFE |
| `phase` | 当前业务阶段 |
| `status` | 当前执行状态 |
| `title_options` | AI 生成的标题候选 JSON |
| `selected_title` | 用户选择的标题 |
| `outline` | AI 生成的大纲 JSON |
| `content` | AI 生成的 Markdown 正文 |
| `full_content` | 后续图文合成后的完整内容 |
| `error_message` | 错误信息 |
| `is_deleted` | 是否软删除 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

---

## 3.2 phase 和 status 的区别

### phase：业务阶段

`phase` 表示文章任务走到了哪个创作阶段。

对应枚举：

```java
public enum ArticlePhase {

    CREATED,

    TITLE_SELECTION,

    OUTLINE_EDITING,

    CONTENT_GENERATION,

    COMPLETED,

    FAILED
}
```

阶段解释：

| phase | 含义 |
|---|---|
| `CREATED` | 任务刚创建 |
| `TITLE_SELECTION` | 标题候选已生成，等待用户选标题 |
| `OUTLINE_EDITING` | 大纲已生成，等待用户确认或编辑 |
| `CONTENT_GENERATION` | 正文生成阶段 |
| `COMPLETED` | 全流程完成 |
| `FAILED` | 任务失败 |

---

### status：执行状态

`status` 表示当前任务执行结果。

对应枚举：

```java
public enum ArticleStatus {

    INIT,

    RUNNING,

    SUCCESS,

    FAILED
}
```

状态解释：

| status | 含义 |
|---|---|
| `INIT` | 初始化 |
| `RUNNING` | 正在执行 |
| `SUCCESS` | 执行成功 |
| `FAILED` | 执行失败 |

简单理解：

```text
phase 说明“走到哪一步”。
status 说明“这一步执行得怎么样”。
```

---

## 3.3 agent_log 表

建表 SQL：

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

当前项目还没有真正写入 `agent_log`，但它的设计作用是：

```text
后续记录每个 Agent 的输入、输出、耗时、状态和错误信息。
```

例如后续可以记录：

```text
TitleGeneratorAgent 输入了什么，输出了什么。
OutlineGeneratorAgent 输入了什么，输出了什么。
ContentGeneratorAgent 生成正文用了多久。
```

这对真实 AI 应用很重要，因为可以用于：

```text
1. 调试 Prompt
2. 追踪模型错误
3. 统计生成耗时
4. 观察不同 Agent 效果
5. 后续做任务回放
```

---

# 4. 后端项目结构总览

后端目录：

```text
backend
├── pom.xml
└── src
    └── main
        ├── java
        │   └── com
        │       └── nana
        │           └── aiarticlestudio
        │               ├── AiArticleStudioApplication.java
        │               ├── agent
        │               ├── common
        │               ├── config
        │               ├── controller
        │               ├── exception
        │               ├── mapper
        │               ├── model
        │               │   ├── dto
        │               │   ├── entity
        │               │   ├── enums
        │               │   └── vo
        │               └── service
        │                   └── impl
        └── resources
            └── application.yml
```

---

# 5. 后端根文件说明

## 5.1 `AiArticleStudioApplication.java`

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/AiArticleStudioApplication.java
```

作用：

```text
Spring Boot 项目的启动入口。
```

典型代码：

```java
@SpringBootApplication
public class AiArticleStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiArticleStudioApplication.class, args);
    }
}
```

你在 IDEA 中点击绿色三角形运行的就是这个类。

启动成功后，后端服务运行在：

```text
http://localhost:8123
```

---

## 5.2 `application.yml`

路径：

```text
backend/src/main/resources/application.yml
```

作用：

```text
后端项目配置文件。
```

当前主要配置：

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

关键配置解释：

| 配置 | 作用 |
|---|---|
| `server.port: 8123` | 后端服务运行端口 |
| `spring.datasource` | MySQL 连接配置 |
| `localhost:13306` | 连接 Docker 暴露出来的 MySQL |
| `spring.data.redis` | Redis 连接配置 |
| `mapper-locations` | MyBatis XML Mapper 位置 |
| `type-aliases-package` | 实体类包路径 |
| `map-underscore-to-camel-case` | 数据库下划线字段自动映射 Java 驼峰字段 |

`map-underscore-to-camel-case` 很重要。

例如数据库字段：

```text
task_id
selected_title
create_time
```

可以自动映射为 Java 字段：

```text
taskId
selectedTitle
createTime
```

---

## 5.3 `pom.xml`

路径：

```text
backend/pom.xml
```

作用：

```text
Maven 项目的依赖管理文件。
```

它负责管理：

```text
Spring Web
MyBatis
MySQL Driver
Redis
Lombok
Validation
Jackson
```

重要编码配置：

```xml
<properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <maven.compiler.encoding>UTF-8</maven.compiler.encoding>
</properties>
```

这些配置的作用：

```text
确保 Java 源码编译时使用 UTF-8。
如果缺少，MockLlmClient 里的中文可能出现 äº§å... 这类乱码。
```

注意：

```xml
<?xml version="1.0" encoding="UTF-8"?>
```

只表示 `pom.xml` 文件本身是 UTF-8，不等于 Java 源码编译一定使用 UTF-8。  
所以仍然建议保留 `<project.build.sourceEncoding>` 等配置。

---

# 6. 后端 common 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/common
```

作用：

```text
存放通用类。
例如统一返回结果、分页结果等。
```

---

## 6.1 `BaseResponse.java`

作用：

```text
统一后端接口返回格式。
```

代码结构：

```java
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

前端收到的数据统一长这样：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

字段解释：

| 字段 | 含义 |
|---|---|
| `code` | 0 表示成功，500 表示失败 |
| `message` | 提示信息 |
| `data` | 真正的数据 |

为什么要统一返回格式？

```text
前端判断接口是否成功会更简单。
不用每个接口都处理不同格式。
```

---

## 6.2 `PageResult.java`

作用：

```text
统一分页返回格式。
```

代码结构：

```java
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

字段解释：

| 字段 | 含义 |
|---|---|
| `total` | 总数据条数 |
| `pageNo` | 当前页码 |
| `pageSize` | 每页条数 |
| `records` | 当前页数据 |

文章列表接口返回时使用它：

```text
BaseResponse<PageResult<ArticleVO>>
```

---

# 7. 后端 config 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/config
```

作用：

```text
存放配置类。
```

---

## 7.1 `CorsConfig.java`

作用：

```text
解决前后端跨域问题。
```

前端运行在：

```text
http://localhost:5173
```

后端运行在：

```text
http://localhost:8123
```

端口不同，浏览器认为它们是不同源。  
如果不配置跨域，前端请求后端会被浏览器拦截。

代码：

```java
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
```

配置解释：

| 配置 | 含义 |
|---|---|
| `/api/**` | 只允许 API 接口跨域 |
| `allowedOrigins("http://localhost:5173")` | 允许前端开发服务器访问 |
| `allowedMethods("*")` | 允许所有请求方法 |
| `allowedHeaders("*")` | 允许所有请求头 |
| `allowCredentials(true)` | 允许携带凭证 |

---

# 8. 后端 exception 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/exception
```

作用：

```text
统一处理后端异常。
```

---

## 8.1 `GlobalExceptionHandler.java`

作用：

```text
把后端异常转成统一 JSON 返回给前端。
```

代码结构：

```java
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

如果没有全局异常处理，后端报错时前端可能收到很长的异常堆栈。  
有了它，前端会收到：

```json
{
  "code": 500,
  "message": "请先生成大纲",
  "data": null
}
```

---

# 9. 后端 model 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/model
```

包含：

```text
dto
entity
enums
vo
```

---

## 9.1 entity：数据库实体

路径：

```text
model/entity
```

### `Article.java`

作用：

```text
对应数据库 article 表。
```

数据库中一行文章任务，在 Java 中就是一个 `Article` 对象。

主要字段：

```java
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
```

为什么 `titleOptions`、`outline` 在数据库是 JSON，但 Java 中用 String？

```text
当前为了简化，数据库 JSON 字段在 Java 中按字符串保存。
前端拿到字符串后再 JSON.parse。
后端生成时用 ObjectMapper 把对象转成 JSON 字符串。
```

---

## 9.2 dto：前端请求对象

路径：

```text
model/dto
```

DTO 的意思是：

```text
Data Transfer Object，数据传输对象。
```

它用于接收前端传给后端的数据。

---

### `ArticleCreateRequest.java`

作用：

```text
接收创建文章任务时的请求参数。
```

字段：

```java
@NotBlank(message = "选题不能为空")
private String topic;

private String style;
```

前端请求示例：

```json
{
  "topic": "AI产品经理如何提升效率",
  "style": "TECH"
}
```

---

### `ArticleListRequest.java`

作用：

```text
接收查询文章列表时的分页参数。
```

字段：

```java
private int pageNo = 1;
private int pageSize = 10;
```

前端请求示例：

```json
{
  "pageNo": 1,
  "pageSize": 10
}
```

---

### `ConfirmTitleRequest.java`

作用：

```text
接收用户确认标题时的请求参数。
```

字段：

```java
@NotBlank(message = "任务 ID 不能为空")
private String taskId;

@NotBlank(message = "标题不能为空")
private String selectedTitle;
```

前端请求示例：

```json
{
  "taskId": "abc123",
  "selectedTitle": "AI 产品经理如何用工具提升 10 倍效率"
}
```

---

## 9.3 enums：枚举

路径：

```text
model/enums
```

### `ArticlePhase.java`

作用：

```text
定义文章任务的业务阶段。
```

```java
public enum ArticlePhase {
    CREATED,
    TITLE_SELECTION,
    OUTLINE_EDITING,
    CONTENT_GENERATION,
    COMPLETED,
    FAILED
}
```

---

### `ArticleStatus.java`

作用：

```text
定义任务执行状态。
```

```java
public enum ArticleStatus {
    INIT,
    RUNNING,
    SUCCESS,
    FAILED
}
```

---

## 9.4 vo：返回给前端的对象

路径：

```text
model/vo
```

VO 的意思是：

```text
View Object，展示对象。
```

它用于返回给前端展示。

---

### `ArticleVO.java`

作用：

```text
返回文章任务详情给前端。
```

它和 `Article` 很像，但可以隐藏不想暴露给前端的字段，比如：

```text
isDeleted
```

核心方法：

```java
public static ArticleVO fromEntity(Article article) {
    if (article == null) {
        return null;
    }
    ArticleVO articleVO = new ArticleVO();
    BeanUtils.copyProperties(article, articleVO);
    return articleVO;
}
```

作用：

```text
把 Article 实体转换成 ArticleVO。
```

---

### `TitleOption.java`

作用：

```text
表示一个标题候选。
```

字段：

```java
private String title;
private String reason;
```

示例：

```json
{
  "title": "AI 产品经理如何用工具提升 10 倍效率",
  "reason": "突出效率提升，适合职场和技术读者"
}
```

---

### `OutlineItem.java`

作用：

```text
表示文章大纲中的一个章节。
```

字段：

```java
private String heading;
private String description;
```

示例：

```json
{
  "heading": "一、为什么 AI 产品经理需要系统化提效",
  "description": "说明 AI 产品经理面对复杂任务时需要提效。"
}
```

---

### `SseMessage.java`

作用：

```text
SSE 推送给前端的消息对象。
```

字段：

```java
private String type;
private String message;
private String content;
private String phase;
private String status;
```

消息类型：

| type | 含义 |
|---|---|
| `progress` | 生成过程进度 |
| `content` | 正文片段 |
| `done` | 生成完成 |
| `fail` | 生成失败 |

---

# 10. 后端 mapper 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/mapper
```

作用：

```text
数据库访问层。
```

---

## 10.1 `ArticleMapper.java`

作用：

```text
直接执行 SQL 操作 article 表。
```

主要方法：

| 方法 | 作用 |
|---|---|
| `insert` | 新增文章任务 |
| `selectByTaskId` | 根据 taskId 查询详情 |
| `list` | 分页查询文章列表 |
| `count` | 查询文章总数 |
| `deleteByTaskId` | 软删除文章任务 |
| `updateTitleOptions` | 保存标题候选 |
| `updateSelectedTitleAndOutline` | 保存已选标题和大纲 |
| `updateContent` | 保存正文内容 |

---

### `insert`

作用：

```text
创建文章任务。
```

SQL：

```java
@Insert("""
        INSERT INTO article (task_id, topic, style, phase, status)
        VALUES (#{taskId}, #{topic}, #{style}, #{phase}, #{status})
        """)
int insert(Article article);
```

---

### `selectByTaskId`

作用：

```text
根据 taskId 查询文章详情。
```

SQL：

```java
@Select("""
        SELECT *
        FROM article
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        LIMIT 1
        """)
Article selectByTaskId(String taskId);
```

---

### `list`

作用：

```text
分页查询文章任务列表。
```

SQL：

```java
@Select("""
        SELECT *
        FROM article
        WHERE is_deleted = 0
        ORDER BY create_time DESC
        LIMIT #{offset}, #{pageSize}
        """)
List<Article> list(@Param("offset") int offset, @Param("pageSize") int pageSize);
```

---

### `deleteByTaskId`

作用：

```text
软删除文章任务。
```

SQL：

```java
@Update("""
        UPDATE article
        SET is_deleted = 1
        WHERE task_id = #{taskId}
          AND is_deleted = 0
        """)
int deleteByTaskId(String taskId);
```

为什么软删除？

```text
数据不会真的消失，方便后续恢复、审计和排查问题。
```

---

### `updateTitleOptions`

作用：

```text
保存标题候选 JSON。
```

保存到字段：

```text
title_options
```

---

### `updateSelectedTitleAndOutline`

作用：

```text
保存用户选择的标题和 AI 生成的大纲。
```

保存到字段：

```text
selected_title
outline
```

---

### `updateContent`

作用：

```text
保存 AI 生成的 Markdown 正文。
```

保存到字段：

```text
content
```

---

# 11. 后端 service 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/service
```

作用：

```text
业务逻辑接口层。
```

---

## 11.1 `ArticleService.java`

作用：

```text
定义文章任务相关业务能力。
```

主要方法：

```java
String createArticle(ArticleCreateRequest request);

ArticleVO getByTaskId(String taskId);

PageResult<ArticleVO> listArticles(ArticleListRequest request);

Boolean deleteByTaskId(String taskId);

ArticleVO generateTitles(String taskId);

ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request);

ArticleVO generateContent(String taskId);

SseEmitter streamGenerateContent(String taskId);
```

这些方法代表完整业务能力：

| 方法 | 作用 |
|---|---|
| `createArticle` | 创建文章任务 |
| `getByTaskId` | 查询文章详情 |
| `listArticles` | 查询文章列表 |
| `deleteByTaskId` | 删除文章任务 |
| `generateTitles` | 生成标题候选 |
| `confirmTitleAndGenerateOutline` | 确认标题并生成大纲 |
| `generateContent` | 一次性生成正文 |
| `streamGenerateContent` | 流式生成正文 |

---

## 11.2 `ArticleServiceImpl.java`

路径：

```text
service/impl/ArticleServiceImpl.java
```

作用：

```text
真正实现文章任务业务逻辑。
```

它连接了：

```text
Controller
↓
ServiceImpl
↓
Mapper
↓
Database
```

也连接了：

```text
ServiceImpl
↓
Agent
↓
LlmClient
```

主要依赖：

```java
private final ArticleMapper articleMapper;
private final TitleGeneratorAgent titleGeneratorAgent;
private final OutlineGeneratorAgent outlineGeneratorAgent;
private final ContentGeneratorAgent contentGeneratorAgent;
private final ObjectMapper objectMapper;
```

依赖解释：

| 依赖 | 作用 |
|---|---|
| `ArticleMapper` | 操作数据库 |
| `TitleGeneratorAgent` | 生成标题候选 |
| `OutlineGeneratorAgent` | 生成大纲 |
| `ContentGeneratorAgent` | 生成正文 |
| `ObjectMapper` | Java 对象和 JSON 字符串互转 |

---

### `createArticle`

作用：

```text
创建文章任务。
```

核心逻辑：

```text
1. 生成 taskId
2. 设置 topic
3. 设置 style
4. 设置 phase = CREATED
5. 设置 status = INIT
6. 插入数据库
7. 返回 taskId
```

---

### `generateTitles`

作用：

```text
根据文章选题生成标题候选。
```

核心逻辑：

```text
1. 根据 taskId 查文章
2. 调用 TitleGeneratorAgent
3. 得到 List<TitleOption>
4. 转成 JSON 字符串
5. 保存到 title_options
6. 更新 phase = TITLE_SELECTION
7. 更新 status = SUCCESS
```

---

### `confirmTitleAndGenerateOutline`

作用：

```text
用户确认标题后生成大纲。
```

核心逻辑：

```text
1. 根据 taskId 查文章
2. 读取 selectedTitle
3. 调用 OutlineGeneratorAgent
4. 得到 List<OutlineItem>
5. 转成 JSON 字符串
6. 保存 selected_title 和 outline
7. 更新 phase = OUTLINE_EDITING
8. 更新 status = SUCCESS
```

---

### `generateContent`

作用：

```text
一次性生成正文。
```

核心逻辑：

```text
1. 根据 taskId 查文章
2. 检查 selectedTitle 是否存在
3. 检查 outline 是否存在
4. 调用 ContentGeneratorAgent
5. 得到 Markdown 正文
6. 保存到 content
7. 更新 phase = CONTENT_GENERATION
8. 更新 status = SUCCESS
```

---

### `streamGenerateContent`

作用：

```text
通过 SSE 流式生成正文。
```

核心逻辑：

```text
1. 创建 SseEmitter
2. 异步执行生成流程
3. 推送 progress 消息
4. 检查文章任务
5. 检查标题和大纲
6. 调用 ContentGeneratorAgent
7. 拆分 Markdown 正文
8. 逐段推送 content 消息
9. 保存完整正文到数据库
10. 推送 done 消息
11. 出错时推送 fail 消息
```

这个方法是 Day 5 的核心。

---

# 12. 后端 agent 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/agent
```

作用：

```text
存放 AI Agent 和模型调用相关逻辑。
```

当前文件：

```text
LlmClient.java
MockLlmClient.java
TitleGeneratorAgent.java
OutlineGeneratorAgent.java
ContentGeneratorAgent.java
```

---

## 12.1 `LlmClient.java`

作用：

```text
统一大模型调用入口。
```

代码：

```java
public interface LlmClient {

    String chat(String prompt);
}
```

为什么要抽象成接口？

```text
Agent 不直接依赖具体模型厂商。
以后换 OpenAI、DeepSeek、通义千问、本地模型时，只需要换 LlmClient 实现。
```

---

## 12.2 `MockLlmClient.java`

作用：

```text
模拟大模型返回。
```

它根据 prompt 内容判断返回什么：

```text
如果 prompt 包含“生成 3 个标题”
→ 返回标题 JSON 数组

如果 prompt 包含“生成文章大纲”
→ 返回大纲 JSON 数组

如果 prompt 包含“生成文章正文”
→ 返回 Markdown 正文
```

当前它的价值是：

```text
不用 API Key，也能跑通完整 AI 应用流程。
```

---

## 12.3 `TitleGeneratorAgent.java`

作用：

```text
根据 topic 和 style 生成 3 个标题候选。
```

核心流程：

```text
1. 拼接 Prompt
2. 调用 llmClient.chat(prompt)
3. 得到 JSON 字符串
4. 用 ObjectMapper 解析成 List<TitleOption>
```

---

## 12.4 `OutlineGeneratorAgent.java`

作用：

```text
根据 topic、selectedTitle、style 生成文章大纲。
```

核心流程：

```text
1. 拼接 Prompt
2. 调用 llmClient.chat(prompt)
3. 得到 JSON 字符串
4. 用 ObjectMapper 解析成 List<OutlineItem>
```

---

## 12.5 `ContentGeneratorAgent.java`

作用：

```text
根据 topic、selectedTitle、outline、style 生成 Markdown 正文。
```

核心流程：

```text
1. 拼接 Prompt
2. 调用 llmClient.chat(prompt)
3. 直接返回 Markdown 字符串
```

为什么不解析 JSON？

```text
正文适合直接用 Markdown 文本保存和展示。
```

---

# 13. 后端 controller 包

路径：

```text
backend/src/main/java/com/nana/aiarticlestudio/controller
```

作用：

```text
接收前端请求，对外暴露 HTTP API。
```

---

## 13.1 `HealthController.java`

作用：

```text
健康检查接口。
```

接口：

```text
GET /api/health
```

返回：

```text
ok
```

用途：

```text
判断后端是否启动成功。
```

---

## 13.2 `ArticleController.java`

作用：

```text
文章任务相关接口入口。
```

当前接口：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/article/create` | POST | 创建文章任务 |
| `/api/article/{taskId}` | GET | 查询文章详情 |
| `/api/article/list` | POST | 查询文章列表 |
| `/api/article/delete/{taskId}` | POST | 删除文章任务 |
| `/api/article/generate-titles/{taskId}` | POST | 生成标题候选 |
| `/api/article/confirm-title` | POST | 确认标题并生成大纲 |
| `/api/article/generate-content/{taskId}` | POST | 一次性生成正文 |
| `/api/article/stream-generate-content/{taskId}` | GET | SSE 流式生成正文 |

---

### 为什么 SSE 接口用 GET？

因为浏览器原生：

```text
EventSource
```

只支持 GET 请求。

所以流式接口写成：

```java
@GetMapping(value = "/stream-generate-content/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
```

其中：

```text
MediaType.TEXT_EVENT_STREAM_VALUE
```

表示这个接口返回 SSE 事件流。

---

# 14. 前端项目结构总览

前端目录：

```text
frontend
├── index.html
├── package.json
├── vite.config.ts
└── src
    ├── main.ts
    ├── App.vue
    ├── api
    │   ├── request.ts
    │   └── article.ts
    ├── router
    │   └── index.ts
    └── pages
        ├── ArticleList.vue
        └── ArticleDetail.vue
```

---

# 15. 前端根文件说明

## 15.1 `package.json`

作用：

```text
前端项目依赖和脚本配置。
```

里面包含：

```text
vue
vite
typescript
axios
vue-router
pinia
ant-design-vue
markdown-it
```

常用命令：

```powershell
npm install
```

含义：

```text
根据 package.json 安装前端依赖。
```

```powershell
npm run dev
```

含义：

```text
启动前端开发服务器。
```

---

## 15.2 `main.ts`

路径：

```text
frontend/src/main.ts
```

作用：

```text
前端应用入口。
```

当前主要做三件事：

```text
1. 创建 Vue 应用
2. 注册 Ant Design Vue
3. 注册路由 router
4. 挂载到页面 #app
```

代码结构：

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

---

## 15.3 `App.vue`

路径：

```text
frontend/src/App.vue
```

作用：

```text
前端根组件。
```

当前代码：

```vue
<template>
  <router-view />
</template>
```

含义：

```text
App.vue 只负责放一个路由出口。
当前显示哪个页面，由 vue-router 决定。
```

---

# 16. 前端 router 目录

路径：

```text
frontend/src/router/index.ts
```

作用：

```text
配置前端页面路由。
```

当前路由：

```ts
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
```

路由解释：

| 路径 | 页面 |
|---|---|
| `/` | 自动跳转到 `/article/list` |
| `/article/list` | 文章任务列表页 |
| `/article/detail/:taskId` | 文章任务详情页 |

`:taskId` 是动态参数。

例如：

```text
/article/detail/abc123
```

前端可以通过：

```ts
route.params.taskId
```

拿到：

```text
abc123
```

---

# 17. 前端 api 目录

路径：

```text
frontend/src/api
```

作用：

```text
统一管理前端请求后端接口的代码。
```

---

## 17.1 `request.ts`

作用：

```text
创建 axios 实例，统一配置后端地址。
```

代码：

```ts
import axios from 'axios'

const request = axios.create({
  baseURL: 'http://localhost:8123',
  timeout: 30000,
})

export default request
```

含义：

```text
以后所有普通 HTTP 请求都会发到 http://localhost:8123。
```

---

## 17.2 `article.ts`

作用：

```text
封装所有文章任务相关接口。
```

它定义了前端类型：

```ts
BaseResponse<T>
PageResult<T>
ArticleVO
TitleOption
OutlineItem
ArticleCreateRequest
ArticleListRequest
ConfirmTitleRequest
```

也封装了接口方法：

| 方法 | 后端接口 | 作用 |
|---|---|---|
| `createArticle` | POST `/api/article/create` | 创建任务 |
| `listArticles` | POST `/api/article/list` | 查询列表 |
| `getArticle` | GET `/api/article/{taskId}` | 查询详情 |
| `deleteArticle` | POST `/api/article/delete/{taskId}` | 删除任务 |
| `generateTitles` | POST `/api/article/generate-titles/{taskId}` | 生成标题 |
| `confirmTitle` | POST `/api/article/confirm-title` | 确认标题并生成大纲 |
| `generateContent` | POST `/api/article/generate-content/{taskId}` | 一次性生成正文 |
| `streamGenerateContentUrl` | GET `/api/article/stream-generate-content/{taskId}` | 获取 SSE URL |

为什么 `streamGenerateContentUrl` 不用 axios？

```text
SSE 使用浏览器原生 EventSource。
EventSource 需要直接传入 URL。
所以这里返回完整 URL，而不是 axios 请求。
```

---

# 18. 前端 pages 目录

路径：

```text
frontend/src/pages
```

作用：

```text
存放页面组件。
```

当前有：

```text
ArticleList.vue
ArticleDetail.vue
```

---

## 18.1 `ArticleList.vue`

作用：

```text
文章任务列表页。
```

它负责：

```text
1. 输入文章选题
2. 选择文章风格
3. 创建文章任务
4. 展示文章任务列表
5. 删除文章任务
6. 点击进入详情页
```

主要状态：

```ts
const topic = ref('')
const style = ref('TECH')
const loading = ref(false)
const articles = ref<ArticleVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
```

状态解释：

| 状态 | 含义 |
|---|---|
| `topic` | 输入框中的文章选题 |
| `style` | 当前选择的文章风格 |
| `loading` | 页面是否正在请求 |
| `articles` | 文章任务列表 |
| `total` | 总任务数 |
| `pageNo` | 当前页 |
| `pageSize` | 每页条数 |

主要方法：

| 方法 | 作用 |
|---|---|
| `loadArticles` | 加载文章列表 |
| `handleCreate` | 创建文章任务 |
| `handleView` | 跳转到详情页 |
| `handleDelete` | 删除文章任务 |

页面加载时：

```ts
onMounted(() => {
  loadArticles()
})
```

作用：

```text
进入列表页后自动加载文章列表。
```

---

## 18.2 `ArticleDetail.vue`

作用：

```text
文章任务详情页。
```

它负责：

```text
1. 展示文章基础信息
2. 生成标题候选
3. 展示标题候选
4. 选择标题
5. 确认标题并生成大纲
6. 展示大纲
7. 一次性生成正文
8. 流式生成正文
9. 展示 Markdown 正文
10. 展示 SSE 生成进度
```

主要状态：

```ts
const loading = ref(false)
const article = ref<ArticleVO | null>(null)
const selectedTitle = ref('')
const streaming = ref(false)
const streamLogs = ref<string[]>([])
const streamContent = ref('')
```

状态解释：

| 状态 | 含义 |
|---|---|
| `loading` | 普通接口请求状态 |
| `article` | 当前文章详情 |
| `selectedTitle` | 用户选择的标题 |
| `streaming` | 是否正在流式生成 |
| `streamLogs` | SSE 生成进度日志 |
| `streamContent` | SSE 实时正文内容 |

---

### `taskId`

```ts
const taskId = computed(() => route.params.taskId as string)
```

作用：

```text
从 URL 中读取当前文章任务 ID。
```

例如 URL：

```text
/article/detail/abc123
```

那么：

```text
taskId = abc123
```

---

### `titleOptions`

```ts
const titleOptions = computed(() => {
  return parseJsonArray<TitleOption>(article.value?.titleOptions)
})
```

作用：

```text
把数据库中的 title_options JSON 字符串解析成标题候选数组。
```

---

### `outlineItems`

```ts
const outlineItems = computed(() => {
  return parseJsonArray<OutlineItem>(article.value?.outline)
})
```

作用：

```text
把数据库中的 outline JSON 字符串解析成大纲数组。
```

---

### `displayContent`

```ts
const displayContent = computed(() => {
  return streamContent.value || article.value?.content || ''
})
```

作用：

```text
如果正在流式生成，就优先展示 streamContent。
如果没有流式内容，就展示数据库中的 article.content。
```

---

### `contentHtml`

```ts
const contentHtml = computed(() => {
  if (!displayContent.value) {
    return ''
  }
  return markdown.render(displayContent.value)
})
```

作用：

```text
把 Markdown 正文转换成 HTML，用于页面展示。
```

---

### `handleGenerateTitles`

作用：

```text
点击“生成标题”按钮后调用后端标题生成接口。
```

流程：

```text
调用 generateTitles(taskId)
↓
后端生成标题候选
↓
保存到 title_options
↓
前端更新 article
↓
页面展示标题候选
```

---

### `handleConfirmTitle`

作用：

```text
用户选择标题后，点击“确认标题并生成大纲”。
```

流程：

```text
检查 selectedTitle
↓
调用 confirmTitle
↓
后端生成大纲
↓
保存 selected_title 和 outline
↓
前端更新 article
↓
页面展示大纲
```

---

### `handleGenerateContent`

作用：

```text
一次性生成正文。
```

流程：

```text
检查是否已有大纲
↓
调用 generateContent
↓
后端生成完整 Markdown 正文
↓
保存到 content
↓
前端更新 article
↓
页面展示正文
```

---

### `handleStreamGenerateContent`

作用：

```text
流式生成正文。
```

流程：

```text
检查是否已有大纲
↓
创建 EventSource
↓
连接后端 SSE 接口
↓
监听 progress 事件
↓
监听 content 事件
↓
监听 done 事件
↓
监听 fail 事件
↓
实时更新 streamLogs 和 streamContent
```

这是 Day 5 的前端核心方法。

---

# 19. 当前完整业务流程

## 19.1 创建文章任务

前端：

```text
ArticleList.vue
handleCreate
```

调用：

```text
createArticle
```

后端：

```text
ArticleController.createArticle
↓
ArticleServiceImpl.createArticle
↓
ArticleMapper.insert
↓
article 表新增一条记录
```

数据库结果：

```text
phase = CREATED
status = INIT
```

---

## 19.2 生成标题候选

前端：

```text
ArticleDetail.vue
handleGenerateTitles
```

后端：

```text
ArticleController.generateTitles
↓
ArticleServiceImpl.generateTitles
↓
TitleGeneratorAgent.generate
↓
MockLlmClient.chat
↓
ArticleMapper.updateTitleOptions
```

数据库结果：

```text
title_options = 标题候选 JSON
phase = TITLE_SELECTION
status = SUCCESS
```

---

## 19.3 确认标题并生成大纲

前端：

```text
ArticleDetail.vue
handleConfirmTitle
```

后端：

```text
ArticleController.confirmTitle
↓
ArticleServiceImpl.confirmTitleAndGenerateOutline
↓
OutlineGeneratorAgent.generate
↓
MockLlmClient.chat
↓
ArticleMapper.updateSelectedTitleAndOutline
```

数据库结果：

```text
selected_title = 用户选择的标题
outline = 大纲 JSON
phase = OUTLINE_EDITING
status = SUCCESS
```

---

## 19.4 一次性生成正文

前端：

```text
ArticleDetail.vue
handleGenerateContent
```

后端：

```text
ArticleController.generateContent
↓
ArticleServiceImpl.generateContent
↓
ContentGeneratorAgent.generate
↓
MockLlmClient.chat
↓
ArticleMapper.updateContent
```

数据库结果：

```text
content = Markdown 正文
phase = CONTENT_GENERATION
status = SUCCESS
```

---

## 19.5 流式生成正文

前端：

```text
ArticleDetail.vue
handleStreamGenerateContent
↓
EventSource
```

后端：

```text
ArticleController.streamGenerateContent
↓
ArticleServiceImpl.streamGenerateContent
↓
SseEmitter
↓
ContentGeneratorAgent.generate
↓
MockLlmClient.chat
↓
分段推送 progress/content/done
↓
ArticleMapper.updateContent
```

数据库结果：

```text
content = Markdown 正文
phase = CONTENT_GENERATION
status = SUCCESS
```

---

# 20. 常用启动命令

## 20.1 启动 Docker 容器

在 `deploy` 目录：

```powershell
docker compose up -d
```

含义：

```text
启动 MySQL 和 Redis 容器。
```

检查：

```powershell
docker ps
```

含义：

```text
查看 MySQL 和 Redis 是否正在运行。
```

---

## 20.2 启动后端

在 IDEA 中运行：

```text
AiArticleStudioApplication
```

检查：

```text
http://localhost:8123/api/health
```

看到：

```text
ok
```

说明后端启动成功。

---

## 20.3 启动前端

进入前端目录：

```powershell
cd D:\Desktop\projects\ai-article-studio\frontend
```

启动：

```powershell
npm run dev
```

含义：

```text
启动 Vue 前端开发服务器。
```

访问：

```text
http://localhost:5173
```

---

# 21. 数据库验收方法

## 21.1 进入 MySQL

```powershell
docker exec -it ai_article_mysql mysql -uroot -p123456
```

含义：

```text
进入 MySQL 容器，并用 root / 123456 登录数据库。
```

如果中文显示乱码，可以先执行：

```powershell
chcp 65001
```

含义：

```text
把当前 PowerShell 终端编码切到 UTF-8。
```

再进入：

```powershell
docker exec -it ai_article_mysql mysql --default-character-set=utf8mb4 -uroot -p123456
```

含义：

```text
进入 MySQL 容器，并强制 MySQL 客户端使用 utf8mb4 字符集。
```

进入 MySQL 后执行：

```sql
SET NAMES utf8mb4;
```

作用：

```text
告诉 MySQL 当前连接使用 utf8mb4 编码传输数据。
```

---

## 21.2 查询文章内容是否保存

```sql
USE ai_article_studio;

SELECT 
  task_id,
  topic,
  phase,
  status,
  CHAR_LENGTH(content) AS content_chars,
  LEFT(content, 300) AS content_preview
FROM article
WHERE task_id = '你的taskId';
```

验收标准：

```text
phase = CONTENT_GENERATION
status = SUCCESS
content_chars > 0
content_preview 有 Markdown 正文
```

---

# 22. 当前项目已经具备的能力

到 Day 5 为止，项目已经具备：

```text
1. Docker 环境
2. MySQL 数据库
3. Redis 容器
4. Spring Boot 后端
5. Vue 3 前端
6. 文章任务 CRUD
7. 标题生成 Agent
8. 大纲生成 Agent
9. 正文生成 Agent
10. Markdown 正文展示
11. SSE 流式输出
12. 生成进度实时展示
13. 正文保存到数据库
```

---

# 23. 当前项目还没有做的能力

当前还没有做：

```text
1. 真实大模型 API 接入
2. 用户登录注册
3. 多用户数据隔离
4. Agent 执行日志写入 agent_log
5. 正文编辑
6. 大纲编辑
7. 图片生成
8. 图文合成
9. 导出 Markdown / Word / PDF
10. 部署上线
11. 权限控制
12. 任务失败重试
13. 更完整的异常体系
```

---

# 24. 后续技术路线建议

## Day 6：接入真实大模型

目标：

```text
1. 新增 OpenAICompatibleLlmClient
2. 通过配置文件读取 API Key
3. 替换 MockLlmClient
4. 让标题、大纲、正文真正由模型生成
5. 处理模型 JSON 输出不稳定问题
```

---

## Day 7：Agent 日志与稳定性

目标：

```text
1. 写入 agent_log 表
2. 记录每次 Agent 输入输出
3. 记录耗时
4. 记录失败原因
5. 增加重试机制
```

---

## 后续产品化方向

可以继续做：

```text
1. 大纲可编辑
2. 正文可编辑
3. 一键复制正文
4. 导出 Markdown
5. 导出 Word
6. 支持不同文章风格
7. 支持不同平台模板
8. 文章历史版本
9. 多用户系统
10. 项目部署上线
```

---

# 25. 初学者应该重点理解什么？

如果你是初学者，不要只复制代码，重点理解这几条线：

---

## 25.1 前后端调用链路

```text
Vue 页面点击按钮
↓
api/article.ts 调用 axios
↓
Spring Boot Controller 接收请求
↓
ServiceImpl 处理业务
↓
Mapper 执行 SQL
↓
MySQL 保存数据
↓
后端返回结果
↓
前端更新页面
```

---

## 25.2 Agent 调用链路

```text
ServiceImpl
↓
TitleGeneratorAgent / OutlineGeneratorAgent / ContentGeneratorAgent
↓
LlmClient 接口
↓
MockLlmClient
```

后续接真实模型时：

```text
MockLlmClient
```

会被替换成：

```text
OpenAICompatibleLlmClient
```

但 Agent 层不用大改。

---

## 25.3 数据状态变化

一条文章任务从创建到正文完成，大概经历：

```text
CREATED / INIT
↓
TITLE_SELECTION / SUCCESS
↓
OUTLINE_EDITING / SUCCESS
↓
CONTENT_GENERATION / SUCCESS
```

这就是一个简单的状态机。

---

## 25.4 SSE 流式通信

普通接口：

```text
前端请求一次
后端返回一次
```

SSE 接口：

```text
前端连接一次
后端可以持续推送多次
```

这就是为什么 SSE 更适合 AI 生成过程展示。

---

# 26. 总结

当前项目虽然还没有接真实大模型，但它已经具备了一个 AI 应用的核心骨架：

```text
前端交互
后端接口
数据库保存
Agent 分层
模型调用抽象
正文生成流程
SSE 实时推送
```

后续接入真实大模型时，不需要推翻当前结构，只需要重点改造：

```text
LlmClient 的实现
Prompt 稳定性
模型返回解析
错误重试
Agent 日志
```

这就是当前项目最重要的学习价值：  
你不是只写了一个页面，而是搭出了一个可扩展的 AI 应用架构。
