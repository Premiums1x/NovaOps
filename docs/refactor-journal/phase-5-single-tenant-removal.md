# Phase 5：单租户化 —— 物理移除多租户结构

## 背景

项目此前已完成"逻辑单租户化"：业务表保留 `tenant_id` 字段但值恒为 `tenant-a`（见 `AuthService` 原注释），租户管理/切换/邀请等功能性结构已删除。本阶段把数据层的租户残留物理移除，使代码与"单租户统一存储"的事实完全一致。

## 本阶段完成任务

- 后端：四个业务模块（ticket/asset/kb/agent）的 mapper SQL、模型、DTO 与 Service 参数链删除全部 `tenantId` 过滤与写入；`IdGenerator.ticketId()/assetNo()` 改为无参、前缀固定 `A`（工单/资产业务编号格式不变）。
- 会话与认证：`CurrentSession`/`JwtService` 删除 `tenantId` 载荷；`/auth/me` 与 `/auth/login` 响应删除 `tenantId/tenants`；删除孤儿类 `TenantRecord`、`TenantInfoResponse` 及 `RefreshTokenRecord.tenantId` 残留字段。
- 向量链路：`KbRetrievalService.retrieve` 去掉 `tenantId` 参数；Qdrant 写入 payload 与检索 filter 去掉 `tenantId`；`KbFileStorage` 目录由 `root/tenantId/docId` 改为 `root/docId`。
- 前端：删除 `TenantInfo`、`UserProfile.tenantId/tenants`、`LoginResponseDto.tenantId` 及各业务类型的 `tenantId`；请求层删除 JWT claims 解析与 `X-NovaOps-Tenant-Id`/`X-NovaOps-Username` 头注入（后端从不消费）。
- MSW mock：Session 与会话识别去租户化（按 `Authorization` token 表识别），四个 `*Db` 数据层删除双租户种子与 `*ByTenant` 首参。
- 数据库：`novaops_init.sql` 六张业务表删除 `tenant_id` 列与相关索引，`uk_asset_tenant_no` 改为 `uk_asset_no(asset_no)`；种子数据删除 tenant-b 演示工单及其关联行；删除已失效的 `refactor_rag_migration.sql`（引用了已不存在的列）。
- 文档：README、BACKEND_GUIDE、STARTUP_GUIDE、KNOWN_ISSUES、PROJECT_ARCHITECTURE、REFACTOR_PLAN 同步为单租户口径，删除不存在的 `switch-tenant` 接口与双租户说明。

## 验证结果

- 后端 `mvn test` 全量通过（含重写后的 IdGeneratorTest、JwtServiceTest、AuthServiceTest）。
- 前端 `vue-tsc` 类型检查通过；vitest 除既有端口契约测试外全部通过（该失败由本地未提交的 vite.config.ts 端口改动引起，与本次改动无关）。

## 运行时数据操作（需在目标环境一次性执行）

1. 重跑 `backend/sql/novaops_init.sql`（drop-and-recreate，本地库按推荐项重置）。
2. 迁移存量 KB 文件：`backend/data/kb/tenant-a/*` → `backend/data/kb/`。
3. 删除 Qdrant 集合 `novaops_kb`（启动后懒创建机制自动重建空集合）：
   `curl -X DELETE http://127.0.0.1:6333/collections/novaops_kb`
4. 端到端冒烟：注册→自动激活→登录→工单→资产→知识库上传→智能问答。

## 设计思路与取舍

- 拆分七个小提交、每个提交编译与单测全绿，按"业务模块先解耦、共享基础设施后删、SQL 与文档收尾"的顺序落地，保证任意中间提交可独立回退。
- 工单/资产编号保持 `A-TICKET-`/`A-ASSET-` 格式不变，存量 ID 与种子数据无需改写。
- 历史记录（phase-1..4、KNOWN_ISSUES 1.6）保留原始表述并补充变更注记，不修改历史事实。

## 遗留问题

- 本地 MySQL（127.0.0.1:13307/3306）凭据不可用，未在本机实际重跑 init.sql；需在可连接的环境执行上述数据操作并完成端到端冒烟。
- Qdrant 存量点位若保留旧 payload 的 `tenantId` 字段不影响单租户检索，可择机清空集合统一重建。
