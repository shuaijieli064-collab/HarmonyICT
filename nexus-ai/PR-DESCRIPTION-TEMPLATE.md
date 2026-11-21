# PR 描述模板：feature/ai-b → develop（契约整合）

> 用途：`gh` 未认证时，可复制到 GitHub 网页创建 PR 的正文框；认证后也可作为 `gh pr create --body-file` 的输入。
> 注意：按 `docs/00`，合并需三方确认；本 PR 含 A 的后端骨架（先前已 merge 进 ai-b）+ B 的 AI 实现与契约文档。

---

## 标题（建议）

```
[ai-b] AI 智能引擎阶段一~四交付 + 抽取契约对齐（待三方确认）
```

## 正文

### 本 PR 包含

| 模块 | 内容 | 提交 |
| --- | --- | --- |
| 阶段一 | MaaS/OCR Provider 接口实现、抽取 Schema、文档抽取 Demo、测试资料集 | `f38226b` `2f0422a` |
| 阶段三 | 4 类风险规则引擎（期限/到期/日程冲突/预算） | `4e8e34c` |
| 阶段四 | Agent 工具注册 + 编排（L0-L2 白名单） | `4e8e34c` |
| 缺口修复 | 抽取输出全链路补齐 `sourceId`，验收项 3 可追溯 | `2e3b321` |
| 契约文档 | schema 第 12 节 11 项 DDL 对齐；给 A 的契约差异移交清单 | `2e3b321` `eb4dd5f` |
| 验收工具 | 真实 MaaS/OCR 调用脚本 + 凭据模板（无密钥入库） | `eb4dd5f` |

### 验证结果

- `mvn test`：**22/22 通过**（Agent 6 / 抽取 6 / 规则抽取 3 / 风险 7）
- mock Demo：facts=3、overallConfidence=0.91，entity + 3 fact + 1 event 均带 `sourceId`
- 边界合规：只改 `com/nexus/ai` 包 + `nexus-ai/` + `docs/extraction-schema.md`，未触碰 A/C 文件

### 需三方确认的契约差异（详见 `nexus-ai/CONTRACT-HANDOFF-TO-A.md`）

- **P0-1** `ExtractionRequest` DTO 增加 `String source_id`（现 B 用 `extra.sourceId` 透传）
- **P0-2** `world_facts` 补 `value_type/unit/normalized_value/fact_status/evidence`（现随 `extra` 透传，不落库）
- **P0-3** `world_sources` 视去重需求补 `sha256/page_count/uploaded_at`
- **P1** aliases / 事实唯一键 / `world_risks` 事实关联 / 实体跨文档合并 4 项待联调确认

### 合入前检查清单

- [ ] A 确认 DTO/DDL 差异处理方案（改 or 不改，本 PR 还是后续 PR）
- [ ] A、C review 通过
- [ ] `develop` 当前仍停在 `c883022`，本 PR 会一并带入 A 的后端骨架（来自 `feature/backend-a`）
- [ ] 真实 MaaS/OCR 调用验收因缺华为云凭据暂缓（P0 阻塞，凭据到位后补）
