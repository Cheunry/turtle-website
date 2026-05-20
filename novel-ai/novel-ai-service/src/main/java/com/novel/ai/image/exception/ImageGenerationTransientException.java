package com.novel.ai.image.exception;

/**
 * 仅此类会触发生图重试：网络抖动、超时、限流、网关 5xx、结果暂不可用等。
 */
public class ImageGenerationTransientException extends ImageGenerationException {

    public ImageGenerationTransientException(String userMessage, Throwable cause) {
        super(Category.TRANSIENT, userMessage, cause);
    }
}
