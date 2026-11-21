# NEXUS · GitHub 协作与整合约定（三人共用）

> 三人各自在自己的 feature 分支开发，通过 GitHub PR 合并到 develop 完成整合。**契约文档是整合的核心，先定契约再并行。**

---

## 1. 仓库与分支

| 项 | 约定 |
| --- | --- |
| 远程仓库 | GitHub 统一仓库 `NEXUS`（owner 由团队定，三人同一仓库） |
| main | 稳定版本，仅发布时合并 |
| develop | 日常集成分支，所有 PR 的目标分支 |
| 成员分支 | A: `feature/backend-a`　B: `feature/ai-b`　C: `feature/frontend-c` |
| 提交纪律 | 禁止直接 push develop/main；每人只在自己的分支提交，通过 PR 合并 |

## 2. 目录边界（避免 git 冲突，严格各写各的）

| 成员 | 独占目录 / 文件 | 禁止触碰 |
| --- | --- | --- |
| A | `nexus-backend/`（除 `ai/` 包）、`docker/`、`docs/database-design.md`、`docs/api-specification.md`、部署脚本 | `nexus-frontend/`、`ai/` 包内的实现 |
| B | `nexus-backend/src/.../ai/**`（仅此包）、`nexus-ai/`（如需独立）、`docs/extraction-schema.md`、`docs/ai-evaluation.md` | 后端其他包、前端、`api-specification.md` |
| C | `nexus-frontend/`、`docs/design-system.md`、`docs/user-manual.md`、演示素材 | 后端全部、`docs/database-design.md` |

> A 负责建立 `ai/` 包骨架与接口定义（仅签名），B 在该包内写实现；A 不读改 B 的实现文件。

## 3. 整合点（契约驱动，第一周第 2-3 天定稿）

| 契约文件 | 主笔 | Review | 作用 |
| --- | --- | --- | --- |
| `docs/database-design.md` | A | B | 世界模型表结构，A/B 共享的数据基础 |
| `docs/api-specification.md` | A | C | 前后端整合点，C 据此写 Mock 先行开发 |
| `docs/extraction-schema.md` | B | A | AI 抽取结果 → 数据库事实字段的映射 |
| `docs/ai-provider.md` | B | A | 模型/OCR 调用接口定义，业务层只依赖接口 |

**契约变更必须同步给另外两人，并经 PR 合并后再改代码。**

## 4. Commit 与同步约定

- Commit 格式：`[A/B/C] 类型: 描述`，例如 `[A] feat: 初始化 Spring Boot 工程`。
- 每天开工前：`git fetch origin && git rebase origin/develop`，同步他人进度。
- 每天收工前：push 自己的 feature 分支（不要求合并）。
- 遇到跨成员依赖：在群里说明"我依赖 XX 契约的 YY 字段"，不要自行假设。

## 5. 整合节奏

- 每天：简短同步进度与阻塞。
- 每个契约定稿：单独发一次 PR，三人确认后合并 develop。
- 第 2 周起：每周一次端到端联调（在 develop 上验证闭环）。
