# PR #1 Review 意见（成员 B → 成员 A）

> 目标 PR：`feature/backend-a` → `develop`，基线 `6ad0ded`，新提交 `f8acddc`
> Review 范围：A 指定的 `database-design.md` 第 4/6 节 + `com.nexus.ai` 接口签名
> 说明：以下为 review 意见，供贴到 PR 评论/群聊。B 未修改 A 独占文件。

---

## 🔴 阻塞项（合入前需 A 拍板）

### 1. `source_id` 三方类型不一致 —— 事实无法入库

- `docs/extraction-schema.md` 定义 `source_id` 为 `string(uuid)`（B 的输出，Demo 已产出 `00000000-0000-4000-8000-000000000001`）。
- `docs/database-design.md` 第 4 节：`world_sources.id BIGINT PRIMARY KEY`、`world_facts.source_id BIGINT REFERENCES world_sources(id)`。
- `docs/api-specification.md` + `JacksonConfig.java`（`f8acddc`）：所有 `*Id` 序列化为**字符串**（雪花 ID 数字字符串）。
- **冲突**：UUID 字符串 ≠ BIGINT 数字字符串，B 的输出无法作为外键写入 A 的事实表，联调必失败。
- **请 A 三选一**：
  1. DDL 把 `world_sources.id` / 各外键改为 `UUID` 或 `VARCHAR(36)`（推荐，贴合 B schema 与可追溯语义）；
  2. B 改成数字字符串（不推荐，丢失 UUID 语义）；
  3. 加 UUID ↔ 雪花 ID 映射表，抽取层留 UUID、入库层转换。
- 详见 `nexus-ai/CONTRACT-HANDOFF-TO-A.md` P0-0。

### 2. B 的校验错误码不在 A 的错误码总表 / `ResultCode.java`

- `extraction-schema.md` 定义的 `E-NO-SOURCE`、`E-SCHEMA`、`E-PREDICATE` 在 `docs/api-specification.md` 错误码总表和 `nexus-backend/.../ResultCode.java` 中均不存在。
- **请 A**：补专属错误码（建议 9xxxx 段），或明确统一映射到 `90001 请求参数不合法`；B 据此调整 `BizException` 抛出。
- 影响：抽取校验失败时前端拿到未定义码，无法正确提示。

---

## 🟡 契约差异（已知，移交清单在案，A 确认即可）

3. `ExtractionRequest` DTO 无 `source_id` 字段，B 现用 `extra.sourceId` 透传 → 需 A 加字段（移交清单 P0-1）。
4. `world_facts` 缺 `value_type` / `unit` / `normalized_value` / `fact_status` / `evidence` 列 → 需 A 补列或独立证据表（移交清单 P0-2）。
5. `world_sources` 缺 `sha256` / `page_count` / `uploaded_at` → 视去重需求补列（移交清单 P0-3）。

---

## ✅ Review 通过的部分

- `database-design.md` 第 6 节 B 注意事项 4 条：与 B 实现一致（写事实带 source_id+confidence、先落 entities 拿 subject_id、sources.status 只读、字段变更走 PR）。无异议。
- `com.nexus.ai` 6 个 Provider 接口签名（`AiProvider` / `Llm` / `Ocr` / `Extraction` / `RiskAnalysis` / `AgentOrchestrator`）：B 已全部实现，签名无需变更。无异议。
- `f8acddc` 的认证链路修复（Bean 扫描、JWT 双注册、401 对齐 10105）、`GET /users/me`、BIGINT 字符串制度化（JacksonConfig）、JSON 错误归 90001：认可，与 B 无冲突。
- 合并预检：`ai-b` 与最新 `backend-a` 无共同改动文件、`merge-tree` 无冲突标记，可干净合并。

---

## 处理结果（2026-11-22 更新，re-review 完成）

A 在 `a6e5c92` 已全部落实，B 已同步，**所有阻塞项关闭**：

| 原问题 | A 的处理 | B 的同步 |
| --- | --- | --- |
| ① source_id 类型冲突 | 拍板保持 BIGINT，DTO 加 `sourceId`，禁止自造 UUID | schema 改 `string(snowflake)`、Demo/测试默认雪花串、Provider 透传 `request.sourceId()`；新增 90011 校验与 3 个测试 |
| ② 校验错误码缺失 | 补 `90011/90012/90013`（`EXTRACTION_NO_SOURCE/SCHEMA/PREDICATE`） | Provider 缺 sourceId 抛 90011、Schema 不符抛 90012；E-PREDICATE 仍为告警（见下） |
| ③ fact_status 取值 | 三态 `confirmed/inferred/unverified`，待 B 确认后补 CHECK | **B 确认：接受三态**。映射 `extracted→confirmed`、`inferred` 不变、`unverified` 为 A 人工录入态（B 不产出）。请 A 补 CHECK 兜底 |
| ④ level `mid` | 统一 `medium`（前端已按 medium） | 规则引擎 4 处输出 + 4 处测试断言 + README 全部改 medium |

**需 A 留意的两点（不阻塞合并）：**
- `E-PREDICATE`（谓词未登记）：B 现按**告警**处理（降权保留事实），未抛 90013 硬错误。若 A 要求硬阻断，请明确，B 再改 parser 抛 `ResultCode.EXTRACTION_PREDICATE`。
- `fact_status` CHECK 兜底：B 已确认三态，请 A 在 DDL 补 `CHECK (fact_status IN ('confirmed','inferred','unverified'))`。

**验证结果**：`mvn test` 25/25 通过；mock Demo sourceId=雪花串（0 UUID 残留）、factStatus=confirmed、facts=3/置信度 0.91；B 侧 `mid`/旧 UUID/`extracted` 全局零残留。

## 建议合入顺序

1. ~~A 拍板阻塞项 1/2~~ ✅ 已完成（`a6e5c92`）。
2. PR #1 合并 develop。
3. B 的 `feature/ai-b` 提 PR 合并 develop（已 merge 最新 `backend-a`，无冲突）。
4. A 补 `fact_status` CHECK（可随 PR #1 后续提交）；B 确认 E-PREDICATE 告警/硬错误语义。
5. 凭据到位后 B 跑真实 MaaS/OCR 验收（`verify-real-call.sh`）。
