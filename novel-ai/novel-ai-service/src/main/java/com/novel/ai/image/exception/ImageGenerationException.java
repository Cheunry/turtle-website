package com.novel.ai.image.exception;

/**
 * 生图异常基类：message 面向用户展示，cause 保留供应商/底层原始异常用于日志排查。
 */
public class ImageGenerationException extends RuntimeException {

    private final Category category;
    private final String userMessage;

    public ImageGenerationException(Category category, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.category = category;
        this.userMessage = userMessage;
    }

    public Category getCategory() {
        return category;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public enum Category {
        TRANSIENT,
        BAD_REQUEST,
        AUTH_OR_QUOTA,
        CONTENT_SAFETY,
        UNKNOWN
    }
}
