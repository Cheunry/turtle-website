package com.novel.user.service;

import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import org.springframework.stereotype.Service;

@Service
public interface AuthorService {



    RestResp<Void> authorRegister(AuthorRegisterReqDto dto);

    RestResp<AuthorInfoDto> getStatus(Long userId);

    /**
     * 扣除作者积分
     * @param dto 扣分请求DTO
     * @return Void
     */
    RestResp<Void> deductPoints(AuthorPointsConsumeReqDto dto);

    /**
     * 回滚作者积分（补偿机制）
     * 当 AI 服务调用失败时，将已扣除的积分加回去
     * @param dto 扣分请求DTO（包含需要回滚的积分信息）
     * @return Void
     */
    RestResp<Void> rollbackPoints(AuthorPointsConsumeReqDto dto);

    /**
     * 发布书籍（异步化版本：发送MQ后立即返回）
     */
    RestResp<Void> publishBook(Long authorId, String penName, BookAddReqDto dto, Boolean auditEnable);

    /**
     * 更新书籍（异步化版本：发送MQ后立即返回）
     */
    RestResp<Void> updateBook(Long authorId, Long bookId, BookUptReqDto dto, Boolean auditEnable);

    /**
     * 删除书籍
     */
    RestResp<Void> deleteBook(Long bookId);

    /**
     * 发布章节（异步化版本：发送MQ后立即返回）
     */
    RestResp<Void> publishBookChapter(Long authorId, Long bookId, ChapterAddReqDto dto, Boolean auditEnable);

    /**
     * 更新章节（异步化版本：发送MQ后立即返回）
     */
    RestResp<Void> updateBookChapter(Long authorId, Long bookId, Integer chapterNum, ChapterUptReqDto dto, Boolean auditEnable);

    /**
     * 删除章节
     */
    RestResp<Void> deleteBookChapter(Long bookId, Integer chapterNum);

    /**
     * 获取作者书籍列表
     */
    RestResp<PageRespDto<BookInfoRespDto>> listBooks(Long authorId, BookPageReqDto dto);

    /**
     * 获取章节列表
     */
    RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(Long authorId, Long bookId, PageReqDto dto);

    /**
     * 获取单个章节详情
     */
    RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum);

    /**
     * 获取书籍详情（用于编辑）
     */
    RestResp<BookInfoRespDto> getBookById(Long bookId);

    /**
     * 获取作家消息列表
     */
    RestResp<PageRespDto<MessageRespDto>> listAuthorMessages(MessagePageReqDto pageReqDto);

    /**
     * 获取作家未读消息数量
     */
    RestResp<Long> getAuthorUnReadCount();

    /**
     * 标记作家消息为已读
     */
    RestResp<Void> readAuthorMessage(Long id);

    /**
     * 删除作家消息
     */
    RestResp<Void> deleteAuthorMessage(Long id);

    /**
     * 批量标记作家消息为已读
     */
    RestResp<Void> batchReadAuthorMessages(java.util.List<Long> ids);

    /**
     * 批量删除作家消息
     */
    RestResp<Void> batchDeleteAuthorMessages(java.util.List<Long> ids);

    /**
     * 全部标记作家消息为已读
     */
    RestResp<Void> allReadAuthorMessages();

    /**
     * 全部删除作家消息
     */
    RestResp<Void> allDeleteAuthorMessages();

    /**
     * AI审核（先扣分后服务，服务失败自动回滚积分）
     */
    RestResp<Object> audit(Long authorId, AuthorPointsConsumeReqDto dto);

    /**
     * AI润色（先扣分后服务，服务失败自动回滚积分）
     */
    RestResp<Object> polish(Long authorId, AuthorPointsConsumeReqDto dto);

    /**
     * AI封面提示词生成（不扣积分）
     */
    RestResp<String> generateCoverPrompt(Long authorId, BookCoverReqDto reqDto);

    /**
     * AI封面生成（先扣分后服务，服务失败自动回滚积分）
     */
    RestResp<Object> generateCover(Long authorId, AuthorPointsConsumeReqDto dto);

    /**
     * 根据用户ID查询作家信息
     * @param userId 用户ID
     * @return 作家信息DTO，如果不存在则返回null
     */
    AuthorInfoDto getAuthorInfoByUserId(Long userId);

}

