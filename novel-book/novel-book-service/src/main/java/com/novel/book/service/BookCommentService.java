package com.novel.book.service;

import com.novel.book.dto.req.BookCommentPageReqDto;
import com.novel.book.dto.req.BookCommentReqDto;
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
