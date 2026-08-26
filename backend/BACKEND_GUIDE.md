# NovaOps Backend 入门指南

这份文档是给“第一次接触真实 Java 后端项目”的你准备的。

目标不是一下子把所有 Spring Boot 和 MyBatis 都讲完，而是帮你先建立一个清楚的脑图：

1. 这个后端项目是什么
2. 我在这个 `backend/` 里做了什么
3. 这个项目是怎么跑起来的
4. 一个前端请求是怎么走到数据库再回来的
5. 你接下来应该先看哪里、先改哪里

如果你以前只写过前端或者只学过 Java 语法，这份文档可以当作你的第一份“真实项目地图”。

---

## 1. 先用一句话理解这个后端

这个 `backend/` 是一个基于 `Spring Boot + MyBatis + MySQL` 的真实后端服务。

它现在主要负责两件事：

- 认证与权限
- 工单模块

前端以前访问的是浏览器里的 Mock 数据，现在可以访问真正的 Java 服务和 MySQL 数据库了。

也就是说，以前的数据是“写死在前端代码里的”，现在的数据是“存在数据库里，由后端查询并返回的”。

---

## 2. 我在这个 backend 里做了什么

我做的事情可以分成 6 块：

### 2.1 搭了一个真正能启动的 Spring Boot 项目

核心文件：

- [pom.xml](/E:/LancerProjects/NovaOps/backend/pom.xml)
- [src/main/java/com/novaops/backend/NovaOpsBackendApplication.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/NovaOpsBackendApplication.java)
- [src/main/resources/application.yml](/E:/LancerProjects/NovaOps/backend/src/main/resources/application.yml)

这部分解决的是：

- 用 Maven 管理依赖
- 用 Spring Boot 启动 Web 服务
- 连接 MySQL
- 扫描 MyBatis Mapper
- 读取 JWT 配置

### 2.2 建了通用基础设施

放在 `common/` 下面，主要包括：

- 统一返回格式 `ApiResponse`
- 分页对象 `PageResult`
- 业务异常 `BusinessException`
- 全局异常处理 `GlobalExceptionHandler`
- JWT 生成与解析 `JwtService`
- 登录态上下文 `RequestContext`
- 鉴权拦截器 `AuthInterceptor`

这部分的作用是让后端“像一个真正项目”，而不是把逻辑全写在 Controller 里。

### 2.3 接了认证与权限接口

对应目录：

- `auth/controller`
- `auth/service`
- `auth/mapper`
- `auth/dto`
- `auth/model`

已经实现的接口：

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/register`
- `GET /api/auth/verify`
- `GET /api/auth/me`
- `GET /api/auth/menu`

### 2.4 接了工单模块真实接口

对应目录：

- `ticket/controller`
- `ticket/service`
- `ticket/mapper`
- `ticket/dto`
- `ticket/model`

已经实现的接口：

- `GET /api/tickets`
- `GET /api/tickets/{id}`
- `POST /api/tickets`
- `PUT /api/tickets/{id}`
- `POST /api/tickets/{id}/actions`
- `GET /api/tickets/{id}/comments`
- `POST /api/tickets/{id}/comments`
- `POST /api/tickets/{id}/attachments`

### 2.5 建了数据库初始化脚本

核心文件：

- [sql/novaops_init.sql](/E:/LancerProjects/NovaOps/backend/sql/novaops_init.sql)

它做了这些事：

- 创建系统表
- 创建工单表
- 插入演示账号
- 插入权限、菜单
- 插入示例工单、评论、附件、时间线

你导入这个 SQL 之后，项目就不是“空库启动”，而是可以直接登录和演示。

### 2.6 给这个项目配了一个备用 Maven settings

文件：

- [mvn-settings.xml](/E:/LancerProjects/NovaOps/backend/mvn-settings.xml)

原因是你本机全局 Maven 配置有解析问题，所以我加了一个项目内可用的 settings，避免你被本地环境卡住。

---

## 3. 这个项目用了哪些技术

从“你现在最需要理解”的角度，可以这样记：

### 3.1 Spring Boot

它负责：

- 启动项目
- 开 HTTP 服务
- 管理对象依赖
- 让你可以用注解快速写接口

你可以把它理解成：

“帮我们把 Java 项目从一个普通程序，变成一个 Web 后端服务。”

### 3.2 MyBatis

它负责：

- 把 Java 方法和 SQL 连接起来
- 执行 SQL
- 把查询结果映射成 Java 对象

你可以把它理解成：

“比 JDBC 更方便，但 SQL 仍然是你自己写，所以很适合学习数据库和后端。”

### 3.3 MySQL

它负责真正存数据。

比如：

- 用户
- 权限
- 菜单
- 工单
- 工单时间线
- 评论
- 附件

### 3.4 JWT

它负责生成 `accessToken`。

前端登录成功后拿到 token，之后访问接口时把 token 放在请求头里：

```http
Authorization: Bearer xxxxx
```

后端解析 token，知道“你是谁”。

### 3.5 BCrypt

它负责保存密码的哈希值，而不是明文密码。

这就是为什么数据库里不会直接存 `123456`，而是存一串加密后的值。

---

## 4. 目录怎么读

先看 `src/main/java/com/novaops/backend/`。

你可以把目录理解成下面这样：

### 4.1 `common/`

这里放“所有模块都会用到的通用能力”。

例如：

- `api/`：统一响应格式
- `config/`：配置类
- `exception/`：异常定义和统一处理
- `security/`：登录鉴权、JWT、请求上下文
- `util/`：工具类

### 4.2 `auth/`

这里放认证和权限模块。

### 4.3 `ticket/`

这里放工单模块。

### 4.4 `resources/mapper/`

这里放 MyBatis 的 XML SQL 文件。

你要记住一件很重要的事：

- `Mapper.java` 里定义“我要有哪些数据库方法”
- `Mapper.xml` 里写“这些方法到底执行什么 SQL”

### 4.5 `sql/`

这里放数据库初始化脚本。

---

## 5. 一个真实后端请求是怎么跑的

这是你最应该建立起来的核心概念。

以“前端登录”为例：

1. 前端发送 `POST /api/auth/login`
2. `AuthController` 接收请求
3. `AuthController` 把请求交给 `AuthService`
4. `AuthService` 调 `AuthMapper` 去数据库查用户
5. `AuthMapper.xml` 里的 SQL 被执行
6. 查到用户后，`AuthService` 校验密码
7. 校验通过后，`AuthService` 生成 `accessToken` 和 `refreshToken`
8. `AuthController` 把结果包装成 `{ code, message, data }` 返回给前端

以“查询工单列表”为例：

1. 前端发送 `GET /api/tickets?page=1&pageSize=10`
2. 请求先经过 `AuthInterceptor`
3. `AuthInterceptor` 解析请求头里的 JWT
4. 解析出的登录态保存到 `RequestContext`
5. `TicketController` 拿到查询参数
6. `TicketController` 调用 `TicketService.list(...)`
7. `TicketService` 调 `TicketMapper` 查询工单主表
8. `TicketService` 再查工单和资产的关联关系
9. `TicketService` 组装成前端需要的 DTO
10. 返回分页数据给前端

这就是一个最典型的后端分层流程：

`Controller -> Service -> Mapper -> SQL -> DB -> Service -> Controller -> JSON`

---

## 6. 分层到底是什么意思

真实项目一般不会把所有逻辑写在一个文件里，因为那样很快会乱。

所以这里做了分层。

### 6.1 Controller 层

职责很简单：

- 接 URL
- 接参数
- 调 Service
- 返回结果

它不应该写太多业务逻辑。

比如：

- [auth/controller/AuthController.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/auth/controller/AuthController.java)
- [ticket/controller/TicketController.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/ticket/controller/TicketController.java)

### 6.2 Service 层

职责是：

- 写业务规则
- 组合多个数据库操作
- 做状态流转
- 做数据组装

比如：

- 登录成功后生成 token
- refresh 时轮换 access token 与 refresh token
- 新建工单时自动给状态 `pending`
- 工单 `advance` 时按 `pending -> processing -> review -> done` 推进

核心文件：

- [auth/service/AuthService.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/auth/service/AuthService.java)
- [ticket/service/TicketService.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/ticket/service/TicketService.java)

### 6.3 Mapper 层

职责是：

- 和数据库交互
- 执行 SQL

核心文件：

- [auth/mapper/AuthMapper.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/auth/mapper/AuthMapper.java)
- [ticket/mapper/TicketMapper.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/ticket/mapper/TicketMapper.java)
- [resources/mapper/AuthMapper.xml](/E:/LancerProjects/NovaOps/backend/src/main/resources/mapper/AuthMapper.xml)
- [resources/mapper/TicketMapper.xml](/E:/LancerProjects/NovaOps/backend/src/main/resources/mapper/TicketMapper.xml)

### 6.4 DTO 和 Model 的区别

这里你很容易混。

可以先这样理解：

- `dto/`：给接口收参数或返回结果用的
- `model/`：更贴近数据库表结构

例如：

- `LoginRequest` 是前端发来的参数
- `UserRecord` 是数据库里查出来的用户记录
- `TicketDetailResponse` 是要返回给前端的工单详情

---

## 7. 认证模块你应该怎么理解

认证模块最重要的是理解“谁负责什么”。

### 7.1 登录发生了什么

在 `AuthService.login()` 里：

1. 根据用户名查数据库
2. 校验账号已激活
3. 用 `BCrypt` 校验密码
4. 生成 refresh token 并存到数据库
5. 生成 access token
6. 返回给前端

### 7.2 为什么有 access token 和 refresh token

可以这样理解：

- `accessToken`：短期通行证，接口访问时带上它
- `refreshToken`：续期凭证，access token 过期时用它换新的

在这个项目里：

- `accessToken` 是 JWT
- `refreshToken` 存在 `sys_refresh_token` 表里

### 7.3 为什么 `me` 和 `menu` 不需要前端再传 userId

因为后端已经从 token 里知道当前登录的是谁了。

也就是说：

- 你是谁
- 你属于什么身份

这些信息都在 JWT 或请求上下文里。

### 7.4 `RequestContext` 是干嘛的

`RequestContext` 是一个“当前请求的登录态暂存区”。

流程是：

1. `AuthInterceptor` 解析 JWT
2. 解析出用户信息
3. 放进 `RequestContext`
4. Controller 或 Service 再去 `RequestContext.getRequired()` 取当前登录人

这样业务代码就不用每个接口都手动解析 token 了。

---

## 8. 工单模块你应该怎么理解

工单模块比认证模块更像“业务代码”。

### 8.1 工单主表是什么

核心表是：

- `biz_ticket`

它存的是工单本体：

- 标题
- 描述
- 状态
- 优先级
- 负责人
- 创建人
- 创建时间
- 更新时间

### 8.2 为什么还有时间线表

因为工单不只是“当前状态”，还需要知道“经历过什么变化”。

所以用了：

- `biz_ticket_timeline`

这里会记录：

- 谁做了操作
- 做了什么操作
- 状态从哪里变到哪里
- 什么时间发生的

### 8.3 为什么还有关联表

因为一个工单可能关联多个资产，所以用了：

- `biz_ticket_asset_rel`

这叫“多对多或一对多关联”的常见数据库设计思路之一。

### 8.4 工单创建时发生了什么

`TicketService.create()` 做了这些事：

1. 生成工单号
2. 写入工单主表
3. 默认状态设成 `pending`
4. 保存关联资产
5. 写入一条 `create` 的时间线
6. 再查询完整详情返回给前端

### 8.5 工单流转时发生了什么

`TicketService.action()` 负责工单动作：

- `assign`
- `transfer`
- `reject`
- `close`
- `advance`

核心不是“能调一个接口”，而是“状态变化规则写在 Service 里”。

这就是后端业务逻辑的典型写法。

比如：

- `assign`：更新负责人，必要时把 `pending` 推到 `processing`
- `advance`：按固定流程推进
- `close`：直接变成 `done`

然后每次都会：

- 更新工单主表
- 写时间线

---

## 9. SQL 在这个项目里扮演什么角色

你已经学了 MySQL，所以这一部分会是你非常重要的优势。

在这个项目里，SQL 是真实业务的一部分，不是边角料。

例如工单列表查询：

- 需要分页
- 需要状态筛选
- 需要优先级筛选
- 需要关键字搜索
- 需要日期区间筛选
- 需要按更新时间倒序

这些逻辑最终都落在 `TicketMapper.xml` 里。

也就是说：

- Java 负责组织流程
- SQL 负责把数据查对

这也是为什么我给你选 `MyBatis` 而不是直接选更“黑盒”的 ORM。

因为你现在最需要建立的是：

“我写的 SQL 是如何为业务服务的。”

---

## 10. 配置文件怎么理解

看这个文件：

- [src/main/resources/application.yml](/E:/LancerProjects/NovaOps/backend/src/main/resources/application.yml)

你重点先看 4 块：

### 10.1 `server.port`

```yml
server:
  port: 8080
```

表示后端运行在 `8080` 端口。

### 10.2 数据库配置

```yml
spring:
  datasource:
    url: ...
    username: ...
    password: ...
```

这就是连接 MySQL 的地方。

### 10.3 MyBatis 配置

```yml
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

意思是：

- 去 `resources/mapper/` 下找 XML
- 数据库字段下划线风格，自动映射到 Java 驼峰字段

比如：

- `created_at -> createdAt`
- `updated_at -> updatedAt`

### 10.4 安全配置

```yml
app:
  security:
    jwt-secret: ...
```

这里是 JWT 的密钥和过期时间配置。

---

## 11. 数据库脚本怎么理解

看这个文件：

- [sql/novaops_init.sql](/E:/LancerProjects/NovaOps/backend/sql/novaops_init.sql)

建议你第一次阅读时按这个顺序看：

1. 先看 `create table`
2. 再看 `insert into sys_*`
3. 最后看 `insert into biz_*`

为什么这样看：

- `sys_*` 一般是系统表
- `biz_*` 一般是业务表

这个项目里大致是：

- `sys_`：用户、角色、权限、菜单、refresh token、邮箱验证
- `biz_`：工单、时间线、评论、附件、资产关联

---

## 12. 你现在该怎么启动它

### 12.1 建库

先在 MySQL 里执行：

```sql
CREATE DATABASE novaops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 12.2 导入初始化 SQL

```bash
mysql -uroot -p novaops < backend/sql/novaops_init.sql
```

### 12.3 启动后端

正常情况下：

```bash
cd backend
mvn spring-boot:run
```

如果你本机 Maven 全局 settings 有问题，就用我放在项目里的备用配置：

```bash
cd backend
mvn -gs mvn-settings.xml spring-boot:run
```

### 12.4 前端联调

前端开发服务器会把 `/api` 代理到 `http://127.0.0.1:8080`。

所以当前端调用：

```text
/api/auth/login
```

实际上就会打到这个 Java 后端。

---

## 13. 如果你想调试，先看哪里

对初学者来说，最好的调试方式不是一上来乱改，而是“顺着一条请求走一遍”。

我建议你按这个顺序看：

### 路线 1：登录流程

1. `AuthController.login()`
2. `AuthService.login()`
3. `AuthMapper.findUserByUsername()`
4. `AuthMapper.xml` 对应 SQL
5. `JwtService.createAccessToken()`

### 路线 2：工单列表流程

1. `TicketController.list()`
2. `TicketService.list()`
3. `TicketMapper.countTickets()`
4. `TicketMapper.queryTickets()`
5. `TicketMapper.xml` 中的筛选 SQL

### 路线 3：工单流转流程

1. `TicketController.action()`
2. `TicketService.action()`
3. 看 `switch (request.getAction())`
4. 看更新主表和写时间线的逻辑

---

## 14. 如果你想自己改功能，先改哪里最安全

这里给你一个非常实用的建议：

### 最适合入门的第一个改动

给工单增加一个新筛选条件，或者给工单详情增加一个新字段。

原因是它能让你同时练到：

- 前端参数传递
- Controller 接参数
- Service 处理逻辑
- Mapper 写 SQL
- 数据库字段映射

### 比如你可以试这些练习

1. 给工单列表增加“负责人筛选”
2. 给工单详情增加“最后操作人”
3. 给工单创建增加“来源渠道”
4. 给用户资料接口增加“手机号”

这些改动都不算太危险，但很能帮你建立完整链路理解。

---

## 15. 你暂时不用纠结的地方

刚入门时，有些点先知道名字就够了，不用一次学透。

比如：

- 为什么 Spring Boot 自动装配这么方便
- `@ConfigurationProperties` 底层怎么绑定配置
- MyBatis 的 XML 动态 SQL 所有高级写法
- JWT 的所有安全细节
- 为什么 ThreadLocal 能保存请求上下文

这些以后都会慢慢懂，现在先不要让它们阻碍你理解项目主流程。

你当前最重要的是先弄清楚：

- 请求怎么进来
- 数据怎么查出来
- 业务规则写在哪
- 响应怎么回去

---

## 16. 这个项目的“阅读顺序”建议

如果你想从 0 到 1 读懂它，我建议按这个顺序：

1. 先读 [pom.xml](/E:/LancerProjects/NovaOps/backend/pom.xml)
2. 再读 [src/main/resources/application.yml](/E:/LancerProjects/NovaOps/backend/src/main/resources/application.yml)
3. 再读 [src/main/java/com/novaops/backend/NovaOpsBackendApplication.java](/E:/LancerProjects/NovaOps/backend/src/main/java/com/novaops/backend/NovaOpsBackendApplication.java)
4. 再读 `common/api`、`common/security`
5. 再读 `auth/controller -> auth/service -> auth/mapper -> AuthMapper.xml`
6. 再读 `ticket/controller -> ticket/service -> ticket/mapper -> TicketMapper.xml`
7. 最后读 [sql/novaops_init.sql](/E:/LancerProjects/NovaOps/backend/sql/novaops_init.sql)

这个顺序的好处是：

- 先知道“项目怎么启动”
- 再知道“请求怎么鉴权”
- 再知道“业务怎么写”
- 最后知道“数据库怎么支撑业务”

---

## 17. 你现在可以把这套后端理解成什么

可以把它理解成一个“很标准的第一套真实 Java 后端样板”。

它已经包含了真实项目里最关键的几件事：

- 配置
- 启动
- 路由接口
- 参数校验
- 统一响应
- 异常处理
- 登录鉴权
- 数据库查询
- 业务规则

所以你接下来不是“从头学一个抽象框架”，而是可以直接围绕这套能跑的项目，一边看一边改。

这会比单独看教程更快建立真实感。

---

## 18. 给你的下一步建议

如果你要继续往下学，我建议你按这个节奏来：

1. 先把后端独立跑起来，确保你能登录
2. 自己打一次 `/api/auth/login` 和 `/api/tickets`
3. 自己在数据库里改一条工单，再刷新前端看变化
4. 自己加一个简单字段或筛选条件
5. 再开始接资产模块

如果你愿意，我下一步可以继续帮你做两种事情里的任意一种：

- 带你逐文件讲解这套后端，像结对编程一样一层层看
- 直接带你做第一个“练手改动”，比如给工单列表加一个“负责人筛选”

