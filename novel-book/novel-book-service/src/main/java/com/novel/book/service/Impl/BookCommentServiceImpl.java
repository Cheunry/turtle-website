package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.book.dao.entity.BookComment;
import com.novel.book.dao.mapper.BookCommentMapper;
import com.novel.book.dto.req.BookCommentPageReqDto;
import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.book.dto.resp.BookCommentRespDto;
import com.novel.book.manager.feign.UserFeignManager;
import com.novel.book.service.BookCommentService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.config.annotation.Key;
import com.novel.config.annotation.Lock;
import com.novel.user.dto.resp.UserInfoRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookCommentServiceImpl implements BookCommentService {

    private final BookCommentMapper bookCommentMapper;
    private final UserFeignManager userFeignManager;


    /**
     * 保存用户评论到数据库。
     *
     * <p>此方法实现了核心的评论发布逻辑，并通过分布式锁机制进行防重复提交处理。</p>
     *
     * @param dto 包含书籍ID、用户ID和评论内容的请求DTO。
     * @return {@code RestResp<Void>} 表示操作成功的响应。
     *
     * @implNote
     * 业务规则说明：
     * 1. 允许同一用户对同一本书发表多条评论。
     * 2. 依赖数据库的自增主键来区分每条评论。
     */
    @Lock(prefix = "userComment")
    @Override
    public RestResp<Void> saveComment(
            /*
             * 分布式锁 Key 表达式：
             * 用于生成 Redisson 分布式锁的唯一 Key。
             * Key = "userComment:" + userId + "::" + bookId + "::" + commentContent
             * 作用：实现“防抖”和“防并发重复提交”。
             * 只要用户在极短的时间内（锁持有期间）再次提交**内容、书籍、用户ID完全相同**的请求，
             * 后续请求将因无法获取锁而失败，从而避免了重复数据。
             */
            @Key(expr = "#{userId + '::' + bookId + '::' + commentContent}") BookCommentReqDto dto) {
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
     * 分页展示小说评论列表
     * @param reqDto 小说评论分页请求Dto
     * @return 小说最新评论数据
     */
    @Override
    public RestResp<PageRespDto<BookCommentRespDto.CommentInfo>> listCommentByPage(BookCommentPageReqDto reqDto) {
        IPage<BookComment> page = new Page<>();
        page.setCurrent(reqDto.getPageNum());
        page.setSize(reqDto.getPageSize());

        QueryWrapper<BookComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookCommentTable.COLUMN_BOOK_ID, reqDto.getBookId())
                // 如果 userId 不为空，则添加筛选条件
                .eq(Objects.nonNull(reqDto.getUserId()), DatabaseConsts.BookCommentTable.COLUMN_USER_ID, reqDto.getUserId())
                .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());

        IPage<BookComment> bookCommentPage = bookCommentMapper.selectPage(page, queryWrapper);

        List<BookComment>  bookComments = bookCommentPage.getRecords();
        if(CollectionUtils.isEmpty(bookComments)) {
            return RestResp.ok(PageRespDto.of(reqDto.getPageNum(), reqDto.getPageSize(), 0, Collections.emptyList()));
        }
        List<Long> userIds = bookComments.stream().map(BookComment::getUserId).toList();
        List<UserInfoRespDto> userInfos  = userFeignManager.listUserInfoByIds(userIds);

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
                            .commentCreateTime(v.getCreateTime())
                            .commentUpdateTime(v.getUpdateTime()).build();
                }).toList();

        return RestResp.ok(PageRespDto.of(reqDto.getPageNum(), reqDto.getPageSize(), bookCommentPage.getTotal(), commentInfos));
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
        bookComment.setUpdateTime(LocalDateTime.now());
        bookCommentMapper.update(bookComment, queryWrapper);
        return RestResp.ok();
    }




}
