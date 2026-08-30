# NovaOps 问题路由与可验证 RAG 改造进度

最后更新：2026-08-30

## 总体状态

- 当前阶段：阶段 F——文档、验收与 PR
- 当前分支：`feat/rag-router-pipeline`
- 总体状态：代码与验收完成，等待 PR 创建
- 执行方案：`docs/RAG_ROUTER_REFACTOR_PLAN.md`

## 阶段跟踪

### 0. 项目审计与方案

- [x] 读取目标说明与架构示意图
- [x] 明确不使用 cavemen / Superpowers
- [x] 审计现有 Agent、KB Retriever、数据库、SSE 与前端展示
- [x] 记录工作区原有改动并隔离
- [x] 创建独立改造分支
- [x] 完成详细执行方案

证据：当前实现的所有问题均由 `AgentService` 直接调用 `KbRetrievalService.retrieve`；现有 `CitationValidator` 使用数字引用和 embedding 相似度；前端仅显示引用标签。

### A. 契约与模型网关

- [x] 新建受控 route 与决策契约
- [x] 新建结构化 RAG answer / relevance / grounding 契约
- [x] 新建 `AgentModelGateway` 抽象与 Spring AI 实现
- [x] 实现严格 JSON 解析与输出白名单
- [x] 添加契约与解析测试

完成说明：`StructuredModelOutputParser` 只接受 JSON 对象、枚举白名单和指定字段；Markdown fence 可提取，非法 route、缺字段、空输出直接失败。

### B. METADATA 与 Router

- [x] 新建 KB 元数据快照服务
- [x] 实现 `QuestionRouter` 与安全降级
- [x] 实现 Metadata Handler
- [x] 实现 Chat Handler
- [x] 添加路由与 handler 测试

完成说明：METADATA 只调用 `KbMetadataService`；编排测试证明 METADATA/CHAT 不会落入 `RagPipeline`。

### C. 可验证 RAG Pipeline

- [x] 新建不可变 `RagExecutionState`
- [x] 实现 Query Rewrite
- [x] 实现 Retrieval Validator/Reranker
- [x] 实现基于 `chunkId` 的 Citation Integrity
- [x] 实现 Grounding Check
- [x] 实现一次重试与二次失败安全拒答
- [x] 覆盖空检索、空有效证据、伪造引用与不受支持回答测试

完成说明：`RagPipelineTest` 证明 0 raw/validated evidence 时生成器零调用；伪造 ID 与 grounding 失败均只重试一次，之后返回固定拒答；成功结果中的 evidence 原文与 Retriever 输出完全一致。

### D. 编排、SSE 与兼容

- [x] 重构 `AgentService` 为路由与 SSE 编排层
- [x] 增加 `route`、`evidence` 与完整 `meta` 事件
- [x] 保持会话历史和现有 API 兼容
- [x] 为模型调用配置独立、可控的异步线程池
- [x] 覆盖三路分流与异常终态的业务级测试

### E. 前端证据 UI

- [x] 更新 TypeScript 消息/SSE 契约
- [x] 接收 route/retrieval/validation 状态
- [x] 展示可展开的 Retriever 原始证据卡
- [x] 更新组件测试与 MSW 三路演示

### F. 文档、验收与 PR

- [x] 更新 README 与项目架构说明
- [x] 后端单元测试通过
- [x] 前端单元测试全部通过（干净检出 9 suites / 25 tests）
- [x] ESLint 通过
- [x] TypeScript 与生产构建通过
- [x] 审计 diff 与敏感信息
- [x] 提交本地分支（`71b7312`）
- [ ] 推送分支
- [ ] 创建 PR

## 验收结果

- 后端：13 suites / 63 tests，0 failures，0 errors。
- 前端：干净检出 9 suites / 25 tests 全部通过。
- TypeScript：`vue-tsc -b` 通过。
- ESLint：0 warnings / 0 errors。
- 生产构建：Vite build 通过；仅保留项目原有的大 chunk 与静态/动态 import 混用提示。
- Diff：`git diff --check` 通过；未检出密钥；本次提交不包含用户原有 `vite.config.ts` 与 `conflict_dump.txt`。

说明：主工作区直接运行全量前端测试时，用户原有未提交的 `vite.config.ts` 会使端口契约测试看到 8080，而仓库后端配置是 8090。为证明本次提交自身状态，已从 `71b7312` 创建临时干净检出并完成 25/25 测试，随后安全删除该临时检出。

## 核心不变量验收表

| 不变量 | 状态 | 验收证据 |
| --- | --- | --- |
| Router 只允许三种 route | 已通过 | `StructuredModelOutputParserTest`、`QuestionRouterTest` |
| METADATA 不调用向量检索 | 已通过 | `AgentWorkflowOrchestratorTest`、`MetadataWorkflowHandlerTest` |
| Retriever 输出是证据唯一来源 | 已通过 | `RagPipelineTest.successfulAnswerUsesOriginalRetrieverChunkAsEvidence` |
| 0 evidence 不调用生成器 | 已通过 | `RagPipelineTest` 两个空证据测试 |
| citation ID 必须属于 validated chunks | 已通过 | `CitationIntegrityValidatorTest` 与伪造 ID Pipeline 测试 |
| grounding 失败不放行 | 已通过 | `RagPipelineTest.unsupportedGroundingRetriesOnceThenFailsClosed` |
| UI 展示原始 chunk | 已通过 | `ChatCore.test.ts` evidence 场景与生产构建 |

## 已知工作区状态

以下内容在本次改造开始前已存在，不属于本次变更，不会纳入提交：

- `vite.config.ts`：本地代理端口由 8090 改为 8080。
- `conflict_dump.txt`：未跟踪文件。

## 决策记录

1. 不采用 Router Agent → RAG Agent → Validation Agent 的多 Agent 链，而采用受控 Router + 固定 Handler/Pipeline。
2. RAG 校验失败时 fail closed，不再返回“未通过校验，仅供参考”的回答。
3. 对外展示序号由后端生成；模型内部引用稳定的 `chunkId`。
4. METADATA 读取 MySQL 文档元数据快照，不调用 Qdrant。
5. 业务层通过 `AgentModelGateway` 隔离 Spring AI，保证可测试性。
