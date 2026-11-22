# 抽取入库编排契约（B → A）

> 用途：A 的 `ingestion` 编排服务调用 B 的 OCR/抽取 Provider 时的对接说明。
> 最后更新：2026-11-22（基于 develop `e799553`；对应 A 正在编写的编排服务）

---

## 1. 现状：Provider 已就绪，缺生产消费方

- `HuaweiOcrProvider.recognize(OcrRequest)` —— 已实现，支持图片与 **PDF 逐页转图识别**（见第 4 节）。
- `DefaultExtractionProvider.extract(ExtractionRequest)` —— 已实现，内部完成 Prompt → LLM → Schema 校验 → DTO 映射，**含重试与 90011/90012/90013 错误码**。
- `ingestion` 包目前只有上传/存储（`SourceServiceImpl`），**没有任何地方调用这两个 Provider** —— 即"无生产消费方"。编排服务需补上"A 上传成功 → 触发抽取 → 落库 → 更新 source.status"的闭环。

---

## 2. 编排服务应如何驱动 B 的链路

推荐顺序（A 侧 Service 内）：

```
upload(pending) → [编排] 读文件字节 → OcrProvider.recognize
                → ExtractionProvider.extract(sourceId + fullText)
                → 映射 ExtractedFact → 建实体(归一化判重) → FactCreateRequest 落库
                → source.status = parsed / failed
```

### 2.1 入参：OcrRequest

| 字段 | 编排服务填什么 | 说明 |
| --- | --- | --- |
| `contentBase64` | 文件字节的 base64 | 阶段一/二本地联调用这个；`obsKey` 目前 OCR 侧未启用 |
| `obsKey` | 可空 | 传了也不会被读取（华为 OCR 走 base64 单图/逐页图） |
| `mimeType` | `image/png` / `image/jpeg` / `pdf` / `text/plain` | 决定是否走 PDF 转图分支 |

### 2.2 出参：OcrResult

| 字段 | 用途 |
| --- | --- |
| `fullText` | 直接作为 `ExtractionRequest.sourceText` 传入抽取 Provider |
| `blocks` | 含**真实页码**（PDF 逐页时为实际页号），供前端按页高亮原文 |
| `confidence` | 整体识别置信度，可用于来源质量提示 |

### 2.3 入参：ExtractionRequest

| 字段 | 编排服务填什么 |
| --- | --- |
| `sourceId` | **必填**，A 上传接口返回的雪花数字字符串；缺失会抛 90011 |
| `sourceText` | `OcrResult.fullText` |
| `schemaHints` | 如 `["device","subscription","goal","task"]`，可空（空则"按资料类型自动判断"） |

### 2.4 出参：ExtractionResult → 落库映射

`ExtractionResult.facts` 是 `ExtractedFact(subjectType, subjectName, predicate, objectValue, confidence, extra)`。
映射到 `FactCreateRequest` 时：

| FactCreateRequest 字段 | 取值来源 |
| --- | --- |
| `subjectId` | **需先按 `(userId, entityType, name)` 归一化判重建/取实体**，拿 `world_entities.id` |
| `predicate` / `objectValue` | `ExtractedFact` 同名 |
| `confidence` | `ExtractedFact.confidence()`（**已由 A 的 PR#5 支持落库，不再硬编码 1.0**） |
| `factStatus` | `extra.get("factStatus")`（B 已做三态白名单，非法值降级 `unverified`） |
| `valueType`/`unit`/`normalizedValue`/`evidence`/`validFrom`/`validUntil` | 同名 `extra` 键 |
| `sourceId` | `Long.valueOf(request.sourceId())` |

> ⚠️ 关键点：`ExtractedFact` 用的是 **`subjectName` + `subjectType`**，不是 `subjectId`。编排服务必须先做实体归一化判重（A 的 `EntityNameNormalizer`），拿到真实 `subjectId` 再建事实，否则无法落库。这一步在 A 的编排里完成，B 不重复实现。

---

## 3. 错误处理与 source.status 流转

| 场景 | Provider 行为 | 编排服务应做 |
| --- | --- | --- |
| 缺 sourceId | 抛 `EXTRACTION_NO_SOURCE(90011)` | `source.status=failed` |
| Schema 不符（重试后仍失败） | 抛 `EXTRACTION_SCHEMA(90012)` | `failed`，记录原因 |
| 谓词未登记 | 抛 `EXTRACTION_PREDICATE(90013)`，不重试 | `failed` |
| OCR 未配置 / 调用失败 | 抛 `INTERNAL_ERROR` | `failed` |
| 正常 | 返回 facts | `parsed`，facts 落库 |

`world_sources.status` 合法值：`pending / parsing / parsed / failed`（B 只读，由编排服务写）。

---

## 4. PDF 转图支持（阶段二遗留，已补齐）

### 4.1 实现方式

`PdfPageRenderer`（`com.nexus.ai.ocr`）用**反射**调用 Apache PDFBox：
- 3.x：`org.apache.pdfbox.Loader.loadPDF(byte[])`
- 2.x：`org.apache.pdfbox.pdmodel.PDDocument.load(byte[])`
- 逐页 `PDFRenderer.renderImageWithDPI(page, 200)` → PNG base64

`HuaweiOcrProvider` 遇到 `mimeType=pdf` 时：逐页转图 → 逐页调华为 OCR → 合并全文、保留真实页码。

### 4.2 需要 A 配合的唯一事项：加依赖

`pom.xml` 属 A 侧独占文件，B 未擅自修改。**请在 `nexus-backend/pom.xml` 加入 PDFBox：**

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>2.0.31</version>
</dependency>
```

- 加依赖前：PDF 调用会抛 `BizException`，提示"需要 Apache PDFBox 依赖"，**编译与其它测试不受影响**（反射解耦，B 侧可独立构建）。
- 加依赖后：PDF 转图自动生效，**B 侧代码零改动**。

### 4.3 约束与已知边界

- 渲染 DPI=200，兼顾文字清晰度与华为 OCR 单图体积上限；超大 PDF（>50 页）建议编排侧限制页数或分批。
- 加密 PDF / 扫描件（无文字层）依赖 OCR 本身，与转图无关。
- `page_count` 可由 `PdfPageRenderer` 的页数回填到 `world_sources.page_count`（编排侧可选）。

---

## 5. 待 A 确认的两点

1. **实体判重时机**：编排服务在"建事实前"按 `EntityNameNormalizer` 判重取 `subjectId`，确认这样符合 A 的 `WorldEntityServiceImpl.create` 抛 `20104` 的语义（重抽时应"取已有"而非"再建导致 20104"）。
2. **source.status 写入责任**：确认 `pending → parsing → parsed/failed` 由编排服务写，B 侧只读不写（与 `SourceService` 接口注释一致）。
