package com.novel.book.service;

import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.book.dto.req.CommentPageReqDto;
import com.novel.book.dto.resp.BookCommentRespDto;
import com.novel.common.resp.RestResp;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;

public interface BookCommentService {

    /**
     * 发表评论
     * @param dto 评论相关 DTO
     * @return void
     */
    RestResp<Void> saveComment(BookCommentReqDto dto);

    /**
     * 小说最新评论查询
     * @param bookId 小说ID
     * @return 小说最新评论数据
     */
    RestResp<BookCommentRespDto> listNewestComments(Long bookId);

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

}
