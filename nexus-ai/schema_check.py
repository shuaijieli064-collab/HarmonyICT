#!/usr/bin/env python3
"""抽取输出 Schema 合规校验（成员 B，标准库实现，无第三方依赖）。

校验依据：docs/extraction-schema.md 第 3/4/9 节。
- 文档第 9 节的 JSON Schema 使用 snake_case；
- 线上 Prompt / mock 输出使用 camelCase（schemaVersion、entityId、factId...）。
  本脚本先把 camelCase 归一化为 snake_case 再校验，从而能同时校验两种线格式，
  并把"字段命名风格"作为独立观察项输出，不与结构合规混为一谈。

用法：
    python schema_check.py <抽取结果.json> [--source-id <雪花ID>]

退出码：0 全部通过；1 存在阻断级违规；2 文件/参数错误。
"""

import json
import re
import sys

TOP_LEVEL_REQUIRED = [
    "schema_version", "extraction_id", "source", "language",
    "document_type", "entities", "facts", "events", "tasks",
    "warnings", "extracted_at",
]

DOCUMENT_TYPES = {"invoice", "subscription", "study_plan", "warranty", "contract", "receipt", "other"}
ENTITY_TYPES = {"device", "subscription", "goal", "task", "course", "organization",
                "person", "account", "asset", "project", "other"}
EVENT_TYPES = {"purchase", "subscription_start", "subscription_renewal", "subscription_expiry",
               "warranty_expiry", "deadline", "payment", "class", "exam", "other"}
PREDICATES = {
    "name", "purchased_at", "price", "currency", "merchant_name", "brand", "model",
    "serial_number", "order_id", "warranty_period", "warranty_until", "subscription_cycle",
    "subscription_start_at", "subscription_renewal_at", "subscription_end_at",
    "subscription_price", "auto_renew", "goal_target", "goal_deadline", "course_name",
    "course_schedule", "task_due_at", "task_status", "project_deadline", "location",
    "budget_limit",
}
VALUE_TYPES = {"string", "number", "date", "datetime", "duration", "boolean"}
FACT_STATUSES = {"confirmed", "inferred", "unverified"}
SNOWFLAKE = re.compile(r"^[1-9][0-9]{17,18}$")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
DATETIME = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$")

SNAKE_CACHE = {}


def to_snake(key):
    """camelCase → snake_case（已是 snake_case 的原样返回）。"""
    if key in SNAKE_CACHE:
        return SNAKE_CACHE[key]
    converted = re.sub(r"(?<!^)(?=[A-Z])", "_", key).lower()
    SNAKE_CACHE[key] = converted
    return converted


def normalize(node):
    """递归把对象键统一为 snake_case；数组逐项处理。"""
    if isinstance(node, dict):
        return {to_snake(k): normalize(v) for k, v in node.items()}
    if isinstance(node, list):
        return [normalize(item) for item in node]
    return node


def check_confidence(value, path, violations):
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        violations.append(f"{path}: confidence 必须是 0-1 的数字，实际 {value!r}")
    elif value < 0 or value > 1:
        violations.append(f"{path}: confidence 越界 {value}")


def check_evidence(evidence, path, violations):
    if not isinstance(evidence, dict):
        violations.append(f"{path}.evidence: 缺失或不是对象")
        return
    snippet = evidence.get("text_snippet")
    if not isinstance(snippet, str) or not snippet.strip():
        violations.append(f"{path}.evidence.text_snippet: 必填且不能为空")
    elif len(snippet) > 200:
        violations.append(f"{path}.evidence.text_snippet: 超过 200 字（{len(snippet)}）")
    page = evidence.get("page")
    if page is not None and (not isinstance(page, int) or isinstance(page, bool) or page < 1):
        violations.append(f"{path}.evidence.page: 必须为 >=1 的整数")


def check_source_id(value, path, violations, expected_source_id):
    text = None if value is None else str(value)
    if not text or not SNOWFLAKE.match(text):
        violations.append(f"{path}: source_id 缺失或不是雪花数字串（实际 {value!r}）")
    elif expected_source_id and text != expected_source_id:
        violations.append(f"{path}: source_id={text} 与期望 {expected_source_id} 不一致")


def check_entity(entity, index, violations, expected_source_id):
    path = f"entities[{index}]"
    if not isinstance(entity, dict):
        violations.append(f"{path}: 不是对象")
        return
    if not re.match(r"^ent_[0-9]+$", str(entity.get("entity_id", ""))):
        violations.append(f"{path}.entity_id: 必须匹配 ent_<数字>，实际 {entity.get('entity_id')!r}")
    if entity.get("entity_type") not in ENTITY_TYPES:
        violations.append(f"{path}.entity_type: 不在枚举内 {entity.get('entity_type')!r}")
    if not (isinstance(entity.get("name"), str) and entity.get("name").strip()):
        violations.append(f"{path}.name: 必填")
    check_confidence(entity.get("confidence"), path, violations)
    check_source_id(entity.get("source_id"), path, violations, expected_source_id)
    check_evidence(entity.get("evidence"), path, violations)


def check_fact(fact, index, violations, expected_source_id):
    path = f"facts[{index}]"
    if not isinstance(fact, dict):
        violations.append(f"{path}: 不是对象")
        return
    if not re.match(r"^fact_[0-9]+$", str(fact.get("fact_id", ""))):
        violations.append(f"{path}.fact_id: 必须匹配 fact_<数字>，实际 {fact.get('fact_id')!r}")
    if not fact.get("subject_ref"):
        violations.append(f"{path}.subject_ref: 必填")
    if fact.get("predicate") not in PREDICATES:
        violations.append(f"{path}.predicate: 不在受控词表 {fact.get('predicate')!r}")
    if "object_value" not in fact:
        violations.append(f"{path}.object_value: 必填")
    if fact.get("value_type") not in VALUE_TYPES:
        violations.append(f"{path}.value_type: 不在枚举内 {fact.get('value_type')!r}")
    check_confidence(fact.get("confidence"), path, violations)
    if fact.get("fact_status") not in FACT_STATUSES:
        violations.append(f"{path}.fact_status: 不在三态内 {fact.get('fact_status')!r}")
    valid_from = fact.get("valid_from")
    if valid_from is not None and not DATE.match(str(valid_from)):
        violations.append(f"{path}.valid_from: 非 YYYY-MM-DD {valid_from!r}")
    valid_until = fact.get("valid_until")
    if valid_until is not None and not DATE.match(str(valid_until)):
        violations.append(f"{path}.valid_until: 非 YYYY-MM-DD {valid_until!r}")
    check_source_id(fact.get("source_id"), path, violations, expected_source_id)
    check_evidence(fact.get("evidence"), path, violations)


def check_event(event, index, violations, expected_source_id):
    path = f"events[{index}]"
    if not isinstance(event, dict):
        violations.append(f"{path}: 不是对象")
        return
    if not re.match(r"^event_[0-9]+$", str(event.get("event_id", ""))):
        violations.append(f"{path}.event_id: 必须匹配 event_<数字>，实际 {event.get('event_id')!r}")
    if event.get("event_type") not in EVENT_TYPES:
        violations.append(f"{path}.event_type: 不在枚举内 {event.get('event_type')!r}")
    if not (isinstance(event.get("title"), str) and event.get("title").strip()):
        violations.append(f"{path}.title: 必填")
    for field in ("start_at", "end_at", "due_at"):
        value = event.get(field)
        if value is not None and not DATETIME.match(str(value)):
            violations.append(f"{path}.{field}: 非 RFC3339 {value!r}")
    if not any(event.get(field) for field in ("start_at", "end_at", "due_at")):
        violations.append(f"{path}: 至少需要一个时间字段（start_at/end_at/due_at）")
    check_confidence(event.get("confidence"), path, violations)
    check_source_id(event.get("source_id"), path, violations, expected_source_id)
    check_evidence(event.get("evidence"), path, violations)


def check_task(task, index, violations, expected_source_id):
    path = f"tasks[{index}]"
    if not isinstance(task, dict):
        violations.append(f"{path}: 不是对象")
        return
    if not re.match(r"^task_[0-9]+$", str(task.get("task_id", ""))):
        violations.append(f"{path}.task_id: 必须匹配 task_<数字>，实际 {task.get('task_id')!r}")
    if not (isinstance(task.get("title"), str) and task.get("title").strip()):
        violations.append(f"{path}.title: 必填")
    due_at = task.get("due_at")
    if due_at is not None and not DATETIME.match(str(due_at)):
        violations.append(f"{path}.due_at: 非 RFC3339 {due_at!r}")
    if task.get("priority") is not None and task.get("priority") not in {"high", "medium", "low"}:
        violations.append(f"{path}.priority: 不在枚举内 {task.get('priority')!r}")
    check_confidence(task.get("confidence"), path, violations)
    check_source_id(task.get("source_id"), path, violations, expected_source_id)
    check_evidence(task.get("evidence"), path, violations)


def validate(raw, expected_source_id):
    violations = []
    raw_keys = set(raw.keys()) if isinstance(raw, dict) else set()
    data = normalize(raw)
    if not isinstance(data, dict):
        return ["顶层必须是 JSON 对象"], []

    for field in TOP_LEVEL_REQUIRED:
        if field not in data:
            violations.append(f"顶层缺少必填字段: {field}")

    if data.get("schema_version") != "1.0":
        violations.append(f"schema_version 必须为 \"1.0\"，实际 {data.get('schema_version')!r}")
    extraction_id = str(data.get("extraction_id", ""))
    if not re.match(r"^[0-9a-fA-F-]{36}$", extraction_id):
        violations.append(f"extraction_id 必须是 UUID，实际 {data.get('extraction_id')!r}")
    if not re.match(r"^[a-z]{2}(-[A-Z]{2})?$", str(data.get("language", ""))):
        violations.append(f"language 必须为 BCP47，实际 {data.get('language')!r}")
    if data.get("document_type") not in DOCUMENT_TYPES:
        violations.append(f"document_type 不在枚举内 {data.get('document_type')!r}")
    extracted_at = str(data.get("extracted_at", ""))
    if not DATETIME.match(extracted_at):
        violations.append(f"extracted_at 必须为 RFC3339，实际 {data.get('extracted_at')!r}")

    source = data.get("source")
    if not isinstance(source, dict):
        violations.append("source 必须为对象")
    else:
        for field in ("source_id", "file_name", "file_type", "mime_type", "obs_key", "sha256", "uploaded_at"):
            if field not in source:
                violations.append(f"source 缺少必填字段: {field}")
        check_source_id(source.get("source_id"), "source", violations, expected_source_id)
        if source.get("file_type") not in {"pdf", "image", "txt"}:
            violations.append(f"source.file_type 不在枚举内 {source.get('file_type')!r}")
        if not re.match(r"^[a-f0-9]{64}$", str(source.get("sha256", ""))):
            violations.append("source.sha256 必须为 64 位小写十六进制")

    for field in ("entities", "facts", "events", "tasks", "warnings"):
        if not isinstance(data.get(field), list):
            violations.append(f"{field} 必须为数组（可为空，不得为 null）")

    for index, entity in enumerate(data.get("entities") or []):
        check_entity(entity, index, violations, expected_source_id)
    for index, fact in enumerate(data.get("facts") or []):
        check_fact(fact, index, violations, expected_source_id)
    for index, event in enumerate(data.get("events") or []):
        check_event(event, index, violations, expected_source_id)
    for index, task in enumerate(data.get("tasks") or []):
        check_task(task, index, violations, expected_source_id)

    camel_keys = [key for key in raw_keys if key != to_snake(key)]
    naming = [
        "字段命名风格为 camelCase：" + ", ".join(sorted(camel_keys)),
        "docs/extraction-schema.md 第 9 节 JSON Schema 定义的是 snake_case，"
        "两种线格式需在契约中明确其一，否则按第 9 节校验必然判 E-SCHEMA。",
    ] if camel_keys else []
    return violations, naming


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__)
        return 2
    path = argv[1]
    expected_source_id = None
    if "--source-id" in argv:
        index = argv.index("--source-id")
        if index + 1 < len(argv):
            expected_source_id = argv[index + 1]
    try:
        with open(path, "r", encoding="utf-8") as handle:
            raw = json.load(handle)
    except Exception as exc:  # noqa: BLE001
        print(f"[错误] 读取/解析 {path} 失败: {exc}")
        return 2

    violations, naming = validate(raw, expected_source_id)
    print(f"[校验文件] {path}")
    if naming:
        print("[命名观察]")
        for note in naming:
            print("  - " + note)
    if violations:
        print(f"[结果] 不符合 docs 第 9 节 JSON Schema，共 {len(violations)} 项：")
        for item in violations:
            print("  - " + item)
        return 1
    print("[结果] 通过：结构与字段均符合 docs 第 9 节 JSON Schema")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))