package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.book.dao.entity.BookComment;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookCommentMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.book.dto.req.CommentPageReqDto;
import com.novel.book.dto.resp.BookCommentRespDto;
import com.novel.book.manager.feign.UserFeignManager;
import com.novel.book.service.BookCommentService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.config.annotation.Key;
import com.novel.config.annotation.Lock;
import com.novel.user.dto.resp.UserInfoRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookCommentServiceImpl implements BookCommentService {

    private final BookCommentMapper bookCommentMapper;
    private final UserFeignManager userFeignManager;


    /**
     * 保存用户评论
     * @param dto 评论相关 DTO
     * @return void
     */
    @Lock(prefix = "userComment")
    @Override
    public RestResp<Void> saveComment(
            @Key(expr = "#{userId + '::' + bookId}") BookCommentReqDto dto) {
        // 校验用户是否已发表评论
        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_USER_ID, dto.getUserId())
                .eq(DatabaseConsts.BookCommentTable.COLUMN_BOOK_ID, dto.getBookId());
        if (bookCommentMapper.selectCount(queryWrapper) > 0) {
            // 用户已发表评论
            return RestResp.fail(ErrorCodeEnum.USER_COMMENTED);
        }
        BookComment bookComment = new BookComment();
        bookComment.setBookId(dto.getBookId());
        bookComment.setUserId(dto.getUserId());
        bookComment.setCommentContent(dto.getCommentContent());
        bookComment.setCreateTime(LocalDateTime.now());
        bookComment.setUpdateTime(LocalDateTime.now());
        bookCommentMapper.insert(bookComment);
        return RestResp.ok();
    }

    /**
     * 展示小说最新评论
     * @param bookId 小说ID
     * @return 最新评论列表
     */
    @Override
    public RestResp<BookCommentRespDto> listNewestComments(Long bookId) {
        // 查询评论总数
        QueryWrapper<BookComment> commentCountQueryWrapper = new QueryWrapper<>();
        commentCountQueryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_BOOK_ID, bookId);
        Long commentTotal = bookCommentMapper.selectCount(commentCountQueryWrapper);
        BookCommentRespDto bookCommentRespDto = BookCommentRespDto.builder()
                .commentTotal(commentTotal).build();
        if (commentTotal > 0) {

            // 查询最新的评论列表
            QueryWrapper<BookComment> commentQueryWrapper = new QueryWrapper<>();
            commentQueryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_BOOK_ID, bookId)
                    .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName())
                    .last(DatabaseConsts.SqlEnum.LIMIT_5.getSql());
            List<BookComment> bookComments = bookCommentMapper.selectList(commentQueryWrapper);

            // 查询评论用户信息，并设置需要返回的评论用户名
            List<Long> userIds = bookComments.stream().map(BookComment::getUserId).toList();
            List<UserInfoRespDto> userInfos = userFeignManager.listUserInfoByIds(userIds);
            Map<Long, UserInfoRespDto> userInfoMap = userInfos.stream()
                    .collect(Collectors.toMap(UserInfoRespDto::getId, Function.identity()));
            List<BookCommentRespDto.CommentInfo> commentInfos = bookComments.stream()
                    .map(v -> {
                        UserInfoRespDto userInfo = userInfoMap.get(v.getUserId());
                        return BookCommentRespDto.CommentInfo.builder()
                            .id(v.getId())
                            .commentUserId(v.getUserId())
                            .commentUser(userInfo != null ? userInfo.getNickName() : "未知用户")
                            .commentUserPhoto(userInfo != null ? userInfo.getUserPhoto() : "")
                            .commentContent(v.getCommentContent())
                            .commentTime(v.getCreateTime()).build();
                    }).toList();
            bookCommentRespDto.setComments(commentInfos);
        } else {
            bookCommentRespDto.setComments(Collections.emptyList());
        }
        return RestResp.ok(bookCommentRespDto);
    }

    /**
     * 删除评论
     * @param dto 评论相关 DTO
     * @return void
     */
    @Override
    public RestResp<Void> deleteComment(BookCommentReqDto dto) {
        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.CommonColumnEnum.ID.getName(), dto.getCommentId())
                .eq(DatabaseConsts.BookCommentTable.COLUMN_USER_ID, dto.getUserId());
        bookCommentMapper.delete(queryWrapper);
        return RestResp.ok();
    }

    /**
     * 更新评论
     * @param dto 评论相关 DTO
     * @return void
     */
    @Override
    public RestResp<Void> updateComment(BookCommentReqDto dto) {
        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.CommonColumnEnum.ID.getName(), dto.getCommentId())
                .eq(DatabaseConsts.BookCommentTable.COLUMN_USER_ID, dto.getUserId());
        BookComment bookComment = new BookComment();
        bookComment.setCommentContent(dto.getCommentContent());
        bookCommentMapper.update(bookComment, queryWrapper);
        return RestResp.ok();
    }


}
