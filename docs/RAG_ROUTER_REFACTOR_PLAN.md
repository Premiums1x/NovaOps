# NovaOps 问题路由与可验证 RAG 架构改造方案

## 1. 改造目标

当前 `AgentService` 会把所有用户问题直接送入向量检索，因此“知识库里有什么”一类总览问题无法得到正确数据；同时，回答生成、引用选择与验证过度依赖模型自证，无法形成可靠的程序级保证。

本次改造将问答入口重构为受控的三路工作流：

```text
User Query
    ↓
Question Router (METADATA / RAG / CHAT)
    ├── METADATA → Metadata Snapshot → Metadata Answer
    ├── RAG      → Rewrite → Retrieve → Validate/Rerank → Evidence Store
    │                                      ↓
    │                               Generate Structured Answer
    │                                      ↓
    │                         Grounding Check + Citation Integrity
    │                                      ↓
    │                         Answer + Original Evidence Chunks
    └── CHAT     → Direct LLM Answer
```

改造后的核心原则：

1. 可选路由仅允许 `METADATA`、`RAG`、`CHAT`，模型不能自由决定任意工具或步骤。
2. Retriever 的真实返回值是 RAG 证据的唯一事实来源。
3. 没有通过相关性校验的 chunk 时，不调用回答生成模型，直接返回无可靠依据提示。
4. 模型只能引用当前请求 `validatedChunks` 中真实存在的 `chunkId`。
5. 原始 chunk 由后端直接发送给前端，模型不能改写“相关资料”。
6. 任何未通过 grounding 或 citation integrity 的回答都不以“仅供参考”的形式放行，而是重试一次后安全拒答。
7. 能由代码确定的事实（是否检索、chunk 是否存在、引用是否越界、是否有证据）全部由代码校验；语义相关性与语义支撑关系才交给模型。

## 2. 当前架构审计结论

### 2.1 已有能力

- `KbRetrievalService` 隔离了 Agent 与 Qdrant，返回含 `chunkId/documentId/documentName/content/score` 的真实 chunk。
- 向量检索已支持 `topK` 与 `minScore`。
- `AgentService` 支持 SSE、会话持久化、无检索结果固定拒答。
- 前端已经能接收 `delta/citation/meta/done/error` 事件，并显示引用标签。
- `CitationValidator` 能识别不存在的数字引用，但仍依赖模型自由生成 `[1]`，且使用 embedding 相似度代替真正的 grounding 判断。

### 2.2 主要缺口

- 无 Question Router，所有问题均执行 chunk embedding 检索。
- 无 METADATA 专用数据接口，知识库总览问题使用了错误的数据源。
- 无 Query Rewrite，追问与口语化问题直接进入检索。
- 无独立 Retrieval Validator/Reranker；Qdrant 阈值是唯一相关性门槛。
- 无显式请求状态，无法清晰区分 raw retrieved chunks、validated chunks、answer 与校验结果。
- 引用使用易伪造的展示序号，不是稳定 `chunkId` 契约。
- grounding 失败后仍可能返回带警告的未验证回答，不满足可信 RAG 的 fail-closed 原则。
- 前端只显示来源标签，没有直接展示 Retriever 返回的原始证据内容与执行状态。
- 缺少 Agent 路由、流水线和引用完整性的后端单元测试。

## 3. 目标模块与职责

### 3.1 `QuestionRouter`

输入：当前问题与有限的最近会话上下文。

输出：

```json
{
  "route": "METADATA | RAG | CHAT",
  "reason": "简短分类原因"
}
```

约束：

- JSON 解析失败、非法枚举或模型不可用时使用安全的确定性降级策略。
- 明确命中知识库总览关键词时降级到 `METADATA`；其他无法确认的问题默认走 `RAG`，避免把潜在知识库问题错误地交给通用模型自由回答。
- 路由结果通过 SSE `route` 事件返回，便于 UI 与测试观察真实执行路径。

### 3.2 `KnowledgeBaseMetadataService`

只读取文档元数据，不访问向量库。输出快照包含：

- 文档总数、READY 文档数；
- 文档标题、文件名、文件类型、状态、chunk 数与更新时间；
- 是否因配置的最大文档数而截断。

METADATA Handler 将用户问题和此快照交给模型总结。若知识库为空，则由代码直接返回固定结果。

### 3.3 `RagPipeline`

按固定顺序执行：

1. Query Rewrite：结合最近上下文将当前问题改写为独立、适合检索的查询；失败时使用原问题。
2. Retrieve：调用 `KbRetrievalService`，记录 `retrievalExecuted=true` 与原始 `retrievedChunks`。
3. Retrieval Validate/Rerank：模型对每个真实 `chunkId` 给出 relevant 与 relevance score；代码丢弃未知 ID、低分项并稳定排序。验证器失败时保守使用已经通过 Qdrant `minScore` 的原始顺序，不创造新证据。
4. Evidence Store：生成不可变的请求状态，后续只能使用 `validatedChunks`。
5. Structured Answer：模型返回 `answer` 与 `citationChunkIds`，禁止自由生成展示序号。
6. Citation Integrity：代码验证每个引用 ID 都属于当前 `validatedChunks`，并要求非空回答至少有一个真实引用。
7. Grounding Check：模型基于回答和原始 evidence 判断事实是否全部受支持，并返回不受支持的声明。
8. Retry/Fail Closed：任一校验失败时只允许重新生成一次；第二次仍失败则返回固定拒答，不返回未验证内容。
9. Response Build：后端把引用 ID 映射为原始 `CitationDto`，再生成 UI 展示序号。

### 3.4 `ChatHandler`

- 仅处理与知识库无关的通用对话。
- 不执行 Retriever，不生成知识库引用。
- SSE meta 明确 `retrievalExecuted=false`、`validationStatus=NOT_APPLICABLE`。

### 3.5 `AgentModelGateway`

集中封装 Spring AI 调用与 JSON 解析，业务编排不直接依赖 `ChatClient`。职责包括：

- route；
- rewrite；
- validate/re-rank chunks；
- generate structured RAG answer；
- validate grounding；
- answer metadata；
- answer chat。

接口化后，路由与流水线可使用假实现进行确定性单元测试。

## 4. 请求状态与事件契约

### 4.1 RAG 请求状态

```text
RagExecutionState
├── originalQuery
├── retrievalQuery
├── retrievalExecuted
├── retrievedChunks        # Retriever 原始输出，禁止模型改写
├── validatedChunks        # 相关性校验后的允许证据集
├── generatedAnswer
├── citedChunkIds
├── groundingPassed
├── citationIntegrityPassed
└── failureReason
```

### 4.2 SSE 事件

保留现有事件，并增加可观察状态：

- `route`：`route`、`reason`。
- `delta`：最终可展示文本片段。
- `citation`：由后端从原始 chunk 构造的引用列表。
- `meta`：`retrievalExecuted`、`retrievedCount`、`validatedCount`、`validationStatus`、`validationReason`、`elapsedMs`。
- `done` / `error`：保持兼容。

### 4.3 前端消息模型

Assistant 消息新增：

- `route`、`routeReason`；
- `retrievalExecuted`、`retrievedCount`、`validatedCount`；
- `validationStatus` 与 `validationReason`；
- `citations` 中保留 Retriever 的原始 chunk 内容。

前端通过可展开证据卡展示文档名、chunkId、score 与原文，不让模型生成“相关资料”文本。

## 5. 分阶段执行方案

### 阶段 A：契约与模型网关

- 新建路由、结构化答案、相关性结果、grounding 结果等 DTO/record。
- 新建 `AgentModelGateway` 与 Spring AI 实现。
- 实现严格 JSON 提取、枚举白名单、未知 ID 丢弃策略。
- 为解析与确定性校验补单元测试。

验收：模型输出非法 JSON、非法 route、伪造 chunkId 时均不会进入未受控路径。

### 阶段 B：METADATA 与 Router

- 在 kb 模块增加只读 Metadata Service，Agent 不跨模块访问 `KbMapper`。
- 实现 `QuestionRouter` 与安全降级。
- 实现 Metadata Handler 和 Chat Handler。
- 添加路由矩阵测试：总览、具体知识、通用闲聊、非法模型输出。

验收：“知识库里有什么”不调用向量 Retriever；具体安装/步骤问题调用 RAG；CHAT 不产生知识库引用。

### 阶段 C：可验证 RAG Pipeline

- 实现 `RagExecutionState`、Query Rewrite、Retrieval Validator/Reranker。
- 将引用契约改为 `chunkId`。
- 实现 deterministic Citation Integrity。
- 实现 Grounding Validator 与一次重试、二次失败安全拒答。
- Retriever 异常、0 raw chunks、0 validated chunks 分别返回明确状态。

验收：0 chunk 不调用生成；伪造 ID 被拒；失败回答不放行；返回引用内容与 Retriever 原文逐字相等。

### 阶段 D：编排、SSE 与持久化兼容

- 将 `AgentService` 缩减为会话/SSE 生命周期与工作流编排。
- 统一三种路径的 response builder 与 meta。
- 历史消息继续兼容既有 `citationsJson/validationPassed` 字段。
- 不引入必须重建数据库的 schema 变更；路由执行信息先通过 live SSE 提供。

验收：三种 route 都能完成 SSE 生命周期；异常路径只发一次终态；断开时停止未完成模型流或任务。

### 阶段 E：前端证据 UI

- 更新 TypeScript 契约与 `useChat` 事件处理。
- 显示路由与检索/校验状态。
- 引用区改为可展开的原始证据卡，而不是只有来源标签。
- 更新组件测试，覆盖 RAG 证据、METADATA/CHAT 无引用与失败状态。

验收：UI 展示的 chunkId、内容、分数来自后端 citation 事件，回答文本不会充当证据。

### 阶段 F：文档、全量验证与 PR

- 更新 README 与项目架构文档。
- 运行后端测试、前端测试、lint、类型检查与生产构建。
- 审计 diff，确认不包含用户原有无关改动与敏感信息。
- 提交 `feat/rag-router-pipeline` 分支并推送，创建 PR。

验收：所有自动化检查通过；PR 描述列出架构变化、失败策略、测试证据与兼容性说明。

## 6. 测试矩阵

| 场景 | 预期 route | Retriever | 生成器 | 最终结果 |
| --- | --- | --- | --- | --- |
| “知识库里有什么？” | METADATA | 不调用 | Metadata answer | 元数据总览 |
| “有没有 Element Plus X 文档？” | METADATA | 不调用 | Metadata answer | 基于标题/文件元数据回答 |
| “Element Plus X 怎么安装？” | RAG | 调用 | 有有效证据才调用 | 回答 + 原始 chunks |
| RAG 返回 0 chunks | RAG | 调用 | 不调用 | 固定无依据提示 |
| Rerank 后 0 chunks | RAG | 调用 | 不调用 | 固定无可靠证据提示 |
| 模型引用未知 chunkId | RAG | 调用 | 最多两次 | 第二次仍失败则拒答 |
| Grounding 判定不通过 | RAG | 调用 | 最多两次 | 第二次仍失败则拒答 |
| “你好” | CHAT | 不调用 | Direct chat | 通用回复、无 citation |
| Router 非法 JSON | 安全降级 | 按降级结果 | 受对应流程约束 | 不越过受控选择集 |
| 向量库不可用 | RAG | 尝试失败 | 不调用 | 服务暂不可用提示 |

## 7. 非目标与兼容性

- 本次不引入多 Agent 自主规划框架；下游是固定 Pipeline/Handler。
- 本次不更换 Qdrant、Spring AI 或 SiliconFlow。
- 本次不要求新增数据库迁移即可运行，优先复用现有会话表结构。
- 保留 `/api/agent/chat` 与现有 SSE 基础事件，前端进行向后兼容扩展。
- 不修改用户现存的本地 Vite 代理改动或未跟踪文件。

## 8. 完成定义

只有同时满足以下条件才视为完成：

1. 三路路由均有明确代码路径与自动化测试。
2. METADATA 不执行 chunk 向量检索。
3. RAG 的 raw/validated evidence 可在请求状态中追踪。
4. 0 evidence 时生成器零调用。
5. 引用只接受当前 validated chunks 的真实 `chunkId`。
6. grounding/citation 两次失败后 fail closed。
7. UI 能查看后端返回的原始证据内容。
8. 文档、测试、lint、构建全部通过。
9. 变更已提交到独立分支并创建 PR。
