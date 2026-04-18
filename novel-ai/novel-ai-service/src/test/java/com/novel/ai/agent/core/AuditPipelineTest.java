package com.novel.ai.agent.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pipeline 编排语义核心测试：CONTINUE 链式推进、SHORT_CIRCUIT 即刻中止、
 * Step 抛异常时委派给 {@link AuditExceptionMapper}，并驱动监听器回调。
 */
class AuditPipelineTest {

    /** 用于测试的极简上下文，REQ/RESP 取 String 方便断言。 */
    static class TestCtx extends AuditContext<String, String> {
        TestCtx(String request) {
            super(request, "unit-test");
        }
    }

    /** 记录调用序列，便于断言。 */
    static class RecordingStep implements AuditStep<TestCtx> {
        private final String id;
        private final StepResult result;
        private final RuntimeException toThrow;
        private final List<String> log;

        RecordingStep(String id, StepResult result, List<String> log) {
            this(id, result, null, log);
        }

        RecordingStep(String id, RuntimeException toThrow, List<String> log) {
            this(id, StepResult.CONTINUE, toThrow, log);
        }

        private RecordingStep(String id, StepResult result, RuntimeException toThrow, List<String> log) {
            this.id = id;
            this.result = result;
            this.toThrow = toThrow;
            this.log = log;
        }

        @Override
        public StepResult execute(TestCtx context) {
            log.add("exec:" + id);
            if (toThrow != null) {
                throw toThrow;
            }
            if (result == StepResult.SHORT_CIRCUIT) {
                context.setResult("short-circuit-by-" + id);
            }
            return result;
        }

        @Override
        public String name() {
            return id;
        }
    }

    @Test
    void runs_all_steps_when_all_continue() {
        List<String> log = new ArrayList<>();
        AuditPipeline<TestCtx> pipeline = new AuditPipeline<>(
                List.of(new RecordingStep("s1", StepResult.CONTINUE, log),
                        new RecordingStep("s2", StepResult.CONTINUE, log),
                        new RecordingStep("s3", StepResult.CONTINUE, log)),
                (ctx, e) -> ctx.setResult("mapper-should-not-be-called"),
                null);

        TestCtx ctx = new TestCtx("req");
        pipeline.execute(ctx);

        assertThat(log).containsExactly("exec:s1", "exec:s2", "exec:s3");
        assertThat(ctx.getResult()).isNull();
        assertThat(ctx.getError()).isNull();
    }

    @Test
    void stops_immediately_on_short_circuit() {
        List<String> log = new ArrayList<>();
        AuditPipeline<TestCtx> pipeline = new AuditPipeline<>(
                List.of(new RecordingStep("s1", StepResult.CONTINUE, log),
                        new RecordingStep("s2", StepResult.SHORT_CIRCUIT, log),
                        new RecordingStep("s3", StepResult.CONTINUE, log)),
                (ctx, e) -> ctx.setResult("mapper-should-not-be-called"),
                null);

        TestCtx ctx = new TestCtx("req");
        pipeline.execute(ctx);

        assertThat(log).containsExactly("exec:s1", "exec:s2");
        assertThat(ctx.getResult()).isEqualTo("short-circuit-by-s2");
    }

    @Test
    void delegates_to_mapper_when_step_throws() {
        List<String> log = new ArrayList<>();
        IllegalStateException boom = new IllegalStateException("boom");

        AuditPipeline<TestCtx> pipeline = new AuditPipeline<>(
                List.of(new RecordingStep("s1", StepResult.CONTINUE, log),
                        new RecordingStep("s2", boom, log),
                        new RecordingStep("s3", StepResult.CONTINUE, log)),
                (ctx, e) -> ctx.setResult("mapped:" + e.getMessage()),
                null);

        TestCtx ctx = new TestCtx("req");
        pipeline.execute(ctx);

        assertThat(log).containsExactly("exec:s1", "exec:s2");
        assertThat(ctx.getResult()).isEqualTo("mapped:boom");
        assertThat(ctx.getError()).isSameAs(boom);
    }

    @Test
    void invokes_listener_hooks_in_expected_order() {
        List<String> log = new ArrayList<>();
        AuditPipelineListener<TestCtx> listener = new AuditPipelineListener<>() {
            @Override public void onStart(TestCtx c) { log.add("listener:start"); }
            @Override public void onStepStart(TestCtx c, AuditStep<TestCtx> s) { log.add("listener:stepStart:" + s.name()); }
            @Override public void onStepEnd(TestCtx c, AuditStep<TestCtx> s, StepResult r) { log.add("listener:stepEnd:" + s.name() + ":" + r); }
            @Override public void onStepError(TestCtx c, AuditStep<TestCtx> s, Exception e) { log.add("listener:stepError:" + s.name()); }
            @Override public void onEnd(TestCtx c) { log.add("listener:end"); }
        };

        AuditPipeline<TestCtx> pipeline = new AuditPipeline<>(
                List.of(new RecordingStep("s1", StepResult.CONTINUE, log),
                        new RecordingStep("s2", StepResult.SHORT_CIRCUIT, log)),
                (ctx, e) -> ctx.setResult("mapper"),
                listener);

        pipeline.execute(new TestCtx("req"));

        assertThat(log).containsExactly(
                "listener:start",
                "listener:stepStart:s1",
                "exec:s1",
                "listener:stepEnd:s1:CONTINUE",
                "listener:stepStart:s2",
                "exec:s2",
                "listener:stepEnd:s2:SHORT_CIRCUIT",
                "listener:end");
    }

    @Test
    void listener_on_error_called_when_step_throws() {
        List<String> log = new ArrayList<>();
        AuditPipelineListener<TestCtx> listener = new AuditPipelineListener<>() {
            @Override public void onStepError(TestCtx c, AuditStep<TestCtx> s, Exception e) {
                log.add("listener:stepError:" + s.name());
            }
            @Override public void onEnd(TestCtx c) { log.add("listener:end"); }
        };

        AuditPipeline<TestCtx> pipeline = new AuditPipeline<>(
                List.of(new RecordingStep("s1", new RuntimeException("x"), log)),
                (ctx, e) -> ctx.setResult("mapped"),
                listener);

        pipeline.execute(new TestCtx("req"));

        assertThat(log).containsExactly("exec:s1", "listener:stepError:s1", "listener:end");
    }
}
