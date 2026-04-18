package com.novel.ai.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RetryTransientAiAdvisor} 单元测试：验证两级重试的外层语义。
 * <ul>
 *     <li>只对 {@link TransientAiException} 做退避重试；</li>
 *     <li>{@link NonTransientAiException} 直接透传，不浪费次数；</li>
 *     <li>达到上限后把最后一次 Transient 异常抛出；</li>
 *     <li>首次就成功时不触发退避。</li>
 * </ul>
 */
class RetryTransientAiAdvisorTest {

    private NovelAiAdvisorProperties defaultProps() {
        NovelAiAdvisorProperties p = new NovelAiAdvisorProperties();
        p.setRetryEnabled(true);
        p.setRetryMaxAttempts(3);
        p.setRetryInitialBackoffMs(1L);
        p.setRetryBackoffMultiplier(1.0);
        p.setRetryMaxBackoffMs(1L);
        return p;
    }

    private ChatClientRequest stubRequest() {
        return ChatClientRequest.builder().prompt(new Prompt("user msg")).build();
    }

    @Test
    void first_attempt_success_not_retried() {
        AtomicInteger calls = new AtomicInteger();
        ChatClientResponse expected = ChatClientResponse.builder().chatResponse(null).build();
        CallAdvisorChain chain = new FakeChain(req -> {
            calls.incrementAndGet();
            return expected;
        });

        ChatClientResponse resp = new RetryTransientAiAdvisor(defaultProps()).adviseCall(stubRequest(), chain);

        assertThat(resp).isSameAs(expected);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retry_transient_then_succeed() {
        AtomicInteger calls = new AtomicInteger();
        ChatClientResponse expected = ChatClientResponse.builder().chatResponse(null).build();
        CallAdvisorChain chain = new FakeChain(req -> {
            int n = calls.incrementAndGet();
            if (n < 3) {
                throw new TransientAiException("rate limit");
            }
            return expected;
        });

        ChatClientResponse resp = new RetryTransientAiAdvisor(defaultProps()).adviseCall(stubRequest(), chain);

        assertThat(resp).isSameAs(expected);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void retry_exhausted_throws_last_transient() {
        AtomicInteger calls = new AtomicInteger();
        CallAdvisorChain chain = new FakeChain(req -> {
            calls.incrementAndGet();
            throw new TransientAiException("still throttled");
        });

        assertThatThrownBy(() ->
                new RetryTransientAiAdvisor(defaultProps()).adviseCall(stubRequest(), chain))
                .isInstanceOf(TransientAiException.class)
                .hasMessageContaining("still throttled");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void non_transient_passthrough_no_retry() {
        AtomicInteger calls = new AtomicInteger();
        CallAdvisorChain chain = new FakeChain(req -> {
            calls.incrementAndGet();
            throw new NonTransientAiException("content safety violated");
        });

        assertThatThrownBy(() ->
                new RetryTransientAiAdvisor(defaultProps()).adviseCall(stubRequest(), chain))
                .isInstanceOf(NonTransientAiException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void disabled_advisor_just_forwards_once() {
        AtomicInteger calls = new AtomicInteger();
        ChatClientResponse expected = ChatClientResponse.builder().chatResponse(null).build();
        NovelAiAdvisorProperties props = defaultProps();
        props.setRetryEnabled(false);
        CallAdvisorChain chain = new FakeChain(req -> {
            calls.incrementAndGet();
            if (calls.get() == 1) {
                throw new TransientAiException("boom");
            }
            return expected;
        });

        assertThatThrownBy(() ->
                new RetryTransientAiAdvisor(props).adviseCall(stubRequest(), chain))
                .isInstanceOf(TransientAiException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    /**
     * 极简 {@link CallAdvisorChain}：不走真实链，直接把请求交给 lambda。
     * 这里用 mock 只是为了实现接口方法，实际路径完全由 {@link #invoker} 驱动。
     */
    private static final class FakeChain implements CallAdvisorChain {

        @FunctionalInterface
        interface Invoker {
            ChatClientResponse invoke(ChatClientRequest request);
        }

        private final Invoker invoker;

        FakeChain(Invoker invoker) {
            this.invoker = invoker;
        }

        @Override
        public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
            return invoker.invoke(chatClientRequest);
        }

        @Override
        public java.util.List<org.springframework.ai.chat.client.advisor.api.CallAdvisor> getCallAdvisors() {
            return java.util.List.of();
        }
    }
}
