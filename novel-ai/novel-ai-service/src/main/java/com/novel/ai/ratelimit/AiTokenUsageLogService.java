package com.novel.ai.ratelimit;

import com.novel.ai.dao.entity.AiTokenUsageLog;
import com.novel.ai.dao.mapper.AiTokenUsageLogMapper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI token 使用流水异步落库。
 */
@Slf4j
@Service
public class AiTokenUsageLogService {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RATE_LIMITED = "RATE_LIMITED";

    private final AiTokenUsageLogMapper mapper;

    @Qualifier("aiTokenUsageLogExecutor")
    private final ThreadPoolTaskExecutor executor;

    public AiTokenUsageLogService(
            AiTokenUsageLogMapper mapper,
            @Qualifier("aiTokenUsageLogExecutor") ThreadPoolTaskExecutor executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    public void recordAsync(RecordCommand command) {
        if (command == null) {
            return;
        }
        executor.execute(() -> {
            try {
                mapper.insert(toEntity(command));
            } catch (Exception e) {
                log.warn("AI token 使用流水写入失败: scene={}, model={}, status={}",
                        command.scene(), command.model(), command.status(), e);
            }
        });
    }

    private AiTokenUsageLog toEntity(RecordCommand command) {
        Integer estimatedTotal = safeInt(command.estimatedTotalTokens());
        Integer actualTotal = safeInt(command.actualTotalTokens());
        Integer delta = actualTotal != null && estimatedTotal != null ? actualTotal - estimatedTotal : null;
        return AiTokenUsageLog.builder()
                .traceId(blankToNull(command.traceId()))
                .scene(command.scene())
                .model(command.model())
                .estimatedPromptTokens(safeInt(command.estimatedPromptTokens()))
                .reservedCompletionTokens(safeInt(command.reservedCompletionTokens()))
                .estimatedTotalTokens(estimatedTotal)
                .actualPromptTokens(safeInt(command.actualPromptTokens()))
                .actualCompletionTokens(safeInt(command.actualCompletionTokens()))
                .actualTotalTokens(actualTotal)
                .estimateDeltaTokens(delta)
                .status(command.status())
                .errorType(truncate(command.errorType(), 128))
                .errorMessage(truncate(command.errorMessage(), 512))
                .durationMs(command.durationMs())
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static String currentTraceId() {
        try {
            String traceId = TraceContext.traceId();
            return traceId == null || traceId.isBlank() || "N/A".equals(traceId) ? null : traceId;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer safeInt(Long value) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < 0) {
            return 0;
        }
        return value.intValue();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength);
    }

    @Builder
    public record RecordCommand(
            String traceId,
            String scene,
            String model,
            Long estimatedPromptTokens,
            Long reservedCompletionTokens,
            Long estimatedTotalTokens,
            Long actualPromptTokens,
            Long actualCompletionTokens,
            Long actualTotalTokens,
            String status,
            String errorType,
            String errorMessage,
            Long durationMs
    ) {
    }
}
