# NovaOps

NovaOps 是一个可直接运行的 Vue3 中后台实战项目，面向前端实习作品集，包含多租户、RBAC、工单、资产、知识库、数据看板等核心能力。项目默认使用 MSW Mock，无需后端即可完整演示分页、筛选、状态流转和权限控制。

## 技术栈

- Vue 3 + Vite + TypeScript
- UI: Ant Design Vue
- 状态: Pinia + pinia-plugin-persistedstate
- 路由: Vue Router（动态路由 addRoute）
- 请求: Axios（拦截器、错误处理、请求取消、401 刷新队列）
- 图表: ECharts
- Mock: MSW (Mock Service Worker)
- 工程化: ESLint + Prettier + Husky + lint-staged + commitlint
- 包管理器: npm

## 账号与登录

- `admin / 123456`
- `staff / 123456`
- `guest / 123456`

支持租户切换：

- `Tenant A`
- `Tenant B`

切换租户后会重新拉取 token、用户信息、菜单、权限和数据（Mock 按 tenantId 过滤）。

## 功能清单

### 1) 认证与权限

- 登录、退出、获取当前用户信息
- accessToken 注入 + 401 自动刷新 refreshToken（并发请求队列）
- 路由守卫：未登录跳转、无权限 403、登录态访问 login 重定向
- RBAC：`user -> roles -> permissions`
- 动态菜单：服务端菜单树 -> 动态路由 `addRoute`
- 按钮级权限：
  - 指令：`v-permission="'ticket:create'"`
  - 组件：`<Permission code="ticket:create">...</Permission>`

### 2) Layout

- 侧边栏菜单（可折叠）
- 顶部栏（面包屑、租户切换、用户下拉）
- 多标签页 Tabs（keep-alive）

### 3) 工单模块（核心）

- 列表页：筛选（状态/优先级/日期/关键字）+ 分页
- ProTable 封装：筛选区、表格、分页、空态、loading
- 列显隐配置（本地缓存）
- 操作：新建、编辑、指派、关闭（权限控制）
- 导出 CSV（前端生成）
- 详情页：
  - 描述信息 + 状态 Steps + 流转 Timeline
  - 评论列表/新增评论
  - 附件上传（Mock）
  - 状态流转：pending -> processing -> review -> done（支持 reject/transfer）

### 4) 资产模块

- 列表页：筛选（状态/类型/关键字）+ 分页
- 操作：入库、编辑、领用、回收、报废
- 详情页：资产信息 + 关联工单
- 关联联动：
  - 资产详情展示关联工单
  - 工单详情展示关联资产并可跳转资产详情

### 5) Dashboard 看板

- 时间范围筛选（近 7 天 / 近 30 天）
- 图表联动刷新：
  - 工单趋势折线图
  - 分类占比饼图
  - 平均处理时长条形图
- 概览指标卡片：工单总量、完成率、平均处理时长、SLA 达成率

### 6) 知识库

- 列表页：关键字搜索、标签筛选、分页
- Markdown 编辑 + 预览（`md-editor-v3`）
- 保存文章（新建/编辑）
- 版本历史（每篇至少 3 个快照）

## 权限模型说明

- 用户角色：
  - `admin`: 全量模块和操作权限
  - `staff`: 业务操作权限（部分受限）
  - `guest`: 只读权限（以看板为主）
- 权限码示例：
  - `ticket:view`、`ticket:create`、`ticket:edit`、`ticket:assign`
  - `asset:view`、`asset:create`、`asset:edit`、`asset:scrap`
  - `kb:view`、`kb:edit`
- 菜单与权限由接口返回，前端不硬编码角色菜单。

## 接口约定

- 通用响应：
  - `{ code: number, message: string, data: T }`
- 分页响应 `data`：
  - `{ list: T[], page: number, pageSize: number, total: number }`
- 业务码：
  - `code = 0` 成功
  - `code = 401` token 失效
  - `code = 403` 无权限

## 目录结构

```text
src/
  api/                # 分模块请求函数 + 类型
  assets/
  components/
    pro-table/
    pro-form/
  directives/
    permission.ts
  hooks/
  layout/
  router/
    staticRoutes.ts
    dynamicRoutes.ts
    guard.ts
  store/
    auth.ts
    app.ts
    permission.ts
  utils/
    request/
      index.ts
      types.ts
  views/
    login/
    dashboard/
    ticket/
    asset/
    kb/
  styles/
  types/
src/mocks/            # MSW handlers + mock db
```

## 快速开始

```bash
npm i
npm run dev
```

默认访问：

- `http://127.0.0.1:5173/login`

## 常用脚本

```bash
npm run dev        # 本地开发
npm run build      # 生产构建
npm run preview    # 预览构建产物
npm run lint       # ESLint 校验
npm run format     # Prettier 格式化
```

## 部署说明

1. 构建：

```bash
npm run build
```

2. 将 `dist/` 部署到静态服务器（Nginx/OSS/CDN）。

3. Nginx 示例（history 路由回退）：

```nginx
server {
  listen 80;
  server_name your-domain.com;

  root /var/www/novaops/dist;
  index index.html;

  location / {
    try_files $uri $uri/ /index.html;
  }
}
```

## 说明

- 开发环境默认启用 MSW，离线可演示业务流程。
- 如果接入真实后端，可逐步替换 `src/mocks` 对应接口，保持 `api` 调用层不变。
