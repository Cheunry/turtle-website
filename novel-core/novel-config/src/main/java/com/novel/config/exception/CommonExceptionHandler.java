package com.novel.config.exception;

import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * 通用的异常处理器
 */
@RestControllerAdvice
public class CommonExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CommonExceptionHandler.class);

    /**
     * 处理数据校验异常
     */
    @ExceptionHandler(BindException.class)
    public RestResp<Void> handlerBindException(BindException e) {
        log.error(e.getMessage(), e);
        return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
    }

    /**
     * 处理参数类型不匹配异常（如字符串 "null" 无法转换为 Long）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public RestResp<?> handlerMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String parameterName = e.getName();
        String requestPath = request.getRequestURI();
        
        log.warn("参数类型转换失败，参数名: {}, 请求路径: {}, 错误信息: {}", 
                parameterName, requestPath, e.getMessage());
        
        // 如果是 bookId 或 id 参数，且值为 "null"，返回空列表或空内容
        if (("bookId".equals(parameterName) || "id".equals(parameterName)) 
                && e.getValue() != null && "null".equals(e.getValue().toString())) {
            // 如果是章节列表接口，返回空列表
            if (requestPath != null && requestPath.contains("/chapter/list")) {
                return RestResp.ok(List.of());
            }
            // 如果是书籍内容接口，返回空内容
            if (requestPath != null && requestPath.contains("/content/")) {
                return RestResp.ok(null);
            }
            // 如果是书籍信息接口，返回错误
            if (requestPath != null && requestPath.matches(".*/book/\\d+$")) {
                return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "书籍ID无效");
            }
        }
        
        // 其他情况返回参数错误
        return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public RestResp<Void> handlerBusinessException(BusinessException e) {
        log.error(e.getMessage(), e);
        return RestResp.fail(e.getErrorCodeEnum());
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(Exception.class)
    public RestResp<Void> handlerException(Exception e) {
        log.error(e.getMessage(), e);
        return RestResp.error();
    }

}
