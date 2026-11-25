package com.nexus.common.exception;

import com.nexus.common.result.Result;
import com.nexus.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/** 全局异常处理器：验证无匹配路由返回 404 + RESOURCE_NOT_FOUND，不再误报 500。 */
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
}
