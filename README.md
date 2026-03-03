# NovaOps

NovaOps 是一个基于 Vue3 + TypeScript 的中后台项目，包含登录鉴权、权限管理、工单、资产、知识库和数据看板等模块。
项目以常见中后台场景为核心，覆盖列表查询、状态流转、权限控制和可视化展示等能力。

项目默认使用 MSW Mock，不依赖真实后端也能演示主要业务流程。

## 技术栈

- Vue 3 + Vite + TypeScript
- Ant Design Vue
- Pinia（含本地持久化）
- Vue Router（含动态路由）
- Axios
- ECharts
- MSW（Mock Service Worker）
- ESLint + Prettier + Husky + lint-staged + commitlint

## 项目亮点

- 完整实现了登录、权限、菜单、业务模块、看板页面这一套中后台基本流程。
- 做了基础的 RBAC 权限模型（角色 + 权限码），支持页面级和按钮级权限控制。
- 抽离了通用表格/表单能力，提高了列表页开发效率。
- 使用 Mock 数据独立完成前端联调，后续可平滑替换为真实接口。

## 演示账号

- `admin / 123456`
- `staff / 123456`
- `guest / 123456`

支持租户切换：

- `Tenant A`
- `Tenant B`

## 功能模块

### 1. 认证与权限

- 登录、退出、获取用户信息
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

```bash
npm install
npm run dev
```

默认地址：`http://127.0.0.1:5173/login`

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

## 后续优化方向

- 接入真实后端接口，替换 `src/mocks`
- 增加单元测试和 E2E 测试
- 完善异常监控和埋点统计
