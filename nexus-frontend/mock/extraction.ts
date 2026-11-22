/* Mock 数据：上传后 AI 抽取产生的事实（契约 docs/api-specification.md 5.2）。
 * 联调时替换为 services/upload.ts 轮询 GET /api/v1/facts?sourceId=...&status=active
 * 的真实返回，本文件仅用于 Mock 抽取流程（规范 3.3：Mock 集中存放并标注）。
 */

import type { Fact } from "@/types";

const MOCK_TIME = "2026-11-21T08:00:00Z";
const MOCK_SUBJECT_ID = "194800000000000100";
const MOCK_SOURCE_ID = "194800000000000200";

export const MOCK_UPLOADED_FACTS: Fact[] = [
  {
    id: "194800000000000001",
    subjectId: MOCK_SUBJECT_ID,
    predicate: "monthly_income",
    objectValue: "12000",
    valueType: "number",
    unit: "元",
    normalizedValue: "12000",
    evidence: "每月工资流水 12000 元",
    sourceId: MOCK_SOURCE_ID,
    confidence: 0.95,
    validFrom: "2026-11-01T00:00:00Z",
    validUntil: null,
    status: "active",
    factStatus: "confirmed",
    createdAt: MOCK_TIME,
    updatedAt: MOCK_TIME,
  },
  {
    id: "194800000000000002",
    subjectId: MOCK_SUBJECT_ID,
    predicate: "fund_market_value",
    objectValue: "58000",
    valueType: "number",
    unit: "元",
    normalizedValue: "58000",
    evidence: "XX 基金 11 月对账单显示市值 58000 元",
    sourceId: MOCK_SOURCE_ID,
    confidence: 0.88,
    validFrom: "2026-11-15T00:00:00Z",
    validUntil: null,
    status: "active",
    factStatus: "inferred",
    createdAt: MOCK_TIME,
    updatedAt: MOCK_TIME,
  },
  {
    id: "194800000000000003",
    subjectId: MOCK_SUBJECT_ID,
    predicate: "pmp_cert_expiry",
    objectValue: "2027-01-20",
    valueType: "date",
    unit: null,
    normalizedValue: "2027-01-20",
    evidence: "证书扫描件显示有效期至 2027-01-20",
    sourceId: MOCK_SOURCE_ID,
    confidence: 0.9,
    validFrom: "2026-11-21T00:00:00Z",
    validUntil: null,
    status: "active",
    factStatus: "unverified",
    createdAt: MOCK_TIME,
    updatedAt: MOCK_TIME,
  },
  {
    id: "194800000000000004",
    subjectId: MOCK_SUBJECT_ID,
    predicate: "employment_status",
    objectValue: "在职",
    valueType: "string",
    unit: null,
    normalizedValue: "employed",
    evidence: "劳动合同显示当前在职，合同期至 2028-06",
    sourceId: MOCK_SOURCE_ID,
    confidence: 0.93,
    validFrom: "2026-11-21T00:00:00Z",
    validUntil: null,
    status: "active",
    factStatus: "confirmed",
    createdAt: MOCK_TIME,
    updatedAt: MOCK_TIME,
  },
];