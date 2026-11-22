# P1 四项联调拍板数据（B → A）

> 用途：阶段四端到端联调前的 P1 决策依据。A 可直接照此拍板，拍板后 B 侧如涉及抽取输出映射再跟进。
> 最后更新：2026-11-22 16:45（基于 develop `e338985` 真实 DDL 与代码复核）

---

## 0. 先报一个 P0 阻塞（不在原 P1 清单内，但会直接毁掉联调数据）

**抽取事实的 `confidence` / `factStatus` 写入链路是断的。**

- `FactResponse`（读）有 `confidence`、`factStatus` 字段；
- 但 `FactCreateRequest`（写）**没有这两个字段**，`WorldFactServiceImpl.create()` 硬编码：
  ```java
  fact.setConfidence(BigDecimal.ONE);    // 56 行，恒为 1.0
  fact.setFactStatus("confirmed");       // 61 行，恒为 confirmed
  ```
- 后果：B 抽取产出的事实（如置信度 0.78 的 `inferred` 推断事实），经 `POST /api/v1/facts` 落库后全部变成 **confidence=1.0 / factStatus=confirmed**。三态与置信度在入库瞬间丢失，下游风险规则、前端展示、人工复核全部失真。
- `api-specification.md` 5.2 的 POST 请求体也确实没列这两个字段——即**契约本身不对称**，不是单纯实现遗漏。

**B 的请求**：`FactCreateRequest` 增加 `BigDecimal confidence`、`String factStatus`（均可选，null 时保持现有默认 1.0/confirmed 以兼容人工录入），落库直接取请求值。B 侧抽取输出已完整携带这两个值，一旦加字段即可透传落库。

---

## P1-4 entity `aliases` 是否独立列

| 项 | 内容 |
| --- | --- |
| 现状 | B 将别名并入 `attributes` JSONB（`world_entities.attributes` 为 TEXT 存 JSON） |
| 建议 | **不加列** |
| 依据 | 别名仅用于展示/检索增强，无独立查询语义；确需检索时对 `attributes` 建 GIN 索引，比拆列更灵活且不锁 schema |
| 落地 | 无 DDL 变更。B 继续写 `attributes.aliases`（字符串数组） |
| 遗留 | 若 C 前端需要"按别名精确查找实体"，届时再加 GIN：`CREATE INDEX idx_we_attrs_gin ON world_entities USING gin ((attributes::jsonb))` |

---

## P1-5 事实唯一键（**前提已变，需重新拍板**）

| 项 | 内容 |
| --- | --- |
| 现状 | 纯应用层按 `(user_id, source_id, subject_id, predicate, valid_from)` 判重，DDL 无唯一约束 |
| ⚠️ 关键前提更正 | 移交清单原写"`valid_from` 可空"——**实际 DDL 是 `valid_from TIMESTAMP NOT NULL`**，且 A 落库时 null 会补 `Instant.now()`（`WorldFactServiceImpl:57`）。这意味着同一份文档重抽时 `valid_from` 每次不同，**含 `valid_from` 的唯一键根本挡不住重复插入**，判重形同虚设 |
| 建议（二选一） | **方案 A（推荐）**：唯一键不含 `valid_from`，改用 `(user_id, source_id, subject_id, predicate)`；同源同主体同谓词视为同一事实，重抽走 upsert/链式失效<br>**方案 B**：保留 `valid_from`，但要求 B 抽取必须传稳定 `valid_from`（schema 现为可选），且 A 落库改为"null 时取文档上传日期"而非 `now`，保证幂等 |
| 方案 A 的 DDL | ```sql\nCREATE UNIQUE INDEX uk_wf_src_fact ON world_facts (user_id, source_id, subject_id, predicate);\n``` |
| 风险 | 方案 A 会合并"同源同谓词但语义上确属多条"的场景（如一份合同多条付款节点）——需确认此类是否存在。若无，A 更优 |
| B 侧配合 | 若选 B，B 在抽取输出里保证 `valid_from` 非空且稳定（缺失时用文档日期兜底），并已在 schema 文档标注 |

> **B 的倾向**：方案 A。理由：抽取语义上"同一来源、同一主体、同一谓词"本就是同一事实的不同版本，版本交给 `status/valid_from` 链式失效表达，而非并存多行。这也与 A 已实现的 `update`→`superseded` 链式语义一致。

---

## P1-6 `world_risks` 关联证据事实

| 项 | 内容 |
| --- | --- |
| 现状 | B 风险输出 `DetectedRisk.evidenceFactIds: List<Long>`（一条风险可对应多条事实）；`world_risks` 现仅 `entity_id` 单列，无事实关联 |
| 建议 | **建关联表，不加数组列** |
| 依据 | 一对多关系；数组列违反范式、无法反查"某事实影响了哪些风险"，也无法加外键 |
| 落地 DDL | ```sql\nCREATE TABLE IF NOT EXISTS world_risk_facts (\n    risk_id BIGINT NOT NULL REFERENCES world_risks(id),\n    fact_id BIGINT NOT NULL REFERENCES world_facts(id),\n    PRIMARY KEY (risk_id, fact_id)\n);\nCREATE INDEX IF NOT EXISTS idx_wrf_fact ON world_risk_facts (fact_id);\n``` |
| A 侧配合 | 风险入库时遍历 `evidenceFactIds` 批量写关联表；`evidenceFactIds` 为空时跳过（`ScheduleConflictRule` 即返回空列表） |
| B 侧配合 | 无需改；`evidenceFactIds` 已全量携带真实 `world_facts.id` |

---

## P1-7 实体跨文档合并的 name 归一化

| 项 | 内容 |
| --- | --- |
| 现状 | 应用层按 `(user_id, entity_type, name)` 判重；`name` 原样比对 |
| 问题 | "华为" / "华为 " / "ＨＵＡＷＥＩ" / "huawei" 会被判为不同实体，产生重复 |
| 建议 | 保持一致（不拆列），但**判重前对 name 做归一化**，且该规则由 A 侧服务统一实现（写入与查询同一套） |
| 归一化规则（B 建议，可执行） | 1. `trim()` 去首尾空白（含全角空格 `\u3000`）<br>2. 全角→半角（NFKC 标准化）<br>3. 连续空白折叠为单个半角空格<br>4. 英文 `toLowerCase()`（中文不受影响）<br>5. 归一化结果仅用于**比对/判重**，`world_entities.name` 仍存原始展示名 |
| 注意 | 不要对中文做繁简转换（超范围且易误判）；归一化值可不落库，实时计算比对即可（数据量小），量大再补 `name_normalized` 列 + 索引 |
| B 侧配合 | B 抽取的 `subjectName` 保持原文；判重归一化在 A 侧实体创建时统一做，B 不重复实现 |

---

## 汇总：A 需拍板的四件事

| # | 议题 | B 建议 | 是否需 DDL |
| --- | --- | --- | --- |
| **P0** | 事实 confidence/factStatus 落库 | `FactCreateRequest` 加两字段，去除硬编码 | 否（仅代码） |
| 4 | aliases | 不加列 | 否 |
| 5 | 事实唯一键 | 方案 A：`(user_id, source_id, subject_id, predicate)` 唯一索引 | 是 |
| 6 | risk 证据事实 | 建 `world_risk_facts` 关联表 | 是 |
| 7 | name 归一化 | 保持一致 + A 侧统一归一化规则 | 否（量大再补列） |

拍板后 B 侧仅需响应"是否要改抽取输出映射"，其余均在 A 侧 WorldFact/Entity/Risk 服务内实现。
