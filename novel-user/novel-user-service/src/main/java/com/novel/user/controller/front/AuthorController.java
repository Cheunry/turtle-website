package com.novel.user.controller.front;

import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.user.feign.BookFeignManager;
import com.novel.user.service.AuthorInfoService;
import com.novel.user.service.MessageService;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.service.impl.MessageServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import com.novel.book.dto.req.BookUptReqDto;
import com.novel.book.dto.req.BookDelReqDto;
import com.novel.ai.feign.AiFeign;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.book.dto.mq.BookUpdateMqDto;
import com.novel.book.dto.mq.BookAddMqDto;
import com.novel.book.dto.mq.ChapterSubmitMqDto;
import com.novel.common.constant.AmqpConsts;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Tag(name = "AuthorController", description = "作者模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequiredArgsConstructor
@Slf4j
@RefreshScope
@RequestMapping(ApiRouterConsts.API_AUTHOR_URL_PREFIX)
public class AuthorController {

    @Value("${novel.audit.enable:true}")
    private Boolean auditEnable;

    private final AuthorInfoService authorInfoService;
    private final BookFeignManager bookFeignManager;
    private final MessageService messageService;
    private final AiFeign aiFeign;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 校验用户是否是作家
     * 如果返回 null，则表示不是作家，前端会跳转到注册页面
     */
    @Operation(summary = "查询作家的状态")
    @GetMapping("status")
    public RestResp<AuthorInfoDto> getStatus() {

        return authorInfoService.getStatus(UserHolder.getUserId());
    }

    /**
     * 作家注册接口
     */
    @Operation(summary = "作家注册接口")
    @PostMapping("register")
    public RestResp<Void> register(@Valid @RequestBody AuthorRegisterReqDto dto) {

        dto.setUserId(UserHolder.getUserId());
        return authorInfoService.authorRegister(dto);
    }

    /**
     * 发布书籍接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 BookAddListener 消费者中异步处理
     */
    @Operation(summary = "发布书籍接口")
    @PostMapping("book")
    public RestResp<Void> publishBook(@Valid @RequestBody BookAddReqDto dto) {
        
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        // 从 UserHolder 获取作者笔名（AuthInterceptor 已查询并存储，避免重复查询）
        String penName = UserHolder.getAuthorPenName();
        if (penName == null) {
            // 如果 UserHolder 中没有笔名，说明用户不是作者，返回错误
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        // 构建MQ消息
        BookAddMqDto mqDto = BookAddMqDto.builder()
                .authorId(authorId)
                .penName(penName)
                .workDirection(dto.getWorkDirection())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .picUrl(dto.getPicUrl())
                .bookName(dto.getBookName())
                .bookDesc(dto.getBookDesc())
                .isVip(dto.getIsVip())
                .bookStatus(dto.getBookStatus())
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.BookAddMq.TOPIC + ":" + AmqpConsts.BookAddMq.TAG_ADD;
            rocketMQTemplate.convertAndSend(destination, mqDto);
            log.debug("书籍新增请求已发送到MQ，bookName: {}, authorId: {}", dto.getBookName(), authorId);
        } catch (Exception e) {
            log.error("发送书籍新增MQ消息失败，bookName: {}, authorId: {}", dto.getBookName(), authorId, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        // 立即返回，网关线程快速释放
        return RestResp.ok();
    }

    /**
     * 更新书籍接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 BookUpdateListener 消费者中异步处理
     */
    @Operation(summary = "更新书籍接口")
    @PutMapping("book/{bookId}")
    public RestResp<Void> updateBook(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Valid @RequestBody BookUptReqDto dto) {
        
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        // 构建MQ消息
        BookUpdateMqDto mqDto = BookUpdateMqDto.builder()
                .bookId(bookId)
                .authorId(authorId)
                .picUrl(dto.getPicUrl())
                .bookName(dto.getBookName())
                .bookDesc(dto.getBookDesc())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .workDirection(dto.getWorkDirection())
                .isVip(dto.getIsVip())
                .bookStatus(dto.getBookStatus())
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.BookUpdateMq.TOPIC + ":" + AmqpConsts.BookUpdateMq.TAG_UPDATE;
            rocketMQTemplate.convertAndSend(destination, mqDto);
            log.debug("书籍更新请求已发送到MQ，bookId: {}, authorId: {}", bookId, authorId);
        } catch (Exception e) {
            log.error("发送书籍更新MQ消息失败，bookId: {}, authorId: {}", bookId, authorId, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        // 立即返回，网关线程快速释放
        return RestResp.ok();
    }

    /**
     * 删除书籍接口
     */
    @Operation(summary = "删除书籍接口")
    @DeleteMapping("book/{bookId}")
    public RestResp<Void> deleteBook(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId) {
        BookDelReqDto dto = new BookDelReqDto();
        dto.setBookId(bookId);
        return bookFeignManager.deleteBook(dto);
    }

    /**
     * 小说章节发布接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 ChapterSubmitListener 消费者中异步处理
     */
    @Operation(summary = "小说章节发布接口")
    @PostMapping("book/chapter/{bookId}")
    public RestResp<Void> publishBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Valid @RequestBody ChapterAddReqDto dto) {
        
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        dto.setAuthorId(authorId);
        dto.setBookId(bookId);
        
        // 构建章节提交MQ消息
        ChapterSubmitMqDto submitDto = ChapterSubmitMqDto.builder()
                .bookId(bookId)
                .authorId(authorId)
                .chapterNum(dto.getChapterNum())
                .chapterName(dto.getChapterName())
                .content(dto.getContent())
                .isVip(dto.getIsVip())
                .operationType("CREATE")
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.ChapterSubmitMq.TOPIC + ":" + AmqpConsts.ChapterSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, submitDto);
            log.debug("章节新增请求已发送到MQ，bookId: {}, chapterNum: {}", bookId, dto.getChapterNum());
        } catch (Exception e) {
            log.error("发送章节新增MQ消息失败，bookId: {}, chapterNum: {}", bookId, dto.getChapterNum(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        // 立即返回，网关线程快速释放
        return RestResp.ok();
    }

    /**
     * 获取作者书籍列表接口
     */
    @Operation(summary = "获取作者书籍列表接口")
    @GetMapping("books")
    public RestResp<PageRespDto<BookInfoRespDto>> listBooks(@ParameterObject BookPageReqDto dto) {

        dto.setAuthorId(UserHolder.getAuthorId());

        return bookFeignManager.listPublishBooks(dto);
    }


    /**
     * 小说章节发布列表查询接口
     */
    @Operation(summary = "小说章节发布列表查询接口")
    @GetMapping("book/chapters/{bookId}")
    public RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @ParameterObject PageReqDto dto) {
        ChapterPageReqDto chapterPageReqReqDto = new ChapterPageReqDto();
        chapterPageReqReqDto.setBookId(bookId);
        chapterPageReqReqDto.setPageNum(dto.getPageNum());
        chapterPageReqReqDto.setPageSize(dto.getPageSize());
        chapterPageReqReqDto.setAuthorId(UserHolder.getAuthorId());     // 设置 AuthorId，用于后续权限校验
        return bookFeignManager.listPublishBookChapters(chapterPageReqReqDto);
    }

    /**
     * 获取单个章节详情接口
     */
    @Operation(summary = "获取单个章节详情")
    @GetMapping("book/chapter/{bookId}/{chapterNum}")
    public RestResp<BookChapterRespDto> getBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        return bookFeignManager.getBookChapter(bookId, chapterNum);
    }

    /**
     * 获取书籍详情接口（用于编辑，不过滤审核状态）
     */
    @Operation(summary = "获取书籍详情（用于编辑）")
    @GetMapping("book/{bookId}")
    public RestResp<BookInfoRespDto> getBookById(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId) {
        return bookFeignManager.getBookByIdForAuthor(bookId);
    }

    /**
     * 更新章节接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 ChapterSubmitListener 消费者中异步处理
     */
    @Operation(summary = "保存对更新章节的修改")
    @PutMapping("book/chapter_update/{bookId}/{chapterNum}")
    public RestResp<Void> updateBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum,
            @Valid @RequestBody ChapterUptReqDto dto) {
        
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        dto.setBookId(bookId);
        dto.setOldChapterNum(chapterNum);
        dto.setAuthorId(authorId);
        
        // 构建章节提交MQ消息
        ChapterSubmitMqDto submitDto = ChapterSubmitMqDto.builder()
                .bookId(bookId)
                .authorId(authorId)
                .oldChapterNum(chapterNum)
                .chapterNum(dto.getChapterNum())
                .chapterName(dto.getChapterName())
                .content(dto.getContent())
                .isVip(dto.getIsVip())
                .operationType("UPDATE")
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.ChapterSubmitMq.TOPIC + ":" + AmqpConsts.ChapterSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, submitDto);
            log.debug("章节更新请求已发送到MQ，bookId: {}, chapterNum: {}", bookId, chapterNum);
        } catch (Exception e) {
            log.error("发送章节更新MQ消息失败，bookId: {}, chapterNum: {}", bookId, chapterNum, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        // 立即返回，网关线程快速释放
        return RestResp.ok();
    }

    /**
     *  删除章节接口
     */
    @Operation(summary = "删除章节")
    @PostMapping("book/chapter/delete/{bookId}/{chapterNum}")
    public RestResp<Void> deleteBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        ChapterDelReqDto dto = new ChapterDelReqDto();
        dto.setBookId(bookId);
        dto.setChapterNum(chapterNum);
        return bookFeignManager.deleteBookChapter(dto);
    }

    /* ************************作家消息相关接口************************* */

    @Operation(summary = "获取作家消息列表")
    @PostMapping("message/list")
    public RestResp<PageRespDto<MessageRespDto>> listAuthorMessages(
        @Parameter(description = "分页参数") @RequestBody MessagePageReqDto pageReqDto
        ) {
        // 明确指定只查询作者消息（receiver_type=2），避免与普通用户消息混淆
        pageReqDto.setReceiverType(2);
        // 如果未指定消息类型，默认只查作家相关的消息（类型2:作家助手/审核）
        // 但允许前端通过busType进一步筛选（如：BOOK_AUDIT, CHAPTER_AUDIT, BOOK_COVER等）
        if (pageReqDto.getMessageType() == null) {
            pageReqDto.setMessageType(2);
        }
        return messageService.listMessages(pageReqDto);
    }

    @Operation(summary = "获取作家未读消息数量")
    @GetMapping("message/unread_count")
    public RestResp<Long> getAuthorUnReadCount() {
        // 调用专门的方法统计作者消息（receiver_type=2）
        MessageServiceImpl messageServiceImpl = (MessageServiceImpl) messageService;
        return messageServiceImpl.getUnReadCountByReceiverType(2, 2);
    }

    @Operation(summary = "标记作家消息为已读")
    @PutMapping("message/read/{id}")
    public RestResp<Void> readAuthorMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return messageService.readMessage(id);
    }

    @Operation(summary = "删除作家消息")
    @DeleteMapping("message/{id}")
    public RestResp<Void> deleteAuthorMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return messageService.deleteMessage(id);
    }

    @Operation(summary = "批量标记作家消息为已读")
    @PutMapping("message/batch_read")
    public RestResp<Void> batchReadAuthorMessages(@RequestBody java.util.List<Long> ids) {
        return messageService.batchReadMessages(2, ids);
    }

    @Operation(summary = "批量删除作家消息")
    @PostMapping("message/batch_delete")
    public RestResp<Void> batchDeleteAuthorMessages(@RequestBody java.util.List<Long> ids) {
        return messageService.batchDeleteMessages(2, ids);
    }

    @Operation(summary = "全部标记作家消息为已读")
    @PutMapping("message/all_read")
    public RestResp<Void> allReadAuthorMessages() {
        return messageService.allReadMessages(2);
    }

    @Operation(summary = "全部删除作家消息")
    @PostMapping("message/all_delete")
    public RestResp<Void> allDeleteAuthorMessages() {
        return messageService.allDeleteMessages(2);
    }

    /* ************************ AI 积分消耗相关接口 ************************* */

    /**
     * AI审核（先扣分后服务，服务失败自动回滚积分）
     * 消耗：1点/次
     * 
     * 流程：
     * 1. 先扣除积分（Redis原子操作 + 幂等性控制）
     * 2. 调用AI审核服务
     * 3. 如果AI服务失败，自动回滚积分
     */
    @Operation(summary = "AI审核（先扣分后服务）")
    @PostMapping("ai/audit")
    public RestResp<Object> audit(@RequestBody AuthorPointsConsumeReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        dto.setAuthorId(authorId);
        dto.setConsumeType(0); // 0-AI审核
        dto.setConsumePoints(1); // 1点/次
        
        // 1. 先扣除积分
        RestResp<Void> deductResult = authorInfoService.deductPoints(dto);
        if (!deductResult.isOk()) {
            return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH, deductResult.getMessage());
        }
        
        // 2. 调用AI审核服务
        try {
            Object result;
            // 判断是审核书籍还是审核章节
            if (dto.getChapterNum() != null || (dto.getContent() != null && !dto.getContent().isEmpty())) {
                // 审核章节
                ChapterAuditReqDto chapterReq = ChapterAuditReqDto.builder()
                        .bookId(dto.getRelatedId())
                        .chapterNum(dto.getChapterNum())
                        .chapterName(dto.getTitle())
                        .content(dto.getContent())
                        .build();
                RestResp<ChapterAuditRespDto> aiResp = aiFeign.auditChapter(chapterReq);
                if (!aiResp.isOk()) {
                     throw new RuntimeException("AI章节审核失败: " + aiResp.getMessage());
                }
                result = aiResp.getData();
            } else {
                // 审核书籍
                BookAuditReqDto bookReq = BookAuditReqDto.builder()
                        .id(dto.getRelatedId())
                        .bookName(dto.getBookName())
                        .bookDesc(dto.getBookDesc())
                        .build();
                RestResp<BookAuditRespDto> aiResp = aiFeign.auditBook(bookReq);
                 if (!aiResp.isOk()) {
                     throw new RuntimeException("AI书籍审核失败: " + aiResp.getMessage());
                }
                result = aiResp.getData();
            }

            return RestResp.ok(result);
            
        } catch (Exception e) {
            // 3. AI服务失败，回滚积分
            log.error("AI审核服务调用失败，开始回滚积分，作者ID: {}, 错误: {}", authorId, e.getMessage(), e);
            RestResp<Void> rollbackResult = authorInfoService.rollbackPoints(dto);
            if (!rollbackResult.isOk()) {
                log.error("积分回滚失败，作者ID: {}, 错误: {}", authorId, rollbackResult.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI服务调用失败，积分回滚也失败，请联系管理员");
            }
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI审核服务调用失败，积分已自动退回");
        }
    }

    /**
     * AI润色（先扣分后服务，服务失败自动回滚积分）
     * 消耗：10点/次
     * 
     * 流程：
     * 1. 先扣除积分（Redis原子操作 + 幂等性控制）
     * 2. 调用AI润色服务
     * 3. 如果AI服务失败，自动回滚积分
     */
    @Operation(summary = "AI润色（先扣分后服务）")
    @PostMapping("ai/polish")
    public RestResp<Object> polish(@RequestBody AuthorPointsConsumeReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        dto.setAuthorId(authorId);
        dto.setConsumeType(1); // 1-AI润色
        dto.setConsumePoints(10); // 10点/次
        
        // 1. 先扣除积分
        RestResp<Void> deductResult = authorInfoService.deductPoints(dto);
        if (!deductResult.isOk()) {
            return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH, deductResult.getMessage());
        }
        
        // 2. 调用AI润色服务
        try {
            TextPolishReqDto polishReq = new TextPolishReqDto();
            polishReq.setSelectedText(dto.getContent());
            polishReq.setStyle(dto.getStyle());
            polishReq.setRequirement(dto.getRequirement());
            
            RestResp<TextPolishRespDto> aiResp = aiFeign.polishText(polishReq);
            if (!aiResp.isOk()) {
                throw new RuntimeException("AI润色失败: " + aiResp.getMessage());
            }
            
            return RestResp.ok(aiResp.getData());
            
        } catch (Exception e) {
            // 3. AI服务失败，回滚积分
            log.error("AI润色服务调用失败，开始回滚积分，作者ID: {}, 错误: {}", authorId, e.getMessage(), e);
            RestResp<Void> rollbackResult = authorInfoService.rollbackPoints(dto);
            if (!rollbackResult.isOk()) {
                log.error("积分回滚失败，作者ID: {}, 错误: {}", authorId, rollbackResult.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI服务调用失败，积分回滚也失败，请联系管理员");
            }
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI润色服务调用失败，积分已自动退回");
        }
    }

    /**
     * AI封面提示词生成（不扣积分，仅生成提示词）
     * 
     * 注意：此接口不扣积分，仅用于生成封面提示词，用户可以在生成提示词后决定是否使用该提示词生成封面
     */
    @Operation(summary = "AI封面提示词生成（不扣积分）")
    @PostMapping("ai/cover-prompt")
    public RestResp<String> generateCoverPrompt(@RequestBody BookCoverReqDto reqDto) {
        Long authorId = UserHolder.getAuthorId();
        log.info("生成封面提示词请求，作者ID: {}, 小说ID: {}, 小说名: {}", authorId, reqDto.getId(), reqDto.getBookName());
        try {
            // 注意：此接口不扣积分，仅调用AI服务生成提示词
            RestResp<String> promptResp = aiFeign.getBookCoverPrompt(reqDto);
            if (!promptResp.isOk()) {
                log.warn("生成封面提示词失败，作者ID: {}, 小说ID: {}, 错误: {}", authorId, reqDto.getId(), promptResp.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "生成封面提示词失败: " + promptResp.getMessage());
            }
            log.info("生成封面提示词成功，作者ID: {}, 小说ID: {}, 提示词长度: {}", authorId, reqDto.getId(), 
                    promptResp.getData() != null ? promptResp.getData().length() : 0);
            return RestResp.ok(promptResp.getData());
        } catch (Exception e) {
            log.error("生成封面提示词异常，作者ID: {}, 小说ID: {}", authorId, reqDto.getId(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "生成封面提示词失败，请稍后重试");
        }
    }

    /**
     * AI封面生成（先扣分后服务，服务失败自动回滚积分）
     * 消耗：100点/次
     * 
     * 流程：
     * 1. 先扣除积分（Redis原子操作 + 幂等性控制）
     * 2. 调用AI封面生成服务
     * 3. 如果AI服务失败，自动回滚积分
     */
    @Operation(summary = "AI封面生成（先扣分后服务）")
    @PostMapping("ai/cover")
    public RestResp<Object> generateCover(@RequestBody AuthorPointsConsumeReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        dto.setAuthorId(authorId);
        dto.setConsumeType(2); // 2-AI封面
        dto.setConsumePoints(100); // 100点/次
        
        // 1. 先扣除积分
        RestResp<Void> deductResult = authorInfoService.deductPoints(dto);
        if (!deductResult.isOk()) {
            return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH, deductResult.getMessage());
        }
        
        // 2. 调用AI封面生成服务
        try {
            // 2.1 获取提示词
            BookCoverReqDto coverReq = BookCoverReqDto.builder()
                    .id(dto.getRelatedId())
                    .bookName(dto.getBookName())
                    .bookDesc(dto.getBookDesc())
                    .categoryName(dto.getCategoryName())
                    .build();
            
            RestResp<String> promptResp = aiFeign.getBookCoverPrompt(coverReq);
            if (!promptResp.isOk()) {
                throw new RuntimeException("获取封面提示词失败: " + promptResp.getMessage());
            }
            String prompt = promptResp.getData();
            
            // 2.2 生成图片
            RestResp<String> imageResp = aiFeign.generateImage(prompt);
            if (!imageResp.isOk()) {
                throw new RuntimeException("图片生成失败: " + imageResp.getMessage());
            }
            
            return RestResp.ok(imageResp.getData());
            
        } catch (Exception e) {
            // 3. AI服务失败，回滚积分
            log.error("AI封面生成服务调用失败，开始回滚积分，作者ID: {}, 错误: {}", authorId, e.getMessage(), e);
            RestResp<Void> rollbackResult = authorInfoService.rollbackPoints(dto);
            if (!rollbackResult.isOk()) {
                log.error("积分回滚失败，作者ID: {}, 错误: {}", authorId, rollbackResult.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI服务调用失败，积分回滚也失败，请联系管理员");
            }
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI封面生成服务调用失败，积分已自动退回");
        }
    }

}
