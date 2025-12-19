package com.novel.ai.service.impl;

import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.ai.service.TextService;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.common.resp.RestResp;
import com.novel.common.constant.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextServiceImpl implements TextService {

    private final ChatClient chatClient;

    private static final int MAX_CONTENT_LENGTH = 5000;
    private static final int MAX_AUDIT_REASON_LENGTH = 500; // 数据库字段限制
    private static final int MIN_BOOK_DESC_LENGTH = 30; // 最小小说简介长度

    /**
     * AI审核书籍（小说名和简介）
     */
    @Override
    public RestResp<BookAuditRespDto> auditBook(BookAuditReqDto reqDto) {
        try {
            // 构建审核提示词
            String prompt = buildAuditPrompt(reqDto.getBookName(), reqDto.getBookDesc());

            // 调用AI模型进行审核
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("AI审核响应，书籍ID: {}, 响应: {}", reqDto.getId(), aiResponse);

            // 解析AI响应
            BookAuditRespDto result = parseAuditResponse(aiResponse, reqDto.getId());

            return RestResp.ok(result);

        } catch (Exception e) {
            log.error("AI审核异常，书籍ID: {}", reqDto.getId(), e);
            
            // 检查是否是内容安全检查失败（AI模型检测到不当内容）
            if (isContentInspectionFailed(e)) {
                // AI模型检测到不当内容，直接返回审核不通过
                log.warn("AI模型检测到不当内容，书籍ID: {}, 直接标记为审核不通过", reqDto.getId());
                BookAuditRespDto result = BookAuditRespDto.builder()
                        .id(reqDto.getId())
                        .auditStatus(2) // 审核不通过
                        .aiConfidence(new BigDecimal("1.0"))
                        .auditReason("内容包含不当信息，不符合平台规范")
                        .build();
                return RestResp.ok(result);
            }
            
            // 其他异常，返回待审核状态
            BookAuditRespDto result = BookAuditRespDto.builder()
                    .id(reqDto.getId())
                    .auditStatus(0) // 待审核
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("AI审核服务异常，已进入人工审核流程")
                    .build();
            return RestResp.ok(result);
        }
    }

    /**
     * AI审核章节（章节名和内容）
     */
    @Override
    public RestResp<ChapterAuditRespDto> auditChapter(ChapterAuditReqDto reqDto) {
        try {
            String content = reqDto.getContent();
            if (content == null || content.trim().isEmpty()) {
                // 内容为空时返回待审核
                ChapterAuditRespDto result = ChapterAuditRespDto.builder()
                        .bookId(reqDto.getBookId())
                        .chapterNum(reqDto.getChapterNum())
                        .auditStatus(0)
                        .aiConfidence(new BigDecimal("0.0"))
                        .auditReason("章节内容为空，需要人工审核")
                        .build();
                return RestResp.ok(result);
            }

            // 如果内容超过MAX_CONTENT_LENGTH字，进行分段审核
            if (content.length() > MAX_CONTENT_LENGTH) {
                List<String> segments = splitContent(content, MAX_CONTENT_LENGTH);
                log.info("章节内容较长，分为 {} 段进行审核，章节 bookId: {}, chapterNum: {}", 
                        segments.size(), reqDto.getBookId(), reqDto.getChapterNum());

                // 对每段进行审核
                List<ChapterAuditRespDto> segmentResults = new ArrayList<>();
                for (int i = 0; i < segments.size(); i++) {
                    String segment = segments.get(i);
                    String prompt = buildChapterAuditPrompt(reqDto.getChapterName(), segment, i + 1, segments.size());

                    try {
                        String aiResponse = chatClient.prompt()
                                .user(prompt)
                                .call()
                                .content();

                        log.info("AI审核响应，章节 bookId: {}, chapterNum: {}, 第 {}/{} 段, 响应: {}", 
                                reqDto.getBookId(), reqDto.getChapterNum(), i + 1, segments.size(), aiResponse);

                        ChapterAuditRespDto segmentResult = parseChapterAuditResponse(
                                aiResponse, reqDto.getBookId(), reqDto.getChapterNum());
                        segmentResults.add(segmentResult);
                    } catch (Exception segmentException) {
                        log.error("AI审核异常，章节 bookId: {}, chapterNum: {}, 第 {}/{} 段", 
                                reqDto.getBookId(), reqDto.getChapterNum(), i + 1, segments.size(), segmentException);
                        
                        // 检查是否是内容安全检查失败
                        if (isContentInspectionFailed(segmentException)) {
                            // 如果任何一段检测到不当内容，直接返回审核不通过
                            log.warn("第 {}/{} 段检测到不当内容，章节 bookId: {}, chapterNum: {}, 直接标记为审核不通过", 
                                    i + 1, segments.size(), reqDto.getBookId(), reqDto.getChapterNum());
                            ChapterAuditRespDto result = ChapterAuditRespDto.builder()
                                    .bookId(reqDto.getBookId())
                                    .chapterNum(reqDto.getChapterNum())
                                    .auditStatus(2) // 审核不通过
                                    .aiConfidence(new BigDecimal("1.0"))
                                    .auditReason("内容包含不当信息，不符合平台规范")
                                    .build();
                            return RestResp.ok(result);
                        } else {
                            // 其他异常，该段标记为待审核
                            ChapterAuditRespDto segmentResult = ChapterAuditRespDto.builder()
                                    .bookId(reqDto.getBookId())
                                    .chapterNum(reqDto.getChapterNum())
                                    .auditStatus(0) // 待审核
                                    .aiConfidence(new BigDecimal("0.0"))
                                    .auditReason("第" + (i + 1) + "段审核异常，已进入人工审核流程")
                                    .build();
                            segmentResults.add(segmentResult);
                        }
                    }
                }

                // 合并分段审核结果
                ChapterAuditRespDto result = mergeSegmentAuditResults(segmentResults, reqDto.getBookId(), reqDto.getChapterNum());
                return RestResp.ok(result);
            } else {
                // 内容较短，直接审核
                String prompt = buildChapterAuditPrompt(reqDto.getChapterName(), content, 1, 1);

                String aiResponse = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                log.info("AI审核响应，章节 bookId: {}, chapterNum: {}, 响应: {}", 
                        reqDto.getBookId(), reqDto.getChapterNum(), aiResponse);

                ChapterAuditRespDto result = parseChapterAuditResponse(aiResponse, reqDto.getBookId(), reqDto.getChapterNum());
                return RestResp.ok(result);
            }

        } catch (Exception e) {
            log.error("AI审核异常，章节 bookId: {}, chapterNum: {}", 
                    reqDto.getBookId(), reqDto.getChapterNum(), e);
            
            // 检查是否是内容安全检查失败（AI模型检测到不当内容）
            if (isContentInspectionFailed(e)) {
                // AI模型检测到不当内容，直接返回审核不通过
                log.warn("AI模型检测到不当内容，章节 bookId: {}, chapterNum: {}, 直接标记为审核不通过", 
                        reqDto.getBookId(), reqDto.getChapterNum());
                ChapterAuditRespDto result = ChapterAuditRespDto.builder()
                        .bookId(reqDto.getBookId())
                        .chapterNum(reqDto.getChapterNum())
                        .auditStatus(2) // 审核不通过
                        .aiConfidence(new BigDecimal("1.0"))
                        .auditReason("内容包含不当信息，不符合平台规范")
                        .build();
                return RestResp.ok(result);
            }
            
            // 其他异常，返回待审核状态
            ChapterAuditRespDto result = ChapterAuditRespDto.builder()
                    .bookId(reqDto.getBookId())
                    .chapterNum(reqDto.getChapterNum())
                    .auditStatus(0) // 待审核
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("AI审核服务异常，已进入人工审核流程")
                    .build();
            return RestResp.ok(result);
        }
    }

    /**
     * 构建审核提示词-小说名称和简介审核
     * @param bookName 小说名称
     * @param bookDesc 小说简介
     * @return 提示词
     */
    private String buildAuditPrompt(String bookName, String bookDesc) {
        return String.format(
                "你是一个专业的内容审核系统。请审核以下小说的名称和简介，判断是否符合网络文学平台的内容规范。\n\n" +
                        "小说名称：%s\n" +
                        "小说简介：%s\n\n" +
                        "审核标准：\n" +
                        "1. 内容是否包含违法违规信息（色情、暴力、政治敏感等）\n" +
                        "2. 内容是否包含低俗、恶俗内容\n" +
                        "3. 内容是否包含广告、推广信息\n" +
                        "4. 内容是否过于简短或缺乏实质性信息\n" +
                        "5. 内容是否符合网络文学的基本规范\n\n" +
                        "请以JSON格式返回审核结果，格式如下：\n" +
                        "{\n" +
                        "  \"auditStatus\": 1或2（1表示通过，2表示不通过）,\n" +
                        "  \"aiConfidence\": 0.0-1.0之间的数字（表示审核置信度）,\n" +
                        "  \"auditReason\": \"详细的审核原因说明\"\n" +
                        "}\n\n" +
                        "如果内容完全符合规范，auditStatus为1；如果存在任何问题，auditStatus为2，并在auditReason中详细说明问题。",
                bookName != null ? bookName : "",
                bookDesc != null ? bookDesc : ""
        );
    }

    /**
     * 构建章节审核提示词
     * @param chapterName 章节名称
     * @param content 要审核的内容（分段后的内容）
     * @param segmentIndex 当前段序号（从1开始）
     * @param totalSegments 总段数
     */
    private String buildChapterAuditPrompt(String chapterName, String content, int segmentIndex, int totalSegments) {
        String segmentInfo = "";
        if (totalSegments > 1) {
            segmentInfo = String.format("\n注意：这是章节内容的第 %d/%d 段，请对该段内容进行审核。\n", segmentIndex, totalSegments);
        }
        
        return String.format(
                "你是一个专业的内容审核系统。请审核以下小说章节的名称和内容，判断是否符合网络文学平台的内容规范。%s\n" +
                        "章节名称：%s\n" +
                        "章节内容：%s\n\n" +
                        "审核标准：\n" +
                        "1. 内容是否包含违法违规信息（色情、暴力、政治敏感等）\n" +
                        "2. 内容是否包含低俗、恶俗内容\n" +
                        "3. 内容是否包含广告、推广信息\n" +
                        "4. 内容是否符合网络文学的基本规范\n" +
                        "5. 内容质量是否达到发布标准\n\n" +
                        "请以JSON格式返回审核结果，格式如下：\n" +
                        "{\n" +
                        "  \"auditStatus\": 1或2（1表示通过，2表示不通过）,\n" +
                        "  \"aiConfidence\": 0.0-1.0之间的数字（表示审核置信度）,\n" +
                        "  \"auditReason\": \"详细的审核原因说明\"\n" +
                        "}\n\n" +
                        "如果内容完全符合规范，auditStatus为1；如果存在任何问题，auditStatus为2，并在auditReason中详细说明问题。",
                segmentInfo,
                chapterName != null ? chapterName : "",
                content != null ? content : ""
        );
    }

    /**
     * 解析AI响应-小说基本信息审核
     */
    private BookAuditRespDto parseAuditResponse(String aiResponse, Long bookId) {
        try {
            // 尝试从响应中提取JSON
            String jsonContent = extractJsonFromResponse(aiResponse);

            // 解析JSON（简化版，实际可以使用Jackson等库）
            Integer auditStatus = extractField(jsonContent, "auditStatus", Integer.class);
            BigDecimal aiConfidence = extractField(jsonContent, "aiConfidence", BigDecimal.class);
            String auditReason = extractField(jsonContent, "auditReason", String.class);

            // 设置默认值
            if (auditStatus == null) {
                auditStatus = 0; // 默认待审核
            }
            if (aiConfidence == null) {
                aiConfidence = new BigDecimal("0.5");
            }
            if (auditReason == null || auditReason.trim().isEmpty()) {
                auditReason = "AI审核完成";
            }

            return BookAuditRespDto.builder()
                    .id(bookId)
                    .auditStatus(auditStatus)
                    .aiConfidence(aiConfidence)
                    .auditReason(auditReason)
                    .build();

        } catch (Exception e) {
            log.error("解析AI审核响应失败，书籍ID: {}", bookId, e);
            // 解析失败时返回待审核状态
            return BookAuditRespDto.builder()
                    .id(bookId)
                    .auditStatus(0)
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("AI审核响应解析失败，已进入人工审核流程")
                    .build();
        }
    }

    /**
     * 解析AI响应-章节审核
     */
    private ChapterAuditRespDto parseChapterAuditResponse(String aiResponse, Long bookId, Integer chapterNum) {
        try {
            // 尝试从响应中提取JSON
            String jsonContent = extractJsonFromResponse(aiResponse);

            // 解析JSON（简化版，实际可以使用Jackson等库）
            Integer auditStatus = extractField(jsonContent, "auditStatus", Integer.class);
            BigDecimal aiConfidence = extractField(jsonContent, "aiConfidence", BigDecimal.class);
            String auditReason = extractField(jsonContent, "auditReason", String.class);

            // 设置默认值
            if (auditStatus == null) {
                auditStatus = 0; // 默认待审核
            }
            if (aiConfidence == null) {
                aiConfidence = new BigDecimal("0.5");
            }
            if (auditReason == null || auditReason.trim().isEmpty()) {
                auditReason = "AI审核完成";
            }

            return ChapterAuditRespDto.builder()
                    .bookId(bookId)
                    .chapterNum(chapterNum)
                    .auditStatus(auditStatus)
                    .aiConfidence(aiConfidence)
                    .auditReason(auditReason)
                    .build();

        } catch (Exception e) {
            log.error("解析AI审核响应失败，章节 bookId: {}, chapterNum: {}", bookId, chapterNum, e);
            // 解析失败时返回待审核状态
            return ChapterAuditRespDto.builder()
                    .bookId(bookId)
                    .chapterNum(chapterNum)
                    .auditStatus(0)
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("AI审核响应解析失败，已进入人工审核流程")
                    .build();
        }
    }

    /**
     * 从响应中提取JSON内容
     */
    private String extractJsonFromResponse(String response) {
        // 尝试提取JSON对象
        Pattern jsonPattern = Pattern.compile("\\{[^}]*\"auditStatus\"[^}]*\\}", Pattern.DOTALL);
        Matcher matcher = jsonPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }
        return response;
    }

    /**
     * 从JSON字符串中提取字段值（简化版解析，存入书籍表或者章节表）
     */
    @SuppressWarnings("unchecked")
    private <T> T extractField(String json, String fieldName, Class<T> type) {
        try {
            Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*([^,}\\]]+)");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                // 移除引号
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                if (type == Integer.class) {
                    return (T) Integer.valueOf(value);
                } else if (type == BigDecimal.class) {
                    return (T) new BigDecimal(value);
                } else if (type == String.class) {
                    return (T) value;
                }
            }
        } catch (Exception e) {
            log.warn("提取字段 {} 失败", fieldName, e);
        }
        return null;
    }

    /**
     * 将内容分段，每段最多指定长度
     * @param content 原始内容
     * @param maxLength 每段最大长度
     * @return 分段后的内容列表
     */
    private List<String> splitContent(String content, int maxLength) {
        List<String> segments = new ArrayList<>();
        if (content == null || content.length() <= maxLength) {
            if (content != null) {
                segments.add(content);
            }
            return segments;
        }

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxLength, content.length());
            
            // 如果不是最后一段，尝试在句号、问号、感叹号或换行符处断开，避免截断句子
            if (end < content.length()) {
                int lastPeriod = content.lastIndexOf('。', end - 1);
                int lastQuestion = content.lastIndexOf('？', end - 1);
                int lastExclamation = content.lastIndexOf('！', end - 1);
                int lastNewline = content.lastIndexOf('\n', end - 1);
                
                // 找到最接近末尾的断句位置
                int breakPoint = Math.max(Math.max(lastPeriod, lastQuestion), 
                                         Math.max(lastExclamation, lastNewline));
                
                // 如果找到了合适的断句位置（在最后200个字符内），使用该位置
                if (breakPoint > start && breakPoint > end - 200) {
                    end = breakPoint + 1;
                }
            }
            
            segments.add(content.substring(start, end));
            start = end;
        }

        return segments;
    }

    /**
     * 合并分段审核结果
     * @param segmentResults 各段的审核结果
     * @param bookId 书籍ID
     * @param chapterNum 章节号
     * @return 合并后的审核结果
     */
    private ChapterAuditRespDto mergeSegmentAuditResults(List<ChapterAuditRespDto> segmentResults, 
                                                         Long bookId, Integer chapterNum) {
        if (segmentResults == null || segmentResults.isEmpty()) {
            return ChapterAuditRespDto.builder()
                    .bookId(bookId)
                    .chapterNum(chapterNum)
                    .auditStatus(0)
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("分段审核结果为空")
                    .build();
        }

        // 合并规则：
        // 1. 如果任何一段不通过（auditStatus=2），则整体不通过
        // 2. 如果任何一段待审核（auditStatus=0），则整体待审核
        // 3. 所有段都通过时，整体通过
        // 4. 置信度取平均值
        // 5. 审核原因智能合并，优先显示不通过和待审核的段，限制总长度

        int overallStatus = 1; // 默认通过
        BigDecimal totalConfidence = BigDecimal.ZERO;
        int validConfidenceCount = 0;
        
        // 分类收集各段的审核信息
        List<String> failedReasons = new ArrayList<>(); // 不通过的段
        List<String> pendingReasons = new ArrayList<>(); // 待审核的段
        int passedCount = 0; // 通过的段数

        for (int i = 0; i < segmentResults.size(); i++) {
            ChapterAuditRespDto segment = segmentResults.get(i);
            int segmentIndex = i + 1;
            
            // 确定整体状态：2 > 0 > 1（不通过 > 待审核 > 通过）
            if (segment.getAuditStatus() != null) {
                if (segment.getAuditStatus() == 2) {
                    overallStatus = 2; // 不通过优先级最高
                } else if (segment.getAuditStatus() == 0 && overallStatus == 1) {
                    overallStatus = 0; // 待审核优先级中等
                }
            }

            // 累计置信度
            if (segment.getAiConfidence() != null) {
                totalConfidence = totalConfidence.add(segment.getAiConfidence());
                validConfidenceCount++;
            }

            // 分类收集审核原因
            String reason = segment.getAuditReason();
            if (reason != null && !reason.trim().isEmpty()) {
                String segmentReason = String.format("第%d段：%s", segmentIndex, reason.trim());
                if (segment.getAuditStatus() != null) {
                    if (segment.getAuditStatus() == 2) {
                        failedReasons.add(segmentReason);
                    } else if (segment.getAuditStatus() == 0) {
                        pendingReasons.add(segmentReason);
                    } else {
                        passedCount++;
                    }
                } else {
                    passedCount++;
                }
            } else {
                if (segment.getAuditStatus() == null || segment.getAuditStatus() == 1) {
                    passedCount++;
                }
            }
        }

        // 计算平均置信度
        BigDecimal avgConfidence = validConfidenceCount > 0
                ? totalConfidence.divide(new BigDecimal(validConfidenceCount), 2, RoundingMode.HALF_UP)
                : new BigDecimal("0.5");

        // 构建审核原因，优先显示重要信息
        String mergedReason = buildMergedReason(failedReasons, pendingReasons, passedCount, segmentResults.size(), overallStatus);

        return ChapterAuditRespDto.builder()
                .bookId(bookId)
                .chapterNum(chapterNum)
                .auditStatus(overallStatus)
                .aiConfidence(avgConfidence)
                .auditReason(mergedReason)
                .build();
    }

    /**
     * 构建合并后的审核原因，限制长度
     * @param failedReasons 不通过的段原因列表
     * @param pendingReasons 待审核的段原因列表
     * @param passedCount 通过的段数
     * @param totalSegments 总段数
     * @param overallStatus 整体审核状态
     * @return 合并后的审核原因
     */
    private String buildMergedReason(List<String> failedReasons, List<String> pendingReasons, 
                                     int passedCount, int totalSegments, int overallStatus) {
        StringBuilder reasonBuilder = new StringBuilder();
        
        // 优先显示不通过的段
        if (!failedReasons.isEmpty()) {
            reasonBuilder.append("不通过段：");
            for (String reason : failedReasons) {
                String toAppend = reason + "；";
                if (reasonBuilder.length() + toAppend.length() > MAX_AUDIT_REASON_LENGTH - 20) {
                    reasonBuilder.append("（内容过长，已截断）");
                    break;
                }
                reasonBuilder.append(toAppend);
            }
        }
        
        // 其次显示待审核的段
        if (!pendingReasons.isEmpty()) {
            if (!reasonBuilder.isEmpty()) {
                reasonBuilder.append(" ");
            }
            reasonBuilder.append("待审核段：");
            for (String reason : pendingReasons) {
                String toAppend = reason + "；";
                if (reasonBuilder.length() + toAppend.length() > MAX_AUDIT_REASON_LENGTH - 20) {
                    reasonBuilder.append("（内容过长，已截断）");
                    break;
                }
                reasonBuilder.append(toAppend);
            }
        }
        
        // 如果所有段都通过，使用简洁的总结
        if (failedReasons.isEmpty() && pendingReasons.isEmpty() && passedCount > 0) {
            if (totalSegments == 1) {
                reasonBuilder.append("内容符合网络文学平台规范，审核通过");
            } else {
                reasonBuilder.append(String.format("共%d段内容，均已审核通过，符合网络文学平台规范", totalSegments));
            }
        } else if (passedCount > 0) {
            // 如果有通过的段，在末尾添加统计信息
            String summary = String.format("（其余%d段通过）", passedCount);
            if (reasonBuilder.length() + summary.length() <= MAX_AUDIT_REASON_LENGTH - 10) {
                reasonBuilder.append(summary);
            }
        }
        
        // 清理末尾的分号
        String result = reasonBuilder.toString().trim();
        if (result.endsWith("；")) {
            result = result.substring(0, result.length() - 1);
        }
        
        // 最终长度检查和截断
        if (result.length() > MAX_AUDIT_REASON_LENGTH) {
            result = result.substring(0, MAX_AUDIT_REASON_LENGTH - 3) + "...";
        }
        
        // 如果结果为空，设置默认值
        if (result.isEmpty()) {
            result = overallStatus == 1 ? "审核通过" : (overallStatus == 2 ? "审核不通过" : "待审核");
        }
        
        return result;
    }

    /**
     * 判断是否是内容安全检查失败异常
     * 当AI模型检测到不当内容时，会抛出包含 "DataInspectionFailed" 或 "inappropriate content" 的异常
     * @param e 异常对象
     * @return true 如果是内容安全检查失败，false 否则
     */
    private boolean isContentInspectionFailed(Exception e) {
        if (e == null) {
            return false;
        }
        
        // 检查异常类型 - NonTransientAiException 且包含内容检查失败的错误码
        if (e instanceof NonTransientAiException) {
            String message = e.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                // 只有当明确包含 DataInspectionFailed 错误码时才认为是内容检查失败
                // 因为 NonTransientAiException 也可能是其他类型的错误
                return lowerMessage.contains("datainspectionfailed") ||
                       lowerMessage.contains("inappropriate content");
            }
        }
        
        // 检查异常消息中是否包含内容安全检查相关的关键词
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("datainspectionfailed") ||
                   lowerMessage.contains("inappropriate content") ||
                   lowerMessage.contains("不当内容") ||
                   lowerMessage.contains("内容安全检查");
        }
        
        return false;
    }

    @Override
    public RestResp<TextPolishRespDto> polishText(TextPolishReqDto reqDto) {
        try {
            // 构建润色提示词
            String prompt = buildPolishPrompt(reqDto);

            // 调用AI模型
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("AI润色响应: {}", aiResponse);

            // 解析响应
            TextPolishRespDto result = parsePolishResponse(aiResponse);
            return RestResp.ok(result);


        } catch (Exception e) {
            log.error("AI润色异常", e);
            // 如果出错，返回空或错误信息
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI润色服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 构建润色提示词
     */
    private String buildPolishPrompt(TextPolishReqDto reqDto) {
        return String.format(
                "你是一个专业的小说编辑。请对以下文本进行润色。\n\n" +
                        "待润色文本：%s\n\n" +
                        "润色风格：%s\n" +
                        "具体要求：%s\n\n" +
                        "请严格以JSON格式返回结果，不要包含Markdown标记或其他多余内容。注意合理小说需要分段。格式如下：\n" +
                        "{\n" +
                        "  \"polishedText\": \"润色后的文本内容\",\n" +
                        "  \"explanation\": \"润色说明（修改了哪里，为什么要这样修改）\"\n" +
                        "}",
                reqDto.getSelectedText(),
                reqDto.getStyle() != null ? reqDto.getStyle() : "通俗易懂",
                reqDto.getRequirement() != null ? reqDto.getRequirement() : "保持原意，提升文学性"
        );
    }

    /**
     * 解析润色响应
     */
    private TextPolishRespDto parsePolishResponse(String aiResponse) {
        try {
            String jsonContent = extractJsonFromResponse(aiResponse);
            
            String polishedText = extractField(jsonContent, "polishedText", String.class);
            String explanation = extractField(jsonContent, "explanation", String.class);
            
            // 如果解析失败，尝试直接使用响应作为润色文本（容错）
            if (polishedText == null) {
                polishedText = aiResponse;
                explanation = "自动润色";
            }
            
            TextPolishRespDto resp = new TextPolishRespDto();
            resp.setPolishedText(polishedText);
            resp.setExplanation(explanation);
            return resp;
        } catch (Exception e) {
            log.warn("解析润色响应失败", e);
            TextPolishRespDto resp = new TextPolishRespDto();
            resp.setPolishedText(aiResponse); // 降级处理
            resp.setExplanation("解析失败，显示原始内容");
            return resp;
        }
    }

    /**
     * 获取小说封面生成提示词
     */
    @Override
    public RestResp<String> getBookCoverPrompt(BookCoverReqDto reqDto) {
        try {
            // 1. 基础校验
            if (reqDto == null || reqDto.getBookName() == null) {
                return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
            }
            
            // 简介少于 MIN_BOOK_DESC_LENGTH 字，依据不足不予生成
            if (reqDto.getBookDesc() == null || reqDto.getBookDesc().trim().length() < MIN_BOOK_DESC_LENGTH) {
                 return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "小说简介过短（需大于30字），无法生成精准封面");
            }

            // 2. 构建提示词
            String prompt = buildImagePromptPrompt(reqDto);

            // 3. 调用AI模型
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("生成封面提示词响应，小说ID: {}, 响应: {}", reqDto.getId(), aiResponse);
            
            // 4. 拼接后缀
            String finalPrompt = aiResponse + "，高品质插画，书籍装帧风格，黄金比例构图，极致细节，最高解析度，(无文字，无水印：1.5)";

            return RestResp.ok(finalPrompt);

        } catch (Exception e) {
            log.error("生成封面提示词异常，小说ID: {}", reqDto.getId(), e);
            return RestResp.fail(ErrorCodeEnum.AI_COVER_TEXT_SERVICE_ERROR, "生成封面提示词服务暂时不可用");
        }
    }

    /**
     * 获取小说封面提示词生成的提示词
     */
    private String buildImagePromptPrompt(BookCoverReqDto dto) {
        // 截取过长的简介
        String bookDesc = dto.getBookDesc();
        if (bookDesc.length() > 500) {
            bookDesc = bookDesc.substring(0, 500);
        }
        
        String categoryName = dto.getCategoryName();
        if (categoryName == null) {
            categoryName = "通俗小说";
        }

        // 构建发给 LLM 的请求
        return String.format(
                "你的角色： 资深 AI 插画提示词专家 & 书籍装帧设计师\n" +
                "任务： 请根据提供的《%s》和《%s》，创作一段专为 AI 生图模型设计的视觉描述词。\n\n" +
                "创作指南：\n" +
                "去剧情化：不要描述故事情节（如“他爱上了她”），要描述具体的视觉画面（如“漫天大雪中，一个孤独的红色背影”）。\n" +
                "三段式结构：\n" +
                "[核心主体]：从小说的核心符号提取。可能是角色、一个象征性物体或标志性建筑。\n" +
                "[环境氛围]：根据简介基调，确定色彩、天气、时代背景。\n" +
                "[艺术规格]：统一使用：极高画质，电影级构图，高分辨率\n" +
                "封面适配逻辑（核心）：\n" +
                "禁忌词：禁止出现文字、人名、水印。\n\n" +
                "输入格式： 小说名：%s 小说类别：%s 小说简介：%s\n\n" +
                "输出格式要求（仅返回以下内容）： [视觉描述]：(100-150字内的结构化提示词)",
                dto.getBookName(),
                bookDesc,
                dto.getBookName(),
                categoryName,
                bookDesc
        );
    }

}