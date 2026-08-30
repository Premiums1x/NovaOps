# NovaOps

NovaOps 是一个面向企业运维场景的运维管理与 RAG 智能问答平台。项目覆盖认证授权、用户管理、工单、资产、知识库、Agent 对话和数据看板，并支持前端 Mock 演示与 Java 真实后端联调。

## 核心能力

- 单租户统一存储：用户、权限、业务数据、知识库向量和 Agent 会话均在同一租户上下文中管理，按身份与权限码控制访问。
- 单身份权限：每个用户绑定一个身份，管理员可维护用户状态、身份和密码。
- 动态权限：后端下发菜单与权限码，前端实现动态路由和按钮级鉴权。
- 知识库：支持文档上传、Apache Tika 异步解析、重叠分块、向量化、分块预览、替换与删除。
- 可信问答：问题先由受控 Router 分流到 METADATA / RAG / CHAT；RAG 路径具备查询改写、相关性过滤、真实 chunk 证据、grounding 与 chunkId 引用完整性校验。
- 运维业务：提供工单、资产、Dashboard 等常见中后台模块。
- 双运行模式：可完全使用 MSW Mock，也可连接 Spring Boot、MySQL、Qdrant 与 SiliconFlow。
- 主题与布局：支持亮色/暗色主题、紧凑模式、动态菜单、多标签页和全局 Agent 浮窗。

## 技术栈

### 前端

- Vue 3、TypeScript、Vite
- Ant Design Vue、Pinia、Vue Router
- Axios、ECharts、MSW
- ESLint、Prettier、Husky、lint-staged、commitlint

### 后端与 AI

- Java 17、Spring Boot 3.4、Spring AI
- MyBatis、MySQL 8、JWT
- Apache Tika、Qdrant
- SiliconFlow OpenAI 兼容接口

## 项目结构

```text
NovaOps/
├─ backend/
│  ├─ sql/                         # 初始化与升级脚本
│  └─ src/main/
│     ├─ java/com/novaops/backend/
│     │  ├─ auth/                  # 认证、用户与权限
│     │  ├─ ticket/                # 工单
│     │  ├─ kb/                    # 文档解析、分块与检索
│     │  ├─ agent/                 # RAG 对话与会话
│     │  └─ common/                # 响应、异常与安全上下文
│     └─ resources/mapper/         # MyBatis 映射
├─ src/
│  ├─ api/                         # 接口客户端
│  ├─ components/                  # 通用组件与 Agent 浮窗
│  ├─ composables/                 # 对话等组合式逻辑
│  ├─ layout/                      # 后台布局
│  ├─ mocks/                       # MSW 数据与处理器
│  ├─ router/                      # 静态和动态路由
│  ├─ store/                       # 用户、主题、标签页、对话状态
│  ├─ views/                       # 页面模块
│  └─ utils/                       # 请求、SSE 等工具
├─ docker-compose.yml              # Qdrant
└─ .env.example                    # 环境变量示例
```

## 环境要求

- Node.js 20+ 与 npm
- Java 17 与 Maven 3.9+
- MySQL 8
- Docker（运行 Qdrant 时需要）
- SiliconFlow API Key（知识库向量化和 RAG 问答时需要）

## 快速开始

### 方式一：前端 Mock 演示

```bash
npm install
npm run dev
```

默认访问地址：`http://127.0.0.1:5173/login`。

`.env.development` 可配置：

```dotenv
VITE_API_BASE_URL=/api
VITE_ENABLE_MOCK=full
```

`VITE_ENABLE_MOCK` 支持：

- `full`：所有接口使用 MSW。
- `partial`：已实现的接口访问 Java 后端，其余接口由 MSW 接管。
- `off`：关闭 MSW，所有 `/api` 请求访问真实后端。

预置账号密码均为 `123456`：

| 账号 | 身份 | 用途 |
| --- | --- | --- |
| `admin` | 管理员 | 全部功能及用户管理 |
| `staff` | 运维人员 | 工单、资产和智能问答 |
| `guest` | 访客 | 授权范围内只读访问 |

新账号通过邮箱注册并激活，默认授予普通成员身份；管理员账号请通过初始化脚本创建（默认 `admin / 123456`）。

### 方式二：运行完整服务

#### 1. 初始化数据库

创建数据库并导入完整初始化脚本：

```sql
CREATE DATABASE novaops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

```bash
mysql -uroot -p novaops < backend/sql/novaops_init.sql
```

如果数据库来自旧版本，再执行：

```bash
mysql -uroot -p novaops < backend/sql/refactor_rag_migration.sql
```

#### 2. 启动 Qdrant

```bash
docker compose up -d qdrant
```

Qdrant HTTP 与 gRPC 默认端口分别为 `6333` 和 `6334`。

#### 3. 配置环境变量

至少配置数据库、JWT 和 SiliconFlow 密钥。`NOVAOPS_JWT_SECRET` 没有默认值，缺失或长度不足 32 字节时后端会拒绝启动。PowerShell 示例：

```powershell
$env:NOVAOPS_DB_URL="jdbc:mysql://127.0.0.1:3306/novaops?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8"
$env:NOVAOPS_DB_USERNAME="root"
$env:NOVAOPS_DB_PASSWORD="你的数据库密码"
$env:NOVAOPS_JWT_SECRET="请替换为足够长的随机密钥"
$env:SILICONFLOW_API_KEY="你的 API Key"
```

可选配置：

```dotenv
SILICONFLOW_BASE_URL=https://api.siliconflow.cn
SILICONFLOW_CHAT_MODEL=Qwen/Qwen3-8B
SILICONFLOW_EMBEDDING_MODEL=BAAI/bge-m3
QDRANT_BASE_URL=http://localhost:6333
QDRANT_COLLECTION=novaops_kb
NOVAOPS_KB_STORAGE=./data/kb
NOVAOPS_AGENT_TOP_K=5
NOVAOPS_AGENT_MIN_SCORE=0.55
NOVAOPS_AGENT_RETRIEVAL_VALIDATION_MIN_SCORE=0.5
NOVAOPS_AGENT_METADATA_MAX_DOCUMENTS=200
```

不要提交真实密码、API Key 或本地配置文件。

#### 4. 启动后端

```bash
cd backend
mvn -gs mvn-settings.xml spring-boot:run
```

后端默认地址：`http://127.0.0.1:8090`。

#### 5. 启动前端

```bash
npm install
npm run dev
```

Vite 会将 `/api` 代理到 `http://127.0.0.1:8090`。

## 主要接口

### 认证与用户

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/register`
- `GET /api/auth/verify`
- `GET /api/auth/me`
- `GET /api/auth/menu`
- `GET /api/auth/roles`
- `GET /api/auth/users`
- `PUT /api/auth/users/{id}/status`
- `PUT /api/auth/users/{id}/role`
- `PUT /api/auth/users/{id}/password`

### 知识库与 Agent

- `GET/POST /api/kb/documents`
- `GET/PUT/DELETE /api/kb/documents/{id}`
- `GET /api/kb/documents/{id}/chunks`
- `POST /api/kb/documents/{id}/replace`
- `POST /api/agent/chat`（SSE）
- `GET /api/agent/conversations`
- `GET /api/agent/conversations/{id}`

Agent 对话会先返回受控路由结果（`route` 事件），并生成一份可公开展示的行动计划（`plan` 事件），再执行真实的处理管线。只有 `RAG` 路由会调用规划模型生成计划，失败或输出格式不合法时自动回退为固定的“检索知识库 → 生成回答 → 校验引用”计划，不会阻断回答；`METADATA` 只读取文档标题、类型、状态等元数据（不执行向量检索），`CHAT` 不访问知识库，两者使用固定计划。RAG 没有有效证据或回答未通过 grounding / chunkId 完整性校验时会安全拒答；会话首轮没有可补全的指代时也会跳过检索词改写调用。可通过 `NOVAOPS_AGENT_PLAN_ENABLED=false` 关闭额外规划调用。

`POST /api/agent/chat` 的 SSE 事件如下：

| 事件 | 用途 |
| --- | --- |
| `route` | 受控路由结果：`METADATA` / `RAG` / `CHAT` 及理由 |
| `plan` | 一次性下发 `steps`，包含 `action/label/query/reason/status`，已按路由裁剪 |
| `step` | 按 `action` 更新执行状态：`running/done/failed`，结果摘要放在 `payload` |
| `delta` | 回答正文增量 |
| `citation` | 知识库引用列表 |
| `evidence` | 通过校验的原始检索证据 |
| `meta` | 检索与校验统计、依据校验结果与耗时 |
| `done` | 本轮正常完成 |
| `error` | 本轮失败；包括建会话等流建立前的同步异常 |

该接口必须返回 `text/event-stream`。前端发现 HTTP 200 携带普通 JSON 错误体时，会读取其中的 `message` 并显示错误，不再产生空白 AI 气泡。

### 工单

- `GET/POST /api/tickets`
- `GET/PUT /api/tickets/{id}`
- `POST /api/tickets/{id}/actions`
- `GET/POST /api/tickets/{id}/comments`
- `POST /api/tickets/{id}/attachments`

## 常用命令

```bash
npm run dev
npm run build
npm run lint
npm run format
```

后端验证（单元测试覆盖登录安全逻辑、自助注册约束、权限码校验、JWT 密钥与工单 ID 生成）：

```bash
cd backend
mvn -gs mvn-settings.xml test
```

生产构建生成在 `dist/`，该目录已被 Git 忽略，可部署到 Nginx 等静态服务器。

## 安全说明

- 生产环境必须覆盖默认数据库密码和 `NOVAOPS_JWT_SECRET`。
- 知识库文件、向量数据、本地环境变量和构建产物均不应提交到仓库。
- 业务数据归属由服务端认证上下文统一确定，前端无法自行声明。
