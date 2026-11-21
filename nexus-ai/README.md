# nexus-ai（成员 B）

AI 与智能引擎交付目录：测试资料集、抽取结果样例、联调说明。
后端实现位于 `nexus-backend/src/main/java/com/nexus/ai/`（B 独占包）。

## 目录

| 路径 | 说明 |
| --- | --- |
| `testdata/invoice.pdf` | 发票样例（PDF，真实验收用 OCR/PDF 转图链路） |
| `testdata/subscription.txt` | 订阅确认样例（TXT，可直接抽取） |
| `testdata/study-plan.png` | 学习计划样例（图片，真实验收用 OCR） |
| `testdata/generate_invoice_pdf.py` | 发票 PDF 生成脚本（Python 标准库） |
| `testdata/GenerateStudyPlan.java` | 学习计划图片生成脚本（JDK 单文件运行） |
| `testdata/extraction-result.json` | Demo 运行产物（不入库，可删除） |

## 运行抽取 Demo

工作目录：`nexus-backend`。

### 本地链路验证（无云凭证）

```
mvn.cmd spring-boot:run -Dspring-boot.run.arguments="--nexus.ai.mode=mock --nexus.ai.demo.enabled=true --nexus.ai.demo.input=../nexus-ai/testdata/subscription.txt"
```

mock 模式用确定性正则抽取，仅验证「原文 → Schema JSON → DTO」链路，**不能作为华为云能力证明**。
mock OCR 只解码纯文本 base64，不识别图片内容。

### 真实华为云调用

先设置环境变量（凭据只走环境变量，禁止写入仓库）：

```
set NEXUS_AI_MODE=real
set NEXUS_AI_MAAS_BASE_URL=https://maas-api.<region>.myhuaweicloud.com
set NEXUS_AI_MAAS_API_KEY=<MaaS API Key>
set NEXUS_AI_MAAS_MODEL=<模型名>
set NEXUS_AI_OCR_IAM_ENDPOINT=https://iam.<region>.myhuaweicloud.com
set NEXUS_AI_OCR_ENDPOINT=https://ocr.<region>.myhuaweicloud.com
set NEXUS_AI_OCR_USERNAME=<IAM 用户名>
set NEXUS_AI_OCR_PASSWORD=<IAM 密码>
set NEXUS_AI_OCR_DOMAIN_NAME=<账号名>
set NEXUS_AI_OCR_PROJECT_ID=<项目 ID>
```

然后运行：

```
mvn.cmd spring-boot:run -Dspring-boot.run.arguments="--nexus.ai.demo.enabled=true --nexus.ai.demo.input=../nexus-ai/testdata/subscription.txt"
```

图片样例把 input 换成 `../nexus-ai/testdata/study-plan.png`；PDF 需先转图片（阶段二随 OBS 接入补齐）。

## 配置项（nexus.ai.*）

| 配置 | 环境变量 | 说明 |
| --- | --- | --- |
| `nexus.ai.mode` | `NEXUS_AI_MODE` | `real`（默认）/ `mock` |
| `nexus.ai.maas.base-url` | `NEXUS_AI_MAAS_BASE_URL` | 只填域名，代码追加 `/v1/chat/completions` |
| `nexus.ai.maas.api-key` | `NEXUS_AI_MAAS_API_KEY` | MaaS API Key |
| `nexus.ai.maas.model` | `NEXUS_AI_MAAS_MODEL` | 模型名 |
| `nexus.ai.maas.temperature` | `NEXUS_AI_MAAS_TEMPERATURE` | 默认 0.1 |
| `nexus.ai.ocr.*` | `NEXUS_AI_OCR_*` | IAM 用户名/密码/账号名/项目 ID |

## 契约

抽取输出以 `docs/extraction-schema.md` 为准；与 A 的 DTO 映射说明见该文档第 10、12 节。