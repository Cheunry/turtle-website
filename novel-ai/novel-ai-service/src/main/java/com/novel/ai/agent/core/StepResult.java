package com.novel.ai.agent.core;

/**
 * Step 执行结果。用于决定 Pipeline 是否继续执行后续步骤。
 *
 * <ul>
 *     <li>{@link #CONTINUE} —— 正常推进到下一步。</li>
 *     <li>{@link #SHORT_CIRCUIT} —— 已在当前 Step 产出最终结果（例如参数校验失败、
 *         命中内容安全拦截），Pipeline 立刻结束，不再走后续 Step。</li>
 * </ul>
 */
public enum StepResult {
    CONTINUE,
    SHORT_CIRCUIT
}
