# PR 正文：feature/ai-b → develop

> 用途：`gh pr create --body-file nexus-ai/PR-BODY.md` 的纯净正文（无标题段落、无 ``` 包裹）。
> 最后更新：2026-11-22 12:15（rebase develop 967f883 之后）。

## 本 PR 包含

| 模块 | 内容 | 提交（rebase 后） |
| --- | --- | --- |
| 阶段一 | MaaS/OCR Provider 接口、抽取 Schema、文档抽取 Demo、测试资料集 | `5eec882` `4626522` |
| 阶段三 | 4 类风险规则引擎（期限/到期/日程冲突/预算） | `7f58689` |
| 阶段四 | Agent 工具注册 + 编排（L0-L2 白名单）、Runner 真实 run 上下文自测 | `7f58689` `40dcee6` |
| 缺口修复 | 抽取输出全链路补齐 `sourceId`（雪花串透传）；90013 硬阻断；文档示例去 UUID | `2856c6b` `6f32327` |
| 契约对齐 | 同步 A 的 PR#1 拍板：sourceId 雪花串、三态 factStatus、level=medium、90011-90013 | `50e6abc` `6f32327` |
| 执行链路修复 | `update_fact` payload 字段 `proposedValue` → `objectValue`，与 A 的 `AgentActionExecutor` 对齐，解除 L2 授权执行失败 | `222a9e3` |
| 契约文档 | schema 第 12 节 DDL 对齐；给 A 的契约差异移交清单（P0 四项已全部关闭） | `2856c6b` `6e6d269` `98f7f5f` |
| 验收工具 | 真实 MaaS/OCR 调用脚本 + 凭据模板（无密钥入库） | `6e6d269` |

本分支已 rebase 到 `develop`（`967f883`，含 A 的 PR#1 + CHECK 改名 + L1/L2 执行器）， ahead 11 / behind 0。

## 验证结果

- `mvn test`：**28/28 通过**（rebase 前后各跑一次，全绿）
- 编排单测覆盖：L0 只读汇总、L1 `create_task`、L2 `update_fact` 授权、歧义降级只读
- `update_fact` payload 键名已与执行器 `requireString("objectValue")` 对齐，L2 授权后事实更新可真实落库（链式失效）
- mock Demo：entity + 3 fact + 1 event 共 5 处均带雪花串 `sourceId`，无自造 UUID

## 契约状态（详见 `nexus-ai/CONTRACT-HANDOFF-TO-A.md`）

- **P0 四项全部关闭**：sourceId 类型拍板（BIGINT 雪花串透传）、`ExtractionRequest.sourceId`、`world_facts` 五列、`world_sources` 三列去重字段 —— A 已在 `a6e5c92`/`f1134dd` 落实并核实。
- **P1 四项待联调拍板**：aliases 不加列 / 事实唯一键建议补 + COALESCE / `risk_facts` 建关联表 / 实体判重 name 归一化 —— 不阻塞本 PR。
- 本次改动仅涉及 `com/nexus/ai` 包、`nexus-ai/`、`docs/extraction-schema.md`，未触碰 A/C 独占文件。

## 合入前检查清单

- [x] A、B、C review 通过（A 的 PR#1 review 意见已全部落实）
- [x] 基于最新 develop rebase，零冲突
- [x] `mvn test` 28/28
- [x] `update_fact` payload 字段与 A 执行器对齐（L2 授权链路关键）
- [ ] 真实 MaaS/OCR 调用验收因缺华为云凭据暂缓（凭据到位后补）

## 合入后计划

C 切真实接口 → 阶段四端到端联调（上传 → 抽取入库轮询 → 规则产险 → 编排 → L1/L2 授权执行）→ 阶段五云上部署。
