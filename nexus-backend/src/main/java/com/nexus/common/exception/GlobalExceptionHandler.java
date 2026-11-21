package com.nexus.common.exception;

import com.nexus.common.result.Result;
import com.nexus.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 * 为什么集中处理：保证所有接口的失败响应都是统一的 Result 结构，错误码与契约文档一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getResultCode().getCode(), e.getMessage());
        return Result.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ResultCode.PARAM_INVALID, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        // 请求体缺失/JSON 语法错误/编码错误都归为参数不合法，避免占用 90000 掩盖真实问题
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_INVALID, "请求体格式错误，请使用 UTF-8 编码的合法 JSON");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception e) {
        // 为什么只打日志不外泄堆栈：避免把内部实现细节暴露给前端
        log.error("未预期异常", e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }
}