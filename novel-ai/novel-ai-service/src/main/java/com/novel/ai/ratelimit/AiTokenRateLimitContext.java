package com.novel.ai.ratelimit;

/**
 * 当前线程内的 AI token 预算场景上下文。
 * <p>
 * ChatClient Advisor 只能看到最终 Prompt，不知道业务入口；调用侧进入模型前设置场景，
 * Advisor 就能按审核/润色/封面提示词等业务特征选择不同的输出预估策略。
 */
public final class AiTokenRateLimitContext {

    public static final String AUDIT_BOOK = "audit-book";
    public static final String AUDIT_CHAPTER = "audit-chapter";
    public static final String COVER_PROMPT = "cover-prompt";
    public static final String POLISH = "polish";
    public static final String POLISH_STREAM = "polish-stream";
    public static final String AUDIT_RULE_EXTRACT = "audit-rule-extract";
    public static final String DEFAULT = "default";

    private static final ThreadLocal<String> SCENE = new ThreadLocal<>();

    private AiTokenRateLimitContext() {
    }

    public static Scope use(String scene) {
        String previous = SCENE.get();
        SCENE.set(scene == null || scene.isBlank() ? DEFAULT : scene);
        return new Scope(previous);
    }

    public static String currentScene() {
        String scene = SCENE.get();
        return scene == null || scene.isBlank() ? DEFAULT : scene;
    }

    public static String sceneFromLogContext(String logContext) {
        if (logContext == null || logContext.isBlank()) {
            return DEFAULT;
        }
        if (logContext.startsWith("book-audit")) {
            return AUDIT_BOOK;
        }
        if (logContext.startsWith("chapter-audit")) {
            return AUDIT_CHAPTER;
        }
        if (logContext.startsWith("text-polish")) {
            return POLISH;
        }
        if (logContext.startsWith("audit-rule-extract")) {
            return AUDIT_RULE_EXTRACT;
        }
        return DEFAULT;
    }

    public static final class Scope implements AutoCloseable {

        private final String previous;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                SCENE.remove();
            } else {
                SCENE.set(previous);
            }
        }
    }
}
