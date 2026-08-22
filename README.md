# NovaOps

NovaOps 是一个基于 Vue3 + TypeScript 的中后台项目，包含登录鉴权、权限管理、工单、资产、知识库和数据看板等模块。
项目以常见中后台场景为核心，覆盖列表查询、状态流转、权限控制和可视化展示等能力。

当前仓库已经支持两种运行方式：

- 前端独立演示：通过 MSW Mock 跑完整前端流程
- 前后端联调：通过 `backend/` 下的 Java + MySQL 真实后端接管认证与工单模块

如果你准备直接把整套项目跑起来，先看这两份文档：

- [backend/STARTUP_GUIDE.md](/E:/LancerProjects/NovaOps/backend/STARTUP_GUIDE.md)
- [backend/KNOWN_ISSUES.md](/E:/LancerProjects/NovaOps/backend/KNOWN_ISSUES.md)

## 技术栈

- Vue 3 + Vite + TypeScript
- Ant Design Vue
- Pinia（含本地持久化）
- Vue Router（含动态路由）
- Axios
- ECharts
- MSW（Mock Service Worker）
- Spring Boot 3
- MyBatis
- MySQL 8
- ESLint + Prettier + Husky + lint-staged + commitlint

## 项目亮点

- 完整实现了登录、权限、菜单、业务模块、看板页面这一套中后台基本流程。
- 做了基础的 RBAC 权限模型（角色 + 权限码），支持页面级和按钮级权限控制。
- 抽离了通用表格/表单能力，提高了列表页开发效率。
- 保留了 Mock 与真实后端双模式切换，适合学习前后端联调和渐进式替换。

## 登录方式

登录采用「账号 + 身份」模式，账号不存在时系统会自动注册：

- 预置账号（密码统一为 `123456`）：
  - `admin / 123456` → 管理员
  - `staff / 123456` → 运维人员
  - `guest / 123456` → 访客
- 也可以输入任意新账号并选择一种身份（管理员 / 运维人员 / 访客），首次登录自动创建账号

支持租户切换：

- `Tenant A`
- `Tenant B`

## 功能模块

### 1. 认证与权限

- 登录、退出、获取用户信息
- 身份（角色）选择与账号自动注册（`GET /api/auth/roles`）
- 请求拦截与 token 注入
- 401 场景下的刷新 token 处理
- 路由守卫（未登录跳转、无权限拦截）
- RBAC：`user -> roles -> permissions`
- 动态菜单 + 动态路由 `addRoute`
- 按钮级权限控制（指令/组件）

### 2. 基础布局

- 侧边栏菜单（可折叠）
- 顶部栏（面包屑、租户切换、用户信息）
- 多标签页（keep-alive）

### 3. 工单模块

- 列表页：筛选、分页、关键字搜索
- 新建、编辑、指派、关闭等操作（按权限控制）
- 详情页：状态流转、评论、附件（Mock）
- CSV 导出（前端生成）

工单相关说明：

- 认证、权限、工单核心接口已支持真实后端
- 评论、附件也已支持真实后端元数据保存
- 资产、知识库、Dashboard 当前仍可通过 `partial` Mock 模式继续联调

### 4. 资产模块

- 列表筛选与分页
- 资产入库、领用、回收、报废等操作
- 资产和工单的关联展示与跳转

### 5. Dashboard 看板

- 时间范围筛选（近 7 天 / 近 30 天）
- 工单趋势、分类占比、处理时长等图表
- 总量、完成率等概览指标卡片

### 6. 知识库模块

- 关键字搜索、标签筛选、分页
- Markdown 编辑与预览
- 文章新建/编辑及版本历史

## 权限模型（简化说明）

- `admin`：全部功能
- `staff`：大部分业务操作
- `guest`：主要查看权限

权限码示例：

- `ticket:view`、`ticket:create`、`ticket:edit`
- `asset:view`、`asset:create`、`asset:edit`
- `kb:view`、`kb:edit`

## 目录结构

```text
backend/
  src/main/
  sql/
src/
  api/
  assets/
  components/
    pro-table/
    pro-form/
  directives/
    permission.ts
  hooks/
  layout/
  router/
  store/
  utils/
  views/
    login/
    dashboard/
    ticket/
    asset/
    kb/
  styles/
  types/
src/mocks/
```

## 快速开始

### 1. 只跑前端 Mock

```bash
npm install
npm run dev
```

默认地址：`http://127.0.0.1:5173/login`

默认 `.env.development` 使用：

```bash
VITE_API_BASE_URL=/api
VITE_ENABLE_MOCK=partial
```

`VITE_ENABLE_MOCK` 支持三种模式：

- `full`：全部接口走 MSW Mock
- `partial`：认证与工单走 Java 后端，资产/知识库/看板等未迁移接口继续走 Mock
- `off`：完全关闭 MSW，所有 `/api` 请求都走真实后端

### 2. 跑 Java + MySQL 真实后端

详细流程见：

- [backend/STARTUP_GUIDE.md](/E:/LancerProjects/NovaOps/backend/STARTUP_GUIDE.md)

简版步骤如下。

1. 创建数据库：

```sql
CREATE DATABASE novaops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

2. 执行初始化脚本：

```bash
mysql -uroot -p novaops < backend/sql/novaops_init.sql
```

如果你用的是 PowerShell，请不要直接用 `<`，改看启动指南里的 `SOURCE` 或管道方式。

3. 在 `backend/src/main/resources/application-local.yml` 里填好你本机 MySQL 的真实账号密码。

4. 启动后端：

```bash
cd backend
mvn -gs mvn-settings.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```

后端默认地址：`http://127.0.0.1:8080`

默认数据库配置可通过环境变量覆盖：

```bash
NOVAOPS_DB_URL
NOVAOPS_DB_USERNAME
NOVAOPS_DB_PASSWORD
NOVAOPS_JWT_SECRET
```

5. 启动前端：

```bash
npm install
npm run dev
```

Vite 开发服务器会自动把 `/api` 代理到 `http://127.0.0.1:8080`。

## 常用脚本

```bash
npm run dev
npm run build
npm run preview
npm run lint
npm run format
```

## 部署

1. 构建项目：

```bash
npm run build
```

2. 将 `dist/` 部署到静态服务器（如 Nginx）。

## 当前真实后端已覆盖的接口

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/switch-tenant`
- `GET /api/auth/roles`
- `GET /api/auth/me`
- `GET /api/auth/menu`
- `GET /api/tickets`
- `GET /api/tickets/:id`
- `POST /api/tickets`
- `PUT /api/tickets/:id`
- `POST /api/tickets/:id/actions`
- `GET /api/tickets/:id/comments`
- `POST /api/tickets/:id/comments`
- `POST /api/tickets/:id/attachments`

## 后续优化方向

- 接入资产、知识库、Dashboard 的真实后端接口
- 增加单元测试和 E2E 测试
- 完善异常监控和埋点统计
