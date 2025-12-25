package com.novel.ai.mq;

import com.novel.book.dto.mq.BookAuditRequestMqDto;
import com.novel.book.dto.mq.BookAuditResultMqDto;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
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
 * 书籍审核请求MQ消费者（AI服务）
 * 接收业务服务发送的审核请求，处理后发送审核结果MQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookAuditRequestMq.TOPIC,
    selectorExpression = AmqpConsts.BookAuditRequestMq.TAG_AUDIT_BOOK_REQUEST,
    consumerGroup = AmqpConsts.BookAuditRequestMq.CONSUMER_GROUP_AUDIT_BOOK_REQUEST
)
public class BookAuditRequestListener implements RocketMQListener<BookAuditRequestMqDto> {

    private final TextService textService;
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(BookAuditRequestMqDto requestDto) {
        log.info("收到书籍审核请求，taskId: {}, bookId: {}", 
                requestDto.getTaskId(), requestDto.getBookId());
        
        try {
            // 1. 构建审核请求DTO
            BookAuditReqDto auditReq = BookAuditReqDto.builder()
                    .id(requestDto.getBookId())
                    .bookName(requestDto.getBookName())
                    .bookDesc(requestDto.getBookDesc())
                    .build();

            // 2. 调用AI审核服务
            RestResp<BookAuditRespDto> auditResp = textService.auditBook(auditReq);

            // 3. 构建审核结果MQ消息
            BookAuditResultMqDto resultDto;
            if (auditResp.isOk() && auditResp.getData() != null) {
                BookAuditRespDto auditResult = auditResp.getData();
                resultDto = BookAuditResultMqDto.builder()
                        .taskId(requestDto.getTaskId())
                        .bookId(requestDto.getBookId())
                        .auditStatus(auditResult.getAuditStatus())
                        .aiConfidence(auditResult.getAiConfidence())
                        .auditReason(auditResult.getAuditReason())
                        .success(true)
                        .build();
                log.info("书籍审核完成，taskId: {}, bookId: {}, auditStatus: {}, confidence: {}", 
                        requestDto.getTaskId(), requestDto.getBookId(), 
                        auditResult.getAuditStatus(), auditResult.getAiConfidence());
            } else {
                // AI审核失败，构建失败结果
                resultDto = BookAuditResultMqDto.builder()
                        .taskId(requestDto.getTaskId())
                        .bookId(requestDto.getBookId())
                        .auditStatus(0) // 待审核
                        .aiConfidence(new BigDecimal("0.0"))
                        .auditReason(auditResp.getMessage() != null ? 
                                "AI审核失败: " + auditResp.getMessage() : "AI审核失败: 未知错误")
                        .success(false)
                        .errorMessage(auditResp.getMessage())
                        .build();
                log.error("书籍审核失败，taskId: {}, bookId: {}, error: {}", 
                        requestDto.getTaskId(), requestDto.getBookId(), auditResp.getMessage());
            }

            // 4. 发送审核结果MQ
            String destination = AmqpConsts.BookAuditResultMq.TOPIC + ":" 
                    + AmqpConsts.BookAuditResultMq.TAG_AUDIT_BOOK_RESULT;
            rocketMQTemplate.convertAndSend(destination, resultDto);
            log.debug("书籍审核结果已发送到MQ，taskId: {}, bookId: {}", 
                    requestDto.getTaskId(), requestDto.getBookId());

        } catch (Exception e) {
            log.error("处理书籍审核请求异常，taskId: {}, bookId: {}", 
                    requestDto.getTaskId(), requestDto.getBookId(), e);
            
            // 发送失败结果MQ，确保业务服务能感知到失败
            try {
                BookAuditResultMqDto errorResult = BookAuditResultMqDto.builder()
                        .taskId(requestDto.getTaskId())
                        .bookId(requestDto.getBookId())
                        .auditStatus(0) // 待审核
                        .aiConfidence(new BigDecimal("0.0"))
                        .auditReason("AI审核服务异常: " + e.getMessage())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
                String destination = AmqpConsts.BookAuditResultMq.TOPIC + ":" 
                        + AmqpConsts.BookAuditResultMq.TAG_AUDIT_BOOK_RESULT;
                rocketMQTemplate.convertAndSend(destination, errorResult);
                log.warn("已发送审核失败结果到MQ，taskId: {}", requestDto.getTaskId());
            } catch (Exception sendEx) {
                log.error("发送审核失败结果MQ异常，taskId: {}", requestDto.getTaskId(), sendEx);
            }
        }
    }
}

