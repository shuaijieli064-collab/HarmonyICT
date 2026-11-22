package com.nexus.common.result;

/**
 * 错误码集中定义，按模块分段（规范 2.3：认证 1xxxx、世界模型 2xxxx、文件 3xxxx、风险 4xxxx、任务 5xxxx、Agent 6xxxx）。
 * 为什么集中：契约文档 api-specification.md 与前后端共用同一份错误码定义，禁止散落魔法值。
 */
public enum ResultCode {

    SUCCESS(0, "成功"),

    // 认证模块 1xxxx
    AUTH_BAD_CREDENTIALS(10101, "用户名或密码错误"),
    AUTH_USER_EXISTS(10102, "用户名已存在"),
    AUTH_TOKEN_INVALID(10103, "令牌无效或已过期"),
    AUTH_TOKEN_EXPIRED(10104, "令牌已过期"),
    AUTH_UNAUTHORIZED(10105, "未登录或令牌缺失"),

    // 世界模型模块 2xxxx
    WORLD_ENTITY_NOT_FOUND(20101, "实体不存在"),
    WORLD_FACT_NOT_FOUND(20102, "事实不存在"),
    WORLD_FACT_CONFLICT(20103, "事实状态冲突"),
    WORLD_ENTITY_DUPLICATE(20104, "同类型实体名称已存在"),

    // 文件与接入模块 3xxxx
    FILE_EMPTY(30101, "上传文件为空"),
    FILE_TYPE_UNSUPPORTED(30102, "不支持的文件类型"),
    FILE_TOO_LARGE(30103, "文件超过大小限制"),
    FILE_STORE_FAILED(30104, "文件存储失败"),

    // 风险模块 4xxxx
    RISK_NOT_FOUND(40101, "风险记录不存在"),

    // 任务模块 5xxxx
    TASK_NOT_FOUND(50101, "任务不存在"),
    TASK_STATE_INVALID(50102, "任务状态流转不合法"),

    // Agent 模块 6xxxx
    AGENT_RUN_NOT_FOUND(60101, "Agent 执行记录不存在"),
    AGENT_PERMISSION_DENIED(60102, "Agent 操作未被授权"),

    // 信息抽取校验 9xxxx（对应 B 侧 extraction-schema.md 的 E-NO-SOURCE / E-SCHEMA / E-PREDICATE）
    EXTRACTION_NO_SOURCE(90011, "抽取请求缺少有效的来源 sourceId"),
    EXTRACTION_SCHEMA(90012, "抽取结果不符合约定 Schema"),
    EXTRACTION_PREDICATE(90013, "抽取谓词不在登记清单内"),

    // 通用 9xxxx
    PARAM_INVALID(90001, "请求参数不合法"),
    RESOURCE_NOT_FOUND(90002, "资源不存在"),
    INTERNAL_ERROR(90000, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}