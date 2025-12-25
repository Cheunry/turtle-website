package com.novel.book.service;

import com.novel.book.dto.req.BookCommentPageReqDto;
import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookCommentRespDto;
import com.novel.book.dto.resp.BookContentAboutRespDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;

import java.util.List;

public interface BookReadService {

    /**
     * 看小说某章节内容
     * @param bookId,chapterNum 章节ID
     * @return 该章节小说内容
     */
    RestResp<BookContentAboutRespDto> getBookContentAbout(Long bookId, Integer chapterNum);

    /**
     * 获取书籍目录
     * @param bookId 书籍ID
     * @return 书籍章节目录列表
     */
    RestResp<List<BookChapterRespDto>> getBookChapter(Long bookId);

    /**
     * 看书籍上一章
     * @param bookId 书籍ID
     * @param chapterNum 章节号
     * @return 上一章章节号
     */
    RestResp<Integer> getPreChapterNum(Long bookId, Integer chapterNum);

    /**
     * 看书籍下一章
     * @param bookId 书籍ID
     * @param chapterNum 章节号
     * @return 下一章章节号
     */
    RestResp<Integer> getNextChapterNum(Long bookId, Integer chapterNum);


    /**
     * 发表评论
     * @param dto 评论相关 DTO
     * @return void
     */
    RestResp<Void> saveComment(BookCommentReqDto dto);


    /**
     * 删除评论
     * @param dto 评论相关 DTO
     * @return void
     */
    RestResp<Void> deleteComment(BookCommentReqDto dto);

    /**
     * 修改评论
     * @param dto 评论相关 DTO
     * @return void
     */
    RestResp<Void> updateComment(BookCommentReqDto dto);


    /**
     * 小说评论分页查询
     * @param reqDto 分页请求DTO
     * @return 小说评论分页数据
     */
    RestResp<PageRespDto<BookCommentRespDto.CommentInfo>> listCommentByPage(BookCommentPageReqDto reqDto);



}
