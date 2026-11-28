package com.nexus.common.exception;

import com.nexus.common.result.Result;
import com.nexus.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 全局异常处理器回归：404 误报修正 + 405 方法不支持映射。 */
class GlobalExceptionHandlerTest {

    @Test
    void noResourceFoundReturns404WithResourceNotFoundCode() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        NoResourceFoundException ex = new NoResourceFoundException(
                HttpMethod.GET, "api/v1/nonexistent");

        Result<Void> result = handler.handleNotFound(ex);

        assertThat(result.code()).isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
        ResponseStatus status = GlobalExceptionHandler.class
                .getMethod("handleNotFound", NoResourceFoundException.class)
                .getAnnotation(ResponseStatus.class);
        assertThat(status).isNotNull();
        assertThat(status.value()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedInsteadOfInternalError() throws NoSuchMethodException {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("PATCH");

        Result<Void> result = handler.handleMethodNotSupported(exception);

        assertEquals(ResultCode.PARAM_INVALID.getCode(), result.code());
        assertNotNull(result.message());

        Method method = GlobalExceptionHandler.class.getMethod(
                "handleMethodNotSupported", HttpRequestMethodNotSupportedException.class);
        ResponseStatus status = method.getAnnotation(ResponseStatus.class);
        assertNotNull(status, "必须显式声明 HTTP 状态码");
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, status.value());
    }
}
