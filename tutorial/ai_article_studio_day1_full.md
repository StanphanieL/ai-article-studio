# AI Article Studio：Day 1 完整开发教程

> 目标：搭建项目地基，跑通 MySQL、Redis、Spring Boot 后端、Vue 前端，并完成前后端联调。  
> 推荐开发方式：**IDEA 写后端，Cursor / VS Code 写前端**。  
> 项目名称：`AI Article Studio`

---

## 0. Day 1 最终成果

完成 Day 1 后，你应该得到这个项目结构：

```text
ai-article-studio
├── backend        # Spring Boot 后端
├── frontend       # Vue 3 前端
└── deploy         # Docker / 部署相关配置
```

最终验收标准：

```text
1. Docker 中 MySQL 正常运行
2. Docker 中 Redis 正常运行
3. MySQL 中存在 article 和 agent_log 两张表
4. Spring Boot 后端运行在 http://localhost:8123
5. Vue 前端运行在 http://localhost:5173
6. 前端点击按钮后，能显示后端返回的 ok
```

---

## 1. 环境检查

打开 PowerShell，执行：

```powershell
java -version
node -v
npm -v
git --version
docker --version
docker compose version
```

这些命令的含义：

```text
java -version：检查 JDK 是否安装成功
node -v：检查 Node.js 是否安装成功
npm -v：检查 npm 是否安装成功
git --version：检查 Git 是否安装成功
docker --version：检查 Docker 是否安装成功
docker compose version：检查 Docker Compose 是否可用
```

建议版本：

```text
JDK：21
Node.js：18 或更高
npm：随 Node 一起安装即可
Git：任意较新版本
Docker Desktop：可正常启动即可
IDE：IntelliJ IDEA
```

---

## 2. 创建项目目录

在 PowerShell 中执行：

```powershell
cd D:\Desktop
mkdir projects
cd projects
mkdir ai-article-studio
cd ai-article-studio
mkdir backend
mkdir frontend
mkdir deploy
```

检查目录：

```powershell
dir
```

应该看到：

```text
backend
frontend
deploy
```

---

## 3. 创建 Docker Compose 配置

进入 `deploy` 目录：

```powershell
cd D:\Desktop\projects\ai-article-studio\deploy
notepad docker-compose.yml
```

`notepad docker-compose.yml` 的含义：

```text
用记事本创建或打开 docker-compose.yml 文件。
```

把下面内容复制进去：

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

这个文件的作用：

```text
mysql：启动一个 MySQL 8.0 容器
redis：启动一个 Redis 7 容器
13306:3306：把本机 13306 端口映射到 MySQL 容器的 3306 端口
16379:6379：把本机 16379 端口映射到 Redis 容器的 6379 端口
MYSQL_ROOT_PASSWORD: 123456：设置 MySQL root 用户密码
MYSQL_DATABASE: ai_article_studio：容器启动时自动创建数据库
mysql_data：保存 MySQL 数据，避免容器删除后数据丢失
```

---

## 4. 启动 MySQL 和 Redis

在 `deploy` 目录执行：

```powershell
docker compose up -d
```

含义：

```text
根据当前目录下的 docker-compose.yml 启动 MySQL 和 Redis。
-d 表示后台运行，不占用当前终端。
```

第一次执行时会下载镜像，速度取决于网络。

启动完成后执行：

```powershell
docker ps
```

含义：

```text
查看当前正在运行的 Docker 容器。
```

应该看到两个容器：

```text
ai_article_mysql
ai_article_redis
```

---

## 5. Docker Hub 下载失败时的处理

如果出现类似错误：

```text
failed to resolve reference "docker.io/library/mysql:8.0"
Docker Desktop has no HTTPS proxy
```

说明 Docker 连不上 Docker Hub，不是项目配置问题。

推荐解决方式：

```text
Docker Desktop → Settings → Resources → Proxies
```

如果电脑使用 Clash / V2Ray / 其他代理，通常可以填：

```text
HTTP Proxy:  http://127.0.0.1:7890
HTTPS Proxy: http://127.0.0.1:7890
```

端口不一定是 `7890`，要以你的代理软件实际端口为准。

然后点击：

```text
Apply & Restart
```

重启 Docker Desktop 后再执行：

```powershell
docker compose up -d
```

含义：

```text
重新根据 docker-compose.yml 启动容器，并自动下载缺失镜像。
```

也可以单独测试拉取镜像：

```powershell
docker pull mysql:8.0
docker pull redis:7
```

含义：

```text
docker pull mysql:8.0：单独下载 MySQL 8.0 镜像
docker pull redis:7：单独下载 Redis 7 镜像
```

---

## 6. 创建数据库表

进入 MySQL：

```powershell
docker exec -it ai_article_mysql mysql -uroot -p123456
```

含义：

```text
进入名为 ai_article_mysql 的 MySQL 容器，
并用 root 用户和密码 123456 登录 MySQL。
```

进入成功后会看到：

```text
mysql>
```

然后执行：

```sql
USE ai_article_studio;

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

两张表的作用：

```text
article：
保存文章生成任务。
包括选题、标题、大纲、正文、当前阶段、任务状态等。

agent_log：
保存每个 Agent 的执行日志。
包括 Agent 名称、输入、输出、耗时、成功或失败状态等。
```

检查表是否创建成功：

```sql
SHOW TABLES;
```

应该看到：

```text
article
agent_log
```

退出 MySQL：

```sql
exit;
```

---

## 7. 创建 Spring Boot 后端项目

打开浏览器进入：

```text
https://start.spring.io/
```

配置如下：

```text
Project: Maven
Language: Java
Spring Boot: 3.5.x
Group: com.nana
Artifact: ai-article-studio
Name: ai-article-studio
Package name: com.nana.aiarticlestudio
Packaging: Jar
Configuration: YAML
Java: 21
```

### 这些配置分别是什么意思？

| 配置项 | 含义 |
|---|---|
| Maven | Java 项目的依赖管理和构建工具，依赖会写在 `pom.xml` 中 |
| Java | 后端开发语言 |
| Spring Boot 3.5.x | 后端框架，用于快速搭建 Web 服务、数据库连接、配置管理等 |
| Group: `com.nana` | 项目组织名 / 包名前缀，避免和其他项目重名 |
| Artifact: `ai-article-studio` | 项目名，也会影响最终打包产物名 |
| Package name: `com.nana.aiarticlestudio` | Java 代码的根包名 |
| Packaging: Jar | 最终打包成可运行的 `.jar` 后端程序 |
| Configuration: YAML | 使用 `application.yml` 作为配置文件 |
| Java: 21 | 使用 JDK 21，长期支持版本 |

### 添加依赖

在 Spring Initializr 中添加：

```text
Spring Web
Lombok
Validation
MyBatis Framework
MySQL Driver
Spring Data Redis
```

这些依赖的作用：

| 依赖 | 作用 |
|---|---|
| Spring Web | 让后端可以提供 HTTP 接口，比如 `/api/health` |
| Lombok | 减少 Java 模板代码，比如 getter、setter |
| Validation | 校验前端传来的参数，比如选题不能为空 |
| MyBatis Framework | 操作 MySQL 数据库 |
| MySQL Driver | 让 Spring Boot 能连接 MySQL |
| Spring Data Redis | 让 Spring Boot 能连接 Redis |

点击 **Generate** 下载压缩包。

下载后解压，把里面内容放到：

```text
D:\Desktop\projects\ai-article-studio\backend
```

最终结构必须是：

```text
backend
├── pom.xml
└── src
```

不要变成：

```text
backend
└── ai-article-studio
    ├── pom.xml
    └── src
```

如果多套了一层，把里面的 `pom.xml` 和 `src` 移到 `backend` 下。

---

# 8. IDEA 详细操作教程

IDEA 指的是 IntelliJ IDEA。它主要负责：

```text
1. 打开 Spring Boot 后端项目
2. 加载 Maven 依赖
3. 写 Java 代码
4. 运行 Spring Boot 后端
5. 查看后端启动日志和报错
```

你可以把它理解成：**IDEA 是 Java 后端项目的开发工具**。

---

## 8.1 用 IDEA 打开后端项目

启动 IntelliJ IDEA 后，首页一般会看到：

```text
New Project
Open
Get from VCS
```

选择：

```text
Open
```

然后选择这个目录：

```text
D:\Desktop\projects\ai-article-studio\backend
```

注意：一定是选择 `backend` 目录，不是选择里面的 `src`，也不是选择整个 `ai-article-studio`。

正确结构应该是：

```text
backend
├── pom.xml
└── src
```

IDEA 打开后，如果弹出：

```text
Trust Project?
```

选择：

```text
Trust Project
```

含义：

```text
允许 IDEA 正常加载这个项目的 Maven、Spring Boot 配置。
```

---

## 8.2 确认 Maven 依赖加载成功

打开项目后，IDEA 右下角可能会显示：

```text
Load Maven Project
Import Maven Project
```

选择加载。

如果没弹出来，可以看 IDEA 右侧边栏有没有：

```text
Maven
```

点开后应该看到：

```text
ai-article-studio
├── Lifecycle
├── Plugins
└── Dependencies
```

这说明 Maven 项目识别成功。

Maven 的作用：

```text
Maven = Java 项目的包管理器和构建工具。
```

我们在 Spring Initializr 选择的这些依赖：

```text
Spring Web
Lombok
Validation
MyBatis
MySQL Driver
Redis
```

都会写在：

```text
pom.xml
```

IDEA 会根据 `pom.xml` 自动下载这些依赖。

如果代码里出现大量红色，比如：

```text
Cannot resolve symbol SpringApplication
Cannot resolve symbol RestController
```

通常说明 Maven 依赖没下载完。  
可以在右侧 Maven 面板点击刷新按钮：

```text
Reload All Maven Projects
```

---

## 8.3 确认 JDK 21 配置正确

在 IDEA 顶部菜单选择：

```text
File → Project Structure
```

然后看：

```text
Project → SDK
```

这里应该是：

```text
JDK 21
```

如果不是，点击下拉框，选择你安装的 JDK 21。

如果没有看到 JDK 21，可以点：

```text
Add SDK → JDK
```

然后一般选择类似路径：

```text
C:\Program Files\Eclipse Adoptium\jdk-21...
```

设置好后点：

```text
Apply → OK
```

这一步的作用：

```text
告诉 IDEA 用 JDK 21 编译和运行你的 Spring Boot 后端项目。
```

---

## 8.4 熟悉 IDEA 左侧项目结构

左侧一般是 `Project` 面板，你会看到类似：

```text
backend
├── .mvn
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com.nana.aiarticlestudio
│   │   │       └── AiArticleStudioApplication.java
│   │   └── resources
│   │       └── application.yml
│   └── test
├── pom.xml
└── ...
```

重点看三个地方：

| 位置 | 作用 |
|---|---|
| `src/main/java` | 放 Java 后端代码 |
| `src/main/resources` | 放配置文件，比如 `application.yml` |
| `pom.xml` | Maven 依赖配置文件 |

---

## 8.5 配置 application.yml

找到：

```text
src/main/resources/application.yml
```

如果你看到的是：

```text
application.properties
```

就右键它：

```text
Refactor → Rename
```

改成：

```text
application.yml
```

然后写入：

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
```

这段配置的作用：

| 配置 | 作用 |
|---|---|
| `server.port: 8123` | 后端启动在 8123 端口 |
| `spring.application.name` | 项目名称 |
| `datasource` | 连接 MySQL |
| `localhost:13306` | 连接 Docker 暴露出来的 MySQL |
| `username/password` | MySQL 用户名和密码 |
| `redis.host/port` | 连接 Redis |
| `mybatis.mapper-locations` | 指定 MyBatis XML 文件位置 |
| `type-aliases-package` | 指定数据库实体类所在包 |

---

## 8.6 创建后端包结构

找到这个目录：

```text
src/main/java/com/nana/aiarticlestudio
```

右键它：

```text
New → Package
```

依次创建这些包：

```text
controller
service
service.impl
mapper
model
model.entity
model.dto
model.vo
model.enums
common
exception
config
manager
agent
```

如果你创建 `service.impl`，IDEA 会自动生成：

```text
service
└── impl
```

这是正常的。

这些包分别是：

| 包名 | 作用 |
|---|---|
| `controller` | 写接口，接收前端请求 |
| `service` | 写业务逻辑接口 |
| `service.impl` | 写业务逻辑实现 |
| `mapper` | 写数据库访问代码 |
| `model.entity` | 写数据库实体类 |
| `model.dto` | 写前端请求对象 |
| `model.vo` | 写后端返回对象 |
| `model.enums` | 写枚举 |
| `common` | 写统一返回结果、常量 |
| `exception` | 写异常处理 |
| `config` | 写配置类 |
| `manager` | 写管理器，比如 SSE 管理器 |
| `agent` | 写标题、大纲、正文等 Agent |

---

## 8.7 创建 HealthController

在 `controller` 包上右键：

```text
New → Java Class
```

类名输入：

```text
HealthController
```

然后写：

```java
package com.nana.aiarticlestudio.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public String health() {
        return "ok";
    }
}
```

它的作用：

```text
提供一个 /api/health 接口，用来测试后端是否正常启动。
```

几个注解简单理解：

| 注解 | 作用 |
|---|---|
| `@RestController` | 告诉 Spring：这是一个接口类 |
| `@GetMapping("/api/health")` | 浏览器访问 `/api/health` 时，会执行这个方法 |

---

## 8.8 创建 CorsConfig

在 `config` 包上右键：

```text
New → Java Class
```

类名输入：

```text
CorsConfig
```

然后写：

```java
package com.nana.aiarticlestudio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

它的作用：

```text
允许 Vue 前端 http://localhost:5173 请求 Spring Boot 后端 http://localhost:8123。
```

否则浏览器会因为跨域限制拦截请求。

---

## 8.9 启动 Spring Boot 项目

找到启动类：

```text
src/main/java/com/nana/aiarticlestudio/AiArticleStudioApplication.java
```

打开它，你会看到类似代码：

```java
@SpringBootApplication
public class AiArticleStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiArticleStudioApplication.class, args);
    }

}
```

在类左侧或 `main` 方法左侧，会有一个绿色三角形按钮。

点击它，选择：

```text
Run 'AiArticleStudioApplication'
```

IDEA 底部会打开 `Run` 窗口。

如果启动成功，你会看到类似：

```text
Tomcat started on port 8123
Started AiArticleStudioApplication
```

然后浏览器访问：

```text
http://localhost:8123/api/health
```

看到：

```text
ok
```

说明后端启动成功。

---

## 8.10 如果后端启动失败，看哪里？

看 IDEA 底部的：

```text
Run
```

或者：

```text
Console
```

里面会有红色报错。

常见问题：

### 8.10.1 Maven 依赖没下载完

表现：

```text
Cannot resolve symbol SpringApplication
Cannot resolve symbol RestController
```

解决：

```text
右侧 Maven 面板 → Reload All Maven Projects
```

也就是点击刷新图标。

### 8.10.2 JDK 没配好

表现：

```text
Project SDK is not defined
```

解决：

```text
File → Project Structure → Project → SDK → 选择 JDK 21
```

### 8.10.3 MySQL 连不上

表现：

```text
Communications link failure
Connection refused
```

先检查 Docker：

```powershell
docker ps
```

含义：

```text
查看 MySQL 和 Redis 容器是否正在运行。
```

确保看到：

```text
ai_article_mysql
ai_article_redis
```

### 8.10.4 8123 端口被占用

表现：

```text
Port 8123 was already in use
```

解决方式：把 `application.yml` 里的端口临时改成：

```yaml
server:
  port: 8124
```

不过正常情况下 8123 不会被占用。

---

## 8.11 IDEA 中常用操作

| 操作 | 方法 |
|---|---|
| 创建包 | 右键目录 → New → Package |
| 创建 Java 类 | 右键包 → New → Java Class |
| 重命名文件 | 右键文件 → Refactor → Rename |
| 运行项目 | 点击启动类旁边绿色三角形 |
| 停止项目 | 底部 Run 窗口红色方块 |
| 重新运行 | 底部 Run 窗口绿色循环箭头 |
| 搜索文件 | 双击 Shift |
| 全局搜索代码 | Ctrl + Shift + F |
| 格式化代码 | Ctrl + Alt + L |
| 查看 Maven | 右侧 Maven 面板 |
| 重新加载 Maven | Maven 面板刷新按钮 |

---

## 8.12 IDEA 部分你只需要完成的事

按照顺序做：

```text
1. 用 IDEA 打开 backend 目录
2. 确认 JDK 是 21，Maven 依赖加载成功
3. 配置 application.yml
4. 创建 HealthController 和 CorsConfig
5. 运行 AiArticleStudioApplication
```

成功后浏览器访问：

```text
http://localhost:8123/api/health
```

看到：

```text
ok
```

就说明 IDEA 这部分完成了。

---

## 9. 创建 Vue 前端项目

打开新的 PowerShell，执行：

```powershell
cd D:\Desktop\projects\ai-article-studio
rmdir /s frontend
npm create vite@latest frontend -- --template vue-ts
```

`rmdir /s frontend` 的含义：

```text
删除原来的 frontend 文件夹及其中所有内容。
因为 Vite 创建项目时希望目标目录不存在或为空。
```

`npm create vite@latest frontend -- --template vue-ts` 的含义：

```text
用 Vite 创建一个名为 frontend 的 Vue 3 + TypeScript 项目。
```

如果提示：

```text
Ok to proceed? (y)
```

输入：

```text
y
```

进入前端目录：

```powershell
cd frontend
```

安装依赖：

```powershell
npm install
```

含义：

```text
根据 package.json 下载前端项目运行所需依赖。
```

安装项目需要的额外库：

```powershell
npm install axios vue-router pinia ant-design-vue
```

含义：

```text
axios：发送 HTTP 请求
vue-router：前端路由
pinia：前端状态管理
ant-design-vue：Vue UI 组件库
```

启动前端：

```powershell
npm run dev
```

含义：

```text
启动 Vite 前端开发服务器。
```

看到类似：

```text
Local: http://localhost:5173/
```

浏览器打开：

```text
http://localhost:5173
```

看到 Vue 默认页面即可。

---

## 10. 配置前端请求工具

在前端项目中创建目录：

```text
frontend/src/api
```

在 `src/api` 下新建：

```text
request.ts
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
统一封装前端请求。
以后所有前端接口都通过 request 调用后端。
baseURL 指向后端地址 http://localhost:8123。
```

---

## 11. 修改 App.vue 测试前后端联调

打开：

```text
frontend/src/App.vue
```

全部替换为：

```vue
<script setup lang="ts">
import { ref } from 'vue'
import request from './api/request'

const message = ref('未连接后端')

const checkBackend = async () => {
  try {
    const res = await request.get('/api/health')
    message.value = res.data
  } catch (error) {
    console.error(error)
    message.value = '连接失败，请检查后端是否启动'
  }
}
</script>

<template>
  <div class="page">
    <h1>AI Article Studio</h1>
    <p>多智能体图文创作平台</p>

    <button @click="checkBackend">
      检查后端连接
    </button>

    <div class="result">
      后端状态：{{ message }}
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 48px;
  font-family: Arial, sans-serif;
}

h1 {
  margin-bottom: 8px;
}

button {
  margin-top: 24px;
  padding: 8px 16px;
  cursor: pointer;
}

.result {
  margin-top: 24px;
  font-size: 18px;
}
</style>
```

作用：

```text
页面上放一个按钮。
点击按钮时，请求后端 /api/health。
如果成功，页面显示 ok。
如果失败，页面显示连接失败。
```

浏览器访问：

```text
http://localhost:5173
```

点击按钮后应该显示：

```text
后端状态：ok
```

---

## 12. Day 1 最终验收清单

### 12.1 检查 Docker 容器

```powershell
docker ps
```

应该看到：

```text
ai_article_mysql
ai_article_redis
```

### 12.2 检查数据库表

```powershell
docker exec -it ai_article_mysql mysql -uroot -p123456
```

进入 MySQL 后执行：

```sql
USE ai_article_studio;
SHOW TABLES;
```

应该看到：

```text
article
agent_log
```

### 12.3 检查后端

浏览器访问：

```text
http://localhost:8123/api/health
```

应该显示：

```text
ok
```

### 12.4 检查前端

浏览器访问：

```text
http://localhost:5173
```

点击按钮后应该显示：

```text
后端状态：ok
```

---

## 13. Day 1 不要做的事

Day 1 只搭地基，不做复杂功能。

暂时不要做：

```text
不要接大模型
不要写 Agent
不要写 SSE
不要做文章生成页面
不要做登录注册
不要做支付
不要做图片生成
不要做页面美化
```

---

## 14. 常见问题

### 问题 1：Docker 拉不下来 MySQL / Redis

原因通常是 Docker Hub 网络问题。

解决方式：

```text
给 Docker Desktop 配代理，或换网络。
```

### 问题 2：后端启动失败，提示连不上 MySQL

先检查：

```powershell
docker ps
```

确认 `ai_article_mysql` 是否在运行。

再检查 `application.yml`：

```yaml
url: jdbc:mysql://localhost:13306/ai_article_studio
username: root
password: 123456
```

### 问题 3：前端请求后端失败

先访问：

```text
http://localhost:8123/api/health
```

如果浏览器能看到 `ok`，说明后端没问题。

再检查前端的：

```ts
baseURL: 'http://localhost:8123'
```

如果浏览器控制台提示 CORS，再检查 `CorsConfig.java` 是否写好。

### 问题 4：Spring Initializr 里为什么不选 Spring Boot 4.x？

因为 4.x 太新，很多依赖和教程还没有完全稳定。  
我们选 3.5.x，更适合学习和项目开发。

---

## 15. 完成 Day 1 后进入 Day 2

Day 2 要做：

```text
1. 创建 Article 实体类
2. 创建 ArticleMapper
3. 创建 ArticleService
4. 实现文章任务创建接口
5. 实现文章列表接口
6. 实现文章详情接口
7. 前端做文章任务创建和列表页面
```

Day 1 的核心意义是：

```text
先把环境、数据库、后端、前端全部打通。
后面所有业务功能都在这个基础上逐步增加。
```
