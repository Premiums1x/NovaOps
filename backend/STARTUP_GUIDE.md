# NovaOps 启动联调指南

这份文档专门解决一件事：

把 `NovaOps` 从“代码放在本地”变成“前端、后端、MySQL 都真正跑起来，并且能联调”。

如果你是第一次跑这个项目，建议就按这份文档的顺序走，不要自己跳步骤。

## 1. 这套项目现在是什么结构

当前仓库已经不是纯前端项目了，而是：

- 前端：`Vue 3 + Vite + TypeScript`
- 后端：`Spring Boot 3 + MyBatis`
- 数据库：`MySQL 8`

第一期已经完成真实后端接管的部分：

- 认证鉴权
- 菜单与权限
- 工单核心流程
- 工单评论与附件元数据

还没有迁到真实后端的部分：

- 资产模块
- Dashboard
- 知识库

所以当前推荐的联调方式是：

- 前端使用 `partial` 模式
- 认证和工单走 Java 后端
- 资产、Dashboard、知识库继续走 Mock

## 2. 运行前你需要准备什么

建议本机先确认这些工具可用：

- `Node.js 18+`
- `npm`
- `Java 17`
- `Maven 3.9+`
- `MySQL 8`

如果你不确定版本，可以先在终端执行：

```powershell
node -v
npm -v
java -version
mvn -v
mysql --version
```

## 3. 第一步：确认 MySQL 服务已经启动

这个项目的真实数据放在 MySQL 里，所以后端启动前，数据库服务必须先是运行状态。

如果你是自己装的 MySQL，一般有两种常见情况：

1. 作为 Windows 服务启动
2. 通过 MySQL Workbench / 命令行手动启动

你只要确保自己能成功进入 mysql 即可，例如：

```powershell
mysql -uroot -p
```

输入你的数据库密码后能进入 mysql，说明数据库服务是好的。

## 4. 第二步：创建数据库

进入 mysql 后，先创建项目数据库：

```sql
CREATE DATABASE novaops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

查看是否创建成功：

```sql
SHOW DATABASES;
```

如果列表里看到了 `novaops`，这一步就完成了。

## 5. 第三步：执行初始化 SQL

初始化脚本位置：

- [backend/sql/novaops_init.sql](/E:/LancerProjects/NovaOps/backend/sql/novaops_init.sql)

它会完成这些事：

- 建系统表
- 建工单相关表
- 初始化演示账号
- 初始化租户、角色、权限、菜单
- 初始化示例工单、评论、附件、时间线

### 5.1 在 CMD 中执行

如果你打开的是 `cmd`，可以直接执行：

```cmd
mysql -uroot -p novaops < E:\LancerProjects\NovaOps\backend\sql\novaops_init.sql
```

### 5.2 在 PowerShell 中执行

你之前已经踩过这个坑了：

- PowerShell 不支持直接用 `<` 做这种 mysql 输入重定向

所以在 PowerShell 中，推荐你用下面两种方式之一。

方式 A：进入 mysql 后执行 `source`

```powershell
mysql -uroot -p
```

进入 mysql 后再执行：

```sql
USE novaops;
SOURCE E:/LancerProjects/NovaOps/backend/sql/novaops_init.sql;
```

方式 B：用管道导入

```powershell
Get-Content E:\LancerProjects\NovaOps\backend\sql\novaops_init.sql | mysql -uroot -p novaops
```

### 5.3 怎么确认脚本执行成功

导入后可以执行：

```sql
USE novaops;
SHOW TABLES;
SELECT username FROM sys_user;
SELECT id, title FROM biz_ticket LIMIT 5;
```

如果这些 SQL 都能查出结果，说明数据库初始化没问题。

## 6. 第四步：配置后端本地数据库账号密码

这是这次真实后端接入里最关键、也最容易出问题的一步。

项目里的公共配置文件是：

- [backend/src/main/resources/application.yml](/E:/LancerProjects/NovaOps/backend/src/main/resources/application.yml)

它的默认数据库配置是：

- 用户名：`root`
- 密码：`root`

这只是一个“公共默认值”，不是你机器上的真实密码。

如果你的 MySQL 密码不是 `root`，后端虽然可能能启动到 Tomcat ready，但第一次真正访问数据库时，接口就会报：

- `服务异常，请稍后重试`

### 正确做法

项目里已经给你预留了本地开发配置文件：

- `backend/src/main/resources/application-local.yml`

你需要把这里面的数据库账号密码改成你自己机器上的真实值。

例如：

```yml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/novaops?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8
    username: root
    password: 这里改成你自己的真实密码

app:
  security:
    jwt-secret: change-this-secret-for-local-dev-please
    access-token-expire-seconds: 1800
    refresh-token-expire-days: 7
```

注意：

- `application-local.yml` 是本地私密配置，已经加入 `.gitignore`
- 不要把你自己的真实密码提交到仓库

## 7. 第五步：启动后端

后端目录：

- `E:\LancerProjects\NovaOps\backend`

推荐启动命令：

```powershell
cd E:\LancerProjects\NovaOps\backend
mvn -gs mvn-settings.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```

这条命令有两个关键点：

1. `-gs mvn-settings.xml`
   作用：避免你本机全局 Maven settings 配置异常时影响项目启动

2. `-Dspring-boot.run.profiles=local`
   作用：强制加载 `application-local.yml`

### 为什么推荐这条，而不是直接 `mvn spring-boot:run`

因为你本机之前已经遇到过这两个实际问题：

- 全局 Maven 配置可能干扰项目
- 数据库密码不等于 `root`

所以当前最稳的方式，就是显式指定项目内 settings，并显式启用 `local` profile。

### 启动成功的标志

日志里看到类似内容：

```text
Tomcat started on port 8080 (http)
Started NovaOpsBackendApplication
```

说明后端已经启动成功，默认地址是：

- `http://127.0.0.1:8080`

## 8. 第六步：验证后端是否真的可用

不要只看“Tomcat 启动成功”，最好马上验证一次登录接口。

在 PowerShell 里执行：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"123456","tenantId":"tenant-a"}'
```

如果返回里有：

- `code = 0`
- `accessToken`
- `refreshToken`

说明后端、数据库、账号密码、JWT 这一整条链路都已经通了。

## 9. 第七步：启动前端

回到项目根目录：

```powershell
cd E:\LancerProjects\NovaOps
npm install
npm run dev
```

前端开发地址一般是：

- `http://127.0.0.1:5173/login`

## 10. 第八步：确认前端现在走的是哪种模式

前端当前用环境变量控制 Mock 模式，配置文件是：

- [/.env.development](/E:/LancerProjects/NovaOps/.env.development)

当前推荐值：

```env
VITE_API_BASE_URL=/api
VITE_ENABLE_MOCK=partial
```

三种模式的区别：

- `full`：全部接口走 Mock
- `partial`：认证和工单走真实后端，其余未迁移模块继续 Mock
- `off`：完全关闭 Mock，全部接口都走真实后端

现在第一期联调，推荐你用：

- `VITE_ENABLE_MOCK=partial`

## 11. 第九步：前后端联调时，数据是怎么流动的

当前联调链路可以这样理解：

1. 浏览器访问前端页面
2. 前端调用 `/api/*`
3. Vite 开发代理把 `/api` 转发到 `http://127.0.0.1:8080`
4. 已接管的认证/工单接口由 Java 后端返回真实数据
5. 未接管的资产/Dashboard/知识库接口继续由 MSW Mock 返回

所以这是一个“真实后端 + 局部 Mock 共存”的阶段性结构。

## 12. 推荐你怎么验证整套流程

第一次联调，建议你按这个顺序测：

### 12.1 登录

测试账号：

- `admin / 123456`
- `staff / 123456`
- `guest / 123456`

租户：

- `tenant-a`
- `tenant-b`

### 12.2 看菜单是否正常

登录后确认：

- 菜单能正常出来
- 不同角色菜单不同
- 租户切换后菜单会变化

### 12.3 测工单列表

确认：

- 列表能正常展示
- 分页正常
- 筛选正常
- 搜索正常

### 12.4 测工单详情

确认：

- 能点开详情
- 时间线能显示
- 评论能显示
- 附件列表能显示

### 12.5 测资产详情

确认：

- 能点开资产详情
- 关联工单能跳转

注意：

- 资产详情目前仍然是 Mock 数据，不是数据库真实资产数据

## 13. 如果某一步失败，优先排查什么

### 13.1 后端启动了，但登录接口报 500

优先检查：

- `application-local.yml` 里的用户名密码是否和真实 MySQL 一致
- `spring-boot.run.profiles=local` 是否真的带上了
- `novaops` 数据库是否已经初始化

### 13.2 PowerShell 执行 SQL 脚本时报 `<` 语法错误

原因：

- PowerShell 不支持这种 mysql 输入重定向语法

做法：

- 改用 `source`
- 或者改用 `Get-Content ... | mysql ...`
- 或者直接换 `cmd`

### 13.3 前端登录成功，但 Dashboard 一直转圈

这通常不是后端挂了，而是：

- 当前页面还走剩余 Mock
- Mock 与真实后端 JWT 的兼容有问题

现在代码里已经做了兼容层，如果你改完相关代码后还看到旧现象，先：

1. 重启前端 `npm run dev`
2. 浏览器强制刷新一次

### 13.4 详情页报“服务异常”

先看它属于哪类接口：

- 工单详情：真实后端
- 资产详情：当前仍是 Mock

这类问题很多时候是“混合模式接口边界”问题，不一定是 Java 接口本身挂了。

## 14. 当前最推荐的启动顺序

每次开发时，建议固定按这个顺序来：

1. 启动 MySQL
2. 确认 `application-local.yml` 里的数据库密码是对的
3. 启动后端
4. 用登录接口测一次后端
5. 启动前端
6. 浏览器登录联调

这样出问题时最好定位。

## 15. 提交代码前你应该确认什么

提交前建议检查这几件事：

- `backend/src/main/resources/application-local.yml` 没有被提交
- `.env.local`、`.env.*.local` 没有被提交
- `backend/target/` 没有被提交
- 前端能登录
- 工单列表和详情能打开
- 资产详情至少能正常打开

## 16. 你接下来可以重点看哪几份文档

如果你想继续理解后端本身，优先看：

- [backend/BACKEND_GUIDE.md](/E:/LancerProjects/NovaOps/backend/BACKEND_GUIDE.md)

如果你想看这次联调里踩过的坑和已知限制，再看：

- [backend/KNOWN_ISSUES.md](/E:/LancerProjects/NovaOps/backend/KNOWN_ISSUES.md)

这两份文档和本文件的关系可以理解成：

- `STARTUP_GUIDE.md`：教你跑起来
- `BACKEND_GUIDE.md`：教你理解后端结构
- `KNOWN_ISSUES.md`：教你避坑
