package com.novel.ai.mq;

import com.novel.book.dto.mq.ChapterAuditRequestMqDto;
import com.novel.book.dto.mq.ChapterAuditResultMqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.ai.service.TextService;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 章节审核请求MQ消费者（AI服务）
 * 接收业务服务发送的审核请求，处理后发送审核结果MQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookAuditRequestMq.TOPIC,
    selectorExpression = AmqpConsts.BookAuditRequestMq.TAG_AUDIT_CHAPTER_REQUEST,
    consumerGroup = AmqpConsts.BookAuditRequestMq.CONSUMER_GROUP_AUDIT_CHAPTER_REQUEST
)
public class ChapterAuditRequestListener implements RocketMQListener<ChapterAuditRequestMqDto> {

    private final TextService textService;
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(ChapterAuditRequestMqDto requestDto) {
        log.info("收到章节审核请求，taskId: {}, chapterId: {}, bookId: {}", 
                requestDto.getTaskId(), requestDto.getChapterId(), requestDto.getBookId());
        
        try {
            // 1. 构建审核请求DTO
            ChapterAuditReqDto auditReq = ChapterAuditReqDto.builder()
                    .bookId(requestDto.getBookId())
                    .chapterNum(requestDto.getChapterNum())
                    .chapterName(requestDto.getChapterName())
                    .content(requestDto.getContent())
                    .build();

            // 2. 调用AI审核服务
            RestResp<ChapterAuditRespDto> auditResp = textService.auditChapter(auditReq);

            // 3. 构建审核结果MQ消息
            ChapterAuditResultMqDto resultDto;
            if (auditResp.isOk() && auditResp.getData() != null) {
                ChapterAuditRespDto auditResult = auditResp.getData();
                resultDto = ChapterAuditResultMqDto.builder()
                        .taskId(requestDto.getTaskId())
                        .chapterId(requestDto.getChapterId())
                        .bookId(requestDto.getBookId())
                        .auditStatus(auditResult.getAuditStatus())
                        .aiConfidence(auditResult.getAiConfidence())
                        .auditReason(auditResult.getAuditReason())
                        .success(true)
                        .build();
                log.info("章节审核完成，taskId: {}, chapterId: {}, auditStatus: {}, confidence: {}", 
                        requestDto.getTaskId(), requestDto.getChapterId(), 
                        auditResult.getAuditStatus(), auditResult.getAiConfidence());
            } else {
                // AI审核失败，构建失败结果
                resultDto = ChapterAuditResultMqDto.builder()
                        .taskId(requestDto.getTaskId())
                        .chapterId(requestDto.getChapterId())
                        .bookId(requestDto.getBookId())
                        .auditStatus(0) // 待审核
                        .aiConfidence(new BigDecimal("0.0"))
                        .auditReason(auditResp.getMessage() != null ? 
                                "AI审核失败: " + auditResp.getMessage() : "AI审核失败: 未知错误")
                        .success(false)
                        .errorMessage(auditResp.getMessage())
                        .build();
                log.error("章节审核失败，taskId: {}, chapterId: {}, error: {}", 
                        requestDto.getTaskId(), requestDto.getChapterId(), auditResp.getMessage());
            }

            // 4. 发送审核结果MQ
            String destination = AmqpConsts.BookAuditResultMq.TOPIC + ":" 
                    + AmqpConsts.BookAuditResultMq.TAG_AUDIT_CHAPTER_RESULT;
            rocketMQTemplate.convertAndSend(destination, resultDto);
            log.debug("章节审核结果已发送到MQ，taskId: {}, chapterId: {}", 
                    requestDto.getTaskId(), requestDto.getChapterId());

        } catch (Exception e) {
            log.error("处理章节审核请求异常，taskId: {}, chapterId: {}", 
                    requestDto.getTaskId(), requestDto.getChapterId(), e);
            
            // 发送失败结果MQ，确保业务服务能感知到失败
            try {
                ChapterAuditResultMqDto errorResult = ChapterAuditResultMqDto.builder()
                        .taskId(requestDto.getTaskId())
                        .chapterId(requestDto.getChapterId())
                        .bookId(requestDto.getBookId())
                        .auditStatus(0) // 待审核
                        .aiConfidence(new BigDecimal("0.0"))
                        .auditReason("AI审核服务异常: " + e.getMessage())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
                String destination = AmqpConsts.BookAuditResultMq.TOPIC + ":" 
                        + AmqpConsts.BookAuditResultMq.TAG_AUDIT_CHAPTER_RESULT;
                rocketMQTemplate.convertAndSend(destination, errorResult);
                log.warn("已发送审核失败结果到MQ，taskId: {}", requestDto.getTaskId());
            } catch (Exception sendEx) {
                log.error("发送审核失败结果MQ异常，taskId: {}", requestDto.getTaskId(), sendEx);
            }
        }
    }
}

