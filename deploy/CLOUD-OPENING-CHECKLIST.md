# NEXUS 华为云资源开通 Checklist（等券期间准备）

> 用途：第九节分派的「预演 MaaS 操作路径 + 开通步骤与截图位」。
> 前置：代金券 11-25 发放；区域统一 `cn-north-4`（方案 3.1）；所有真实凭据只填本机 env，不入库不进对话。
> 截图位口径：`docs/evidence/` 由前端线建立命名规范，本清单只列「应留证的动作」，实际文件名按前端线规范落盘，由主控统一归档（第九节主控职责①）。

## 1. 券到后执行顺序（总览）

```
① 9 项资源开通（逐项截图） → ② 填 env（本机，600） → ③ 手工执行 init-postgres.sql
→ ④ 本地 verify-real-call.sh（真云抽取跑通） → ⑤ CodeArts 流水线 → ⑥ ECS 部署 → ⑦ 公网验收清单
```

关键顺序说明：③ 必须在 ⑥ 之前——prod profile 的 `spring.sql.init.mode=never`，空库直接启动会失败（方案 5 节）。

## 2. 九项资源逐项 Checklist

| # | 资源 | 开通要点（控制台路径） | 凭据落点 | 截图位（应留证动作） | 完成 |
| --- | --- | --- | --- | --- | --- |
| 1 | CodeArts | 开通免费版 → 新建 Scrum 项目 NEXUS → 编译构建新建任务，代码源选 GitHub `develop` | 控制台授权（无 env 变量） | 项目首页 + 代码源绑定成功页 | ☐ |
| 2 | ECS + EIP | 2vCPU/4GiB、系统盘 ≥80GiB、Ubuntu/CentOS，绑定 EIP；演示用 EIP:8080 规避备案（方案 4 节） | SSH 密钥（本机保管） | 实例列表页 + EIP 绑定页 | ☐ |
| 3 | VPC / 安全组 | 默认 VPC；安全组放行 22 / 80 / 443 / 8080 | — | 安全组入方向规则页 | ☐ |
| 4 | RDS PostgreSQL | 基础版、同区域、记录内网地址与端口 | `NEXUS_RDS_HOST/PORT/DB/USER/PASSWORD` | 实例详情页（内网地址）+ 账号创建页 | ☐ |
| 5 | Redis | 基础版 1GB 起、同区域 | `NEXUS_REDIS_HOST/PORT/PASSWORD` | 实例详情页（内网地址）+ 连接信息页 | ☐ |
| 6 | OBS 桶 | 标准存储、同区域、桶名全局唯一 | `NEXUS_OBS_ENDPOINT/BUCKET` | 桶列表页（名称 + 区域） | ☐ |
| 7 | MaaS | 模型广场开通文本模型（GLM/Kimi 等）→ 拿 API Key → **看「API调用」面板确认 v1/v2**（见第 3 节） | `NEXUS_AI_MAAS_BASE_URL/API_KEY/MODEL/COMPLETION_PATH` | 模型开通页 + **API调用面板 URL 全貌** + API Key 创建页 | ☐ |
| 8 | OCR | 开通通用文字识别；建专用 IAM 用户（最小权限） | `NEXUS_AI_OCR_IAM_ENDPOINT/ENDPOINT/USERNAME/PASSWORD/DOMAIN_NAME/PROJECT_ID` | 服务开通页 + IAM 用户权限页 | ☐ |
| 9 | IAM AK/SK | 专用于 OBS 的 IAM 用户，最小权限 | `NEXUS_OBS_AK/SK` | IAM 用户列表页 + AK/SK 创建确认页（值不截图） | ☐ |

**截图红线**：API Key、AK/SK、密码的「值」一律不截图、不回显；只截创建成功提示与列表页名称。

## 3. MaaS 控制台操作路径预演（v1/v2 判定）

### 3.1 为什么要预演
代码已把 path 改为配置化：`completionPath` 默认 `/v1/chat/completions`，`application-prod.yml` 默认按新版填 `/v2/chat/completions`。
华为云中国站存在两代端点，**path 填错必 404**（方案 3.2）。券到后以控制台实际 URL 为准，不靠猜。

### 3.2 操作路径
1. 进入 MaaS（ModelArts Studio）控制台 → 模型广场 → 开通任一文本模型。
2. 打开该模型的「API调用」面板，**完整复制**给出的调用地址（含域名与 path）。
3. 按第 3.3 节判定，把 URL 拆成 `baseUrl`（只到域名）+ `completionPath`（path）。
4. 记下模型名（面板示例里的 `model` 值），填 `NEXUS_AI_MAAS_MODEL`。

### 3.3 判定决策树

| 控制台 API调用面板的 URL 形态 | `NEXUS_AI_MAAS_BASE_URL` | `NEXUS_AI_MAAS_COMPLETION_PATH` |
| --- | --- | --- |
| `https://api.modelarts-maas.com/v2/chat/completions`（新版全局端点） | `https://api.modelarts-maas.com` | `/v2/chat/completions` |
| `https://maas-api.<区域>.myhuaweicloud.com/v1/chat/completions`（旧版区域端点） | `https://maas-api.<区域>.myhuaweicloud.com` | `/v1/chat/completions` |
| 其他形态 | 按「域名归 baseUrl、path 归 completionPath」拆分，**照抄控制台，不套默认** | 同左 |

代码侧已兼容三种写法：path 漏写前导斜杠会自动补，baseUrl 结尾斜杠会去掉，path 空串回退 `/v1`（不要依赖回退，显式填）。

### 3.4 券到前可做的离线预演
用 `curl` 对候选端点发一次**无凭据**请求，观察 HTTP 状态码即可验证 path 是否存在（无需券）：

```bash
# 新版端点探测：404 表示路径不存在，401/403 表示路径存在但缺鉴权（说明 path 对了）
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  https://api.modelarts-maas.com/v2/chat/completions \
  -H 'Content-Type: application/json' -d '{}'

# 旧版端点探测
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  https://maas-api.cn-southwest-2.myhuaweicloud.com/v1/chat/completions \
  -H 'Content-Type: application/json' -d '{}'
```

**离线实测（2026-11-24）判据修正**：
- 新版 `https://api.modelarts-maas.com/v2/chat/completions`：空 body 返回 **HTTP 400**（服务已响应，说明 path 存在、仅缺必填参数）→ 新版 path 可用。
- 旧版 `https://maas-api.cn-southwest-2.myhuaweicloud.com/v1/chat/completions`：**HTTP 000 不可达**（本机网络/代理不通该区域域名，非 path 错误证据）。
- 判据统一为：**400 / 401 / 403 = 路径存在**（缺参数或缺鉴权而已）；**404 = 路径错误**，换另一版；**000 = 网络不通**，不能作为 path 判定依据，需换网络环境重测。
- 此探测不含任何密钥，可安全在对话中执行；本机对旧区域域名不可达，券到后在 ECS 上复测旧端点。

## 4. 券到后逐阶段验收动作

| 阶段 | 动作 | 通过标准 | 截图位 |
| --- | --- | --- | --- |
| ① 资源 | 上表 9 项全开通 | 每项控制台可见 | 每项 1~2 张 |
| ② 凭据 | 填本机 env（不入库） | `git grep` 无明文 | 无（禁止截取值） |
| ③ 建表 | 手工执行 `init-postgres.sql` | RDS 可见 9 张表 | psql 执行成功回显 |
| ④ 真调用 | `./verify-real-call.sh real-call.env` | 抽取 Demo 完成日志 + schema 校验通过 | 终端输出（脱敏） |
| ⑤ 流水线 | CodeArts 构建任务跑通，产物归档 | jar/前端包产出 | 构建成功页 |
| ⑥ 部署 | `nexus-deploy.sh install/upload/start/health` | health 全绿 | 部署脚本输出 + systemd status |
| ⑦ 公网 | 上传→OCR→MaaS→落库→风险→Agent 走一遍 | Demo 链接可访问，全链路可演示 | 页面 + 调用日志 |

## 5. 当前可勾选的「已完成」

- [x] 6 项云改造代码 + deploy/ 资产（`8f428f3`，87 测试全绿）
- [x] env 字段与方案第 7 节核对（`deploy/ENV-VARIABLE-MAPPING.md`）
- [x] MaaS v1/v2 判定路径预演（本节第 3 节）
- [x] 9 项开通步骤与截图位整理（本节第 2 节）
- [ ] 券到：9 项资源开通
- [ ] 券到：真云抽取跑通 + 公网 Demo 链接与截图
