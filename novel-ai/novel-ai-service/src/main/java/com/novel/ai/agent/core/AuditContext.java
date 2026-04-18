package com.novel.ai.agent.core;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 审核流水线的上下文基类。Step 之间通过读写本对象的字段协作，避免方法间
 * 传递大量参数。具体业务（书籍审核 / 章节审核）继承此类并补充自己的字段。
 *
 * @param <REQ>  原始请求 DTO 类型
 * @param <RESP> 最终响应 DTO 类型
 */
@Getter
@Setter
public abstract class AuditContext<REQ, RESP> {

    /** 原始请求体，整个流水线只读。 */
    private final REQ request;

    /** 流水线开始时间戳（毫秒）。用于统一记录总耗时。 */
    private final long startTimeMs = System.currentTimeMillis();

    /** 本次调用在链路追踪 / 日志中的业务名，便于观测。 */
    private final String operationName;

    /** 渲染完成的 system prompt（已附 JSON 格式说明）。 */
    private String systemPrompt;

    /** 渲染完成的 user prompt。 */
    private String userPrompt;

    /** 若某 Step 决定短路，则在这里放最终响应。 */
    private RESP result;

    /** 流水线中发生的异常，由 AuditExceptionMapper 统一翻译为业务响应。 */
    private Exception error;

    /**
     * Step 起始纳秒时戳缓存，由 {@link AuditPipelineListener} 写入，用于计算 step 级耗时。
     * 放在上下文中可保证 listener 无状态，避免 ThreadLocal 泄漏风险。
     */
    private final Map<String, Long> stepStartNanos = new HashMap<>();

    protected AuditContext(REQ request, String operationName) {
        this.request = request;
        this.operationName = operationName;
    }

    /** 是否已经由上游 Step 产出最终结果。 */
    public boolean hasResult() {
        return result != null;
    }
}
