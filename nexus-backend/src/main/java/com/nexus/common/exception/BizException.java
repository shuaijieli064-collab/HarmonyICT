package com.nexus.common.exception;

import com.nexus.common.result.ResultCode;

/**
 * 业务异常。规范 2.3：所有业务错误统一抛此异常，由全局异常处理器转 Result，Controller 不自行 try-catch 拼响应。
 */
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}