# NovaOps 真实后端接入过程中的问题记录

这份文档记录的是两类内容：

- 这次把 NovaOps 从 Mock 接到真实 Java + MySQL 后端时，实际踩到的问题
- 当前版本已经修复的问题，以及还没有彻底解决的阶段性限制

你以后继续做第二期、第三期时，这份文档会很有用，因为很多坑不会只出现一次。

## 1. 已修复问题

### 1.1 PowerShell 下用 `<` 导入 SQL 会直接报语法错误

现象：

```powershell
mysql -uroot -p novaops < E:\LancerProjects\NovaOps\backend\sql\novaops_init.sql
```

会报：

- `The '<' operator is reserved for future use.`

原因：

- 这是 PowerShell 语法限制，不是 MySQL 脚本坏了
- PowerShell 不能像 `cmd` 一样直接把 `<` 当成 mysql 输入重定向

解决：

- 改成 `cmd` 执行
- 或者进入 mysql 后使用 `SOURCE`
- 或者用 `Get-Content ... | mysql ...`

状态：

- 已定位
- 已写入启动指南

### 1.2 后端启动看起来成功，但第一次调接口就报“服务异常”

现象：

- Spring Boot 日志里显示 Tomcat 已启动
- 但前端登录时报：
  - `{"code":500,"message":"服务异常，请稍后重试"}`

原因：

- `application.yml` 里的数据库默认值是 `root/root`
- 用户本机 MySQL 密码并不是 `root`
- Hikari 初始化阶段未必立刻把这个问题暴露出来
- 真正访问数据库时才报错

解决：

- 新增本地专用配置：
  - `backend/src/main/resources/application-local.yml`
- 启动后端时显式带上：

```powershell
mvn -gs mvn-settings.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```

状态：

- 已修复

### 1.3 Maven 全局 settings 可能干扰项目启动

现象：

- 使用机器全局 Maven 配置时，可能出现解析或仓库配置异常

解决：

- 在 `backend/` 里补了一份项目内 `mvn-settings.xml`
- 启动和打包时优先使用：

```powershell
mvn -gs mvn-settings.xml ...
```

状态：

- 已绕过

### 1.4 工单列表编辑弹窗描述字段回填错误

现象：

- 从工单列表点“编辑”时，表单描述字段回填不正确

原因：

- 编辑弹窗最初是直接拿列表项数据拼表单
- 列表数据不等于完整详情数据

解决：

- 改成打开编辑弹窗时先请求工单详情接口
- 用详情数据回填表单

状态：

- 已修复

### 1.5 `partial` 模式下，Dashboard 一直加载

现象：

- 登录后能进系统
- 但 Dashboard 一直在 loading

原因：

- 当前是“认证和工单走真实后端，其余模块继续 Mock”的混合模式
- 剩余 mock 模块最初只认识 mock 自己签发的 token
- 当登录改成真实后端后，Dashboard 还在走 Mock，但它拿到的是后端 JWT

解决：

- 在 mock 侧补了对后端 JWT 的兼容解析
- 后来又进一步补了请求头兼容，避免只依赖 Authorization

状态：

- 已修复

### 1.6 点击工单详情、资产详情时报“服务异常”

现象：

- 点击工单详情或资产详情时会报服务异常

原因：

- 工单详情页会额外请求资产批量接口
- 资产详情页本身也还在走剩余 Mock
- 混合模式下，真实后端 token 与剩余 mock 的会话识别不完全兼容

解决：

- 在前端请求层额外附带：
  - `X-NovaOps-Tenant-Id`
  - `X-NovaOps-Username`
- 在剩余 Mock 接口里优先用这些头恢复会话
- 同时保留 JWT 解析兜底

状态：

- 已修复

### 1.7 修改了 MSW 逻辑后，浏览器不一定立刻恢复正常

现象：

- 代码已经改对了
- 但浏览器还在表现旧问题

原因：

- `MSW + Vite dev server + 浏览器缓存` 组合下，service worker 和 bundle 有时不会同步更新到预期状态

解决：

- 重启 `npm run dev`
- 浏览器强制刷新

状态：

- 这是工具链行为，不是业务 bug
- 目前已有应对经验

## 2. 当前版本的阶段性限制

### 2.1 资产模块还没有接真实后端

当前情况：

- 资产列表、资产详情、资产操作，仍然是 Mock

影响：

- 工单里保存的 `assetIds` 已经入库
- 但资产本体数据还不是从 MySQL 查出来的

后续建议：

- 第二期接真实资产表和资产接口

### 2.2 Dashboard 还没有接真实后端

当前情况：

- Dashboard 指标和图表数据仍然由 Mock 生成

影响：

- 现在可以演示页面流程
- 但不是数据库真实统计

### 2.3 知识库还没有接真实后端

当前情况：

- 知识库列表、详情、编辑、版本仍然是 Mock

影响：

- 可以展示交互
- 但没有真正落库

### 2.4 附件上传目前只是“元数据真实化”

当前情况：

- 附件接口已经能写数据库
- 但没有真实文件上传与存储

影响：

- 现在保存的是附件名称、大小、URL
- 不是把文件实体传到对象存储或本地目录

后续建议：

- 第二期再接 multipart 上传
- 再决定存本地、MinIO、OSS 或其他方案

### 2.5 refresh token 当前放在 MySQL

当前情况：

- 为了降低学习成本，refresh token 暂时存在 MySQL

影响：

- 本地开发足够
- 但高并发和更复杂的 token 生命周期管理场景下，不如 Redis 灵活

后续建议：

- 等第二阶段再考虑迁 Redis

## 3. 容易再次踩坑的点

### 3.1 看见“后端启动成功”不等于数据库一定连通

建议：

- 每次启动后端后，立刻手动调一次登录接口

### 3.2 Mock 和真实后端混跑时，不要默认以为“都是后端问题”

建议：

- 先判断当前接口到底走的是：
  - Java 后端
  - 还是剩余 Mock

### 3.3 本地配置文件一定不要提交

最需要注意的文件：

- `backend/src/main/resources/application-local.yml`
- `.env.local`
- `.env.*.local`

现在这些都应该被 `.gitignore` 忽略。

## 4. 这次接入后端带来的实际收获

虽然踩了不少坑，但这些坑其实很有价值。它们说明项目已经从“纯静态演示”进入“真实工程联调”阶段了。

这次最重要的收获有这些：

- 前端和后端接口契约已经真正跑通
- 数据库初始化流程已经固定下来
- 本地开发配置和公共配置已经分离
- 混合模式的联调边界已经更清晰
- 已经积累出一套可复用的排障经验

这也意味着：

后面接资产、知识库、Dashboard 时，会比第一期顺很多，因为“真实后端怎么接进前端项目”这件事已经走通了。
