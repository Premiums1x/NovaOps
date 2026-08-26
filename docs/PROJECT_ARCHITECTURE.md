# NovaOps 项目架构导览

## 1. 项目定位

NovaOps 是 Vue 3 SPA + Spring Boot 单体模块化后端。存量的认证、工单、资产和看板保持原业务边界，本次新增企业知识库与可信 RAG 问答。

## 2. 前端从哪里开始读

1. `src/main.ts`：启动应用、Pinia、路由、Ant Design Vue 与 MSW。
2. `src/App.vue`、`src/store/theme.ts`：全局主题算法和 CSS token。
3. `src/router/guard.ts`、`src/store/permission.ts`：JWT 状态、动态菜单与路由级权限。
4. `src/api` → `src/store`/`src/composables` → `src/views`：API、状态/业务交互、视图三层。
5. `src/components/agent/ChatCore.vue`：浮窗与独立页共用的聊天核心。

## 3. 后端从哪里开始读

后端按 `auth/ticket/kb/agent/common` 分包。Controller 只处理 HTTP 契约，Service 承担业务，Mapper 只操作本模块表。

- `common/security/AuthInterceptor`：所有 `/api/**` 的 JWT 与账号启用状态入口。
- `auth/AuthService`：登录认证、用户管理和菜单。
- `kb/KbDocumentService`：文档 CRUD；`KbIngestionService`：异步解析与向量化；`KbRetrievalService`：唯一的对内检索契约。
- `agent/AgentService`：检索、Prompt、模型流、校验、SSE 和会话持久化。

## 4. RAG 数据流

上传时，MySQL 保存文档状态和原始 chunk，Qdrant 保存 embedding 与 `documentId/chunkId/documentName` payload。问答时，Agent 只调用 `KbRetrievalService.retrieve(query, topK, minScore)`；无结果拒答，有结果才调用模型。回答引用编号必须存在于本次命中集合，并通过论断与 chunk 的 embedding 相似度复核。

## 5. 安全边界

- 用户身份来自已签名 JWT；前端无法自行声明业务数据归属。
- 知识库管理与用户管理在菜单/路由和后端 Service 两层限制管理员。
- SiliconFlow Key 只从后端 `SILICONFLOW_API_KEY` 读取。
- 用户禁用逐请求生效；角色、密码和启停变更撤销 refresh token。

## 6. 部署组件

- 前端静态资源
- Spring Boot 3.4.5 / Java 17+
- MySQL 8
- Qdrant（根目录 `docker-compose.yml`）
- SiliconFlow OpenAI 兼容 API

外部 AI 或向量服务不可用时，认证、工单、资产和看板仍可工作。
