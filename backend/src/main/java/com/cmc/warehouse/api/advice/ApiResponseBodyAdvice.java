package com.cmc.warehouse.api.advice;

import com.cmc.warehouse.api.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps successful controller payloads in {@link ApiResponse} while leaving errors
 * (already wrapped in {@code GlobalExceptionHandler}) unchanged.
 */
@RestControllerAdvice(basePackages = "com.cmc.warehouse.api.controller")
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        if (body instanceof ApiResponse<?>) {
            return body;
        }
        if (body == null) {
            int status = resolveStatus(response);
            String phrase = reasonPhrase(status);
            return ApiResponse.success(status, phrase, null);
        }
        if (body instanceof org.springframework.core.io.Resource) {
            return body;
        }
        if (body instanceof byte[]) {
            return body;
        }

        int status = resolveStatus(response);
        String phrase = reasonPhrase(status);
        return ApiResponse.success(status, phrase, body);
    }

    private static int resolveStatus(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            int status = servletResponse.getServletResponse().getStatus();
            if (status > 0) {
                return status;
            }
        }
        return HttpStatus.OK.value();
    }

    private static String reasonPhrase(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.getReasonPhrase() : "OK";
    }
}
