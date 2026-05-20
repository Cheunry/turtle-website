package com.novel.ai.image.exception;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.util.Locale;

/**
 * 将 Spring AI / DashScope 生图异常归类，决定是否重试以及给用户展示的失败原因。
 */
public final class ImageGenerationExceptionClassifier {

    private static final String MSG_TRANSIENT = "AI生图服务繁忙，请稍后重试";
    private static final String MSG_BAD_REQUEST = "封面提示词参数异常，请重新生成提示词后再试";
    private static final String MSG_AUTH_OR_QUOTA = "AI生图服务暂不可用，请稍后再试";
    private static final String MSG_CONTENT_SAFETY = "封面提示词触发内容安全，请调整作品简介或提示词后重试";
    private static final String MSG_UNKNOWN = "封面生成失败，网站日志已经记录，后续会排查";

    private ImageGenerationExceptionClassifier() {
    }

    public static ImageGenerationException classify(Throwable e) {
        if (e instanceof ImageGenerationException imageException) {
            return imageException;
        }
        if (e instanceof TransientAiException) {
            return transientError(e);
        }

        String message = collectMessages(e);
        String lower = message.toLowerCase(Locale.ROOT);

        if (isContentSafety(lower)) {
            return nonRetryable(ImageGenerationException.Category.CONTENT_SAFETY, MSG_CONTENT_SAFETY, e);
        }
        if (isAuthOrQuota(lower)) {
            return nonRetryable(ImageGenerationException.Category.AUTH_OR_QUOTA, MSG_AUTH_OR_QUOTA, e);
        }
        if (isBadRequest(lower)) {
            return nonRetryable(ImageGenerationException.Category.BAD_REQUEST, MSG_BAD_REQUEST, e);
        }
        if (e instanceof NonTransientAiException) {
            return nonRetryable(ImageGenerationException.Category.UNKNOWN, MSG_UNKNOWN, e);
        }
        if (isTransient(lower)) {
            return transientError(e);
        }
        return nonRetryable(ImageGenerationException.Category.UNKNOWN, MSG_UNKNOWN, e);
    }

    public static ImageGenerationTransientException transientError(Throwable cause) {
        return new ImageGenerationTransientException(MSG_TRANSIENT, cause);
    }

    private static ImageGenerationException nonRetryable(
            ImageGenerationException.Category category,
            String userMessage,
            Throwable cause) {
        return new ImageGenerationException(category, userMessage, cause);
    }

    private static boolean isContentSafety(String lower) {
        return lower.contains("datainspectionfailed")
                || lower.contains("inappropriate content")
                || lower.contains("content inspection")
                || lower.contains("safety")
                || lower.contains("unsafe")
                || lower.contains("不当内容")
                || lower.contains("内容安全")
                || lower.contains("安全检查");
    }

    private static boolean isAuthOrQuota(String lower) {
        return containsHttpStatus(lower, 401)
                || containsHttpStatus(lower, 403)
                || lower.contains("unauthorized")
                || lower.contains("forbidden")
                || lower.contains("accessdenied")
                || lower.contains("access denied")
                || lower.contains("invalidapikey")
                || lower.contains("invalid api key")
                || lower.contains("api-key")
                || lower.contains("apikey")
                || lower.contains("authentication")
                || lower.contains("quota")
                || lower.contains("insufficient balance")
                || lower.contains("no enough balance")
                || lower.contains("out of credit")
                || lower.contains("billing")
                || lower.contains("余额不足")
                || lower.contains("欠费")
                || lower.contains("额度不足")
                || lower.contains("配额");
    }

    private static boolean isBadRequest(String lower) {
        return containsHttpStatus(lower, 400)
                || lower.contains("bad request")
                || lower.contains("invalid parameter")
                || lower.contains("invalidparam")
                || lower.contains("invalid input")
                || lower.contains("illegal parameter")
                || lower.contains("unsupported")
                || lower.contains("resolution")
                || lower.contains("参数")
                || lower.contains("不支持");
    }

    private static boolean isTransient(String lower) {
        return containsHttpStatus(lower, 429)
                || containsHttpStatus(lower, 500)
                || containsHttpStatus(lower, 502)
                || containsHttpStatus(lower, 503)
                || containsHttpStatus(lower, 504)
                || lower.contains("timeout")
                || lower.contains("timed out")
                || lower.contains("connection reset")
                || lower.contains("connection refused")
                || lower.contains("service unavailable")
                || lower.contains("bad gateway")
                || lower.contains("gateway timeout")
                || lower.contains("too many requests")
                || lower.contains("throttl")
                || lower.contains("rate limit")
                || lower.contains("qps")
                || lower.contains("pending")
                || lower.contains("暂不可用")
                || lower.contains("超时")
                || lower.contains("限流");
    }

    private static boolean containsHttpStatus(String lower, int status) {
        String s = String.valueOf(status);
        return lower.contains(" " + s + " ")
                || lower.contains("status " + s)
                || lower.contains("status=" + s)
                || lower.contains("status:" + s)
                || lower.contains("http " + s)
                || lower.contains("http=" + s)
                || lower.contains("code " + s)
                || lower.contains("code=" + s)
                || lower.contains("\"code\":\"" + s)
                || lower.contains("[" + s + "]");
    }

    private static String collectMessages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current.getMessage() != null) {
                sb.append(' ').append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }
}
