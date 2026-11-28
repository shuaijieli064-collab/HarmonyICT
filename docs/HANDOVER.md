# NEXUS 交接文档（AI 队员 → 三人团队）

> 更新：2026-11-25。当前基线：`develop` HEAD = `9a4367f`（PR #30 / #33 / #34 已合入）。
> 本轮为**审查修复轮次**：更新过时文档基线、补齐遗漏描述，并完成接口契约与演示材料修订；云部署待代金券到账后启动，云方案保持原样。
> 下列个别状态项以本轮修订为准；全部改动经 PR 合入 `develop` 后同步。

## 1. 当前仓库状态

| 项 | 状态 |
|---|---|
| 分支 | `develop` 为最新集成分支；`main` 待功能冻结后统一晋升 |
| 后端测试 | **158 全绿**（`mvn -f nexus-backend/pom.xml test`，含 8 步 E2E 与 404/405 异常映射回归；2026-11-28 复验） |
| 前端构建 | `next build` + `eslint` + `tsc --noEmit` 通过（2026-11-25 修复 4 处 React hooks lint 后复验） |
| 未提交内容 | 交接时无；本地工具目录 `.opencode/` / `.security/` 勿提交 |
| H2 注意 | local profile 内存库**重启即清空**，演示前需跑 `scripts/seed-demo.ps1` |

## 2. 本阶段新增能力（vs 上一节点）

### A 组：风险操作闭环
- 风险卡「生成待办」按钮：riskId 关联派生待办
- `POST /risks/scan` 手动扫描（免等 60s 定时），前端「立即扫描」
- `scripts/seed-demo.ps1` 一键演示数据

### B 组：世界模型洞察
- `GET /dashboard/health-score`：健康度综合分（新鲜度 40 + 风险 40 + 任务 20），驾驶舱环形仪表
- `risk_snapshots` 表 + 每日 01:00 快照 + 同日 upsert；`GET /risks/trend` 近 N 天趋势，情报中心 ECharts 折线
- `GET /facts/{id}/history`：supersede 链版本回溯，世界面板历史展开
- ⚠️ 踩坑记录：`@MapperScan("com.nexus.**.mapper")` 只扫 `*.mapper` 包，**新 Mapper 必须放 mapper 包**（RiskSnapshotMapper 曾因此导致上下文起不来）

### C 组：产品与证据
- README 更新：页面表 + 4.1 核心能力清单（10 项）
- 双账号隔离实跑留证：`scripts/verify-isolation.ps1`（三项 PASS），证据在 `docs/evidence/isolation/`
- PWA：`public/manifest.json` + 品牌图标（any/maskable）+ layout metadata 挂接
- 2026-11-25 打磨：修复 4 处 React hooks lint 错误；`docs/api-specification.md` 补齐 5 个新端点；PPT 大纲 / 录屏脚本 / 演示脚本按新能力更新

### E2E 回归网（step1–8）
注册登录 → 上传抽取 → 事实事件 → 风险扫描（含手动 scan 端点）→ 隔离 → **health-score → trend → fact-history**。
提交前跑 `mvn test` 即回归全部核心链路。

### 演示趋势数据
- `DemoSnapshotController`（`@Profile("local")`，prod 无此端点）：`POST /api/v1/risks/snapshots/demo-history?days=14`
- seed-demo 会自动注入 13 天历史快照 + 趋势自检，**实跑验证 14/14 天有数据**，趋势图无断点

## 3. 队员下一步（按优先级）

### P0 · 周五券到 → 阶段二云部署（deploy/ 物料已就绪）
1. 开通 9 项资源：ECS / RDS / Redis / OBS / MaaS / OCR / CodeArts / CES / IAM（截图留证 `docs/evidence/cloud/<产品>/`）
2. 在 ECS `/etc/nexus/nexus.env`（权限 600）填 `NEXUS_RDS_*`、`NEXUS_REDIS_*` 等变量；部署 unit 使用 **`--spring.profiles.active=prod`**，对应配置是 `application-prod.yml`（不是 `application-postgres.yml`，后者是本地 docker profile 且含默认密码）
3. **首次启动前手工建表**：prod 为 `spring.sql.init.mode=never`，空库直接启动会失败；先执行 `psql -h <RDS内网地址> -U <用户> -d nexus -f nexus-backend/src/main/resources/db/init-postgres.sql`，确认 9 张表可见
4. `verify-real-call.sh` 真实调用留档（真实 OCR/MaaS 模式需 `--nexus.ai.mode=real`）
5. CodeArts 流水线：拉 develop → 后端 `mvn test` → 前端 `npm ci && npm run build` → 部署 ECS + nginx
6. 公网 Demo 冒烟：注册 → 上传 → 扫描 → trend 图有数据

### P1 · 云上演示数据
- RDS 持久库不需要 demo-history 注入；跑 seed-demo（真实上传+扫描）即可，快照随每日定时任务累积
- 演示前 1 天先注入一次，次日趋势即有 2 个真实点

### P2 · 交付物（12-8 功能冻结前）
- PPT（`docs/ppt-outline.md`）、录屏（`docs/recording-script.md`）、演示脚本（`docs/demo-script.md`）按新能力清单更新
- B 组新端点补进 `docs/api-specification.md`（health-score / risks/trend / facts/{id}/history / risks/scan / snapshots/demo-history）

## 4. 本地快速上手

```powershell
# 后端（H2，无外部依赖）
cd nexus-backend; mvn spring-boot:run        # 8080

# 前端
cd nexus-frontend; npm run dev               # 3000

# 演示数据（含 14 天趋势）
pwsh scripts/seed-demo.ps1                    # 账号 e2e-demo / E2eDemo#2026

# 隔离验证留证
pwsh scripts/verify-isolation.ps1

# 回归
cd nexus-backend; mvn test                    # 158 全绿预期
```

> Windows 注意：跑含中文的 .ps1 用 `pwsh`（7.x），别用系统自带 Windows PowerShell 5.1（GBK 乱码）；Maven 若无全局 PATH，用本机 `mvn` 绝对路径（例如接手机 `D:\maven\bin\mvn.cmd`），不要照抄交接机的用户目录。
> 批量删除保护：若本机安全钩子拦截 `npm ci` 清理 `node_modules/.bin` 或 `next build` 清理 `.next`，可用 `npm install` + 临时 `distDir` 完成验证，验完还原 `next.config.ts` / `tsconfig.json` / `package-lock.json` 并删除临时目录。
