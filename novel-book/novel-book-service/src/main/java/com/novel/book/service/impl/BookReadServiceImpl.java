package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookComment;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookCommentMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.req.BookCommentPageReqDto;
import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookCommentRespDto;
import com.novel.book.dto.resp.BookContentAboutRespDto;
import com.novel.book.feign.UserFeignManager;
import com.novel.book.service.BookReadService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.UserInfoRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookReadServiceImpl implements BookReadService {

    private final BookChapterMapper bookChapterMapper;
    private final BookInfoMapper bookInfoMapper;
    private final BookCommentMapper bookCommentMapper;
    private final UserFeignManager userFeignManager;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 看小说某章节内容
     * @param bookId,chapterNum 章节ID
     * @return 该章节小说内容
     */
    @Override
    public RestResp<BookContentAboutRespDto> getBookContentAbout(Long bookId, Integer chapterNum) {

        // 1. 查询小说基本信息
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        
        // 检查书籍审核状态：只有审核通过的书籍才能被读者查看
        if (bookInfo == null || bookInfo.getAuditStatus() == null || bookInfo.getAuditStatus() != 1) {
            // 书籍不存在或未审核通过，返回空内容
            return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(BookContentAboutRespDto.BookInfo.builder()
                    .categoryName(null)
                    .authorName(null)
                    .build())
                .chapterInfo(BookContentAboutRespDto.ChapterInfo.builder()
                    .bookId(null)
                    .chapterNum(null)
                    .chapterName(null)
                    .chapterWordCount(null)
                    .chapterUpdateTime(null)
                    .build())
                .bookContent(null)
                .build());
        }

        // 2. 查询章节信息（包含 content），只查询审核通过的章节
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
                .eq("audit_status", 1); // 只查询审核通过的章节
        BookChapter bookChapter = bookChapterMapper.selectOne(chapterQueryWrapper);

        // 3. 组装并返回
        return RestResp.ok(BookContentAboutRespDto.builder()
            .bookInfo(BookContentAboutRespDto.BookInfo.builder()
                .categoryName(bookInfo != null ? bookInfo.getCategoryName() : null)
                .authorName(bookInfo != null ? bookInfo.getAuthorName() : null)
                .build())
            .chapterInfo(BookContentAboutRespDto.ChapterInfo.builder()
                .bookId(bookChapter != null ? bookChapter.getBookId() : null)
                .chapterNum(bookChapter != null ? bookChapter.getChapterNum() : null)
                .chapterName(bookChapter != null ? bookChapter.getChapterName() : null)
                .chapterWordCount(bookChapter != null ? bookChapter.getWordCount() : null)
                .chapterUpdateTime(bookChapter != null ? bookChapter.getUpdateTime() : null)
                .build())
            .bookContent(bookChapter != null ? bookChapter.getContent() : null)
            .build());
    }

    /**
     * 看书籍下一章
     * @param bookId 书籍ID
     * @param chapterNum 章节号
     * @return 下一章章节号
     */
    @Override
    public RestResp<Integer> getNextChapterNum(Long bookId, Integer chapterNum) {

        // 获取下一章（只查询审核通过的章节）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .gt(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
                .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(
                Optional.ofNullable(bookChapterMapper.selectOne(queryWrapper))
                        .map(BookChapter::getChapterNum).orElse(null)
        );

    }

    /**
     * 看书籍上一章
     * @param bookId 书籍ID
     * @param chapterNum 章节号
     * @return 上一章章节号
     */
    @Override
    public RestResp<Integer> getPreChapterNum(Long bookId, Integer chapterNum) {

        // 获取上一章
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .lt(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(
                Optional.ofNullable(bookChapterMapper.selectOne(queryWrapper))
                        .map(BookChapter::getChapterNum).orElse(null)
        );
    }

    /**
     * 获取书籍目录
     * @param bookId 书籍ID
     * @return 书籍章节目录列表
     */
    @Override
    public RestResp<List<BookChapterRespDto>> getBookChapter(Long bookId) {

        // 只查询审核通过的章节（auditStatus=1）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM);

        return RestResp.ok(
                bookChapterMapper.selectList(queryWrapper).stream()
                        .map(v -> BookChapterRespDto.builder()
                                .chapterNum(v.getChapterNum())
                                .chapterName(v.getChapterName())
                                .chapterWordCount(v.getWordCount())
                                .chapterUpdateTime(v.getUpdateTime())
                                .isVip(v.getIsVip()).build()
                        ).toList()
        );

    }


    /**
     * 保存用户评论到数据库。
     *
     * <p>使用 Redis SETNX 实现防重复提交（防抖），防止用户在短时间内重复提交评论。</p>
     *
     * @param dto 包含书籍ID、用户ID和评论内容的请求DTO。
     * @return {@code RestResp<Void>} 表示操作成功的响应。
     *
     * @implNote
     * 业务规则说明：
     * 1. 允许同一用户对同一本书发表多条评论。
     * 2. 使用 Redis SETNX 防止 3 秒内的重复提交。
     */
    @Override
    public RestResp<Void> saveComment(BookCommentReqDto dto) {
        // 生成防抖 Key：同一用户对同一本书的连续提交应该被防抖
        String debounceKey = "comment:debounce:" + dto.getUserId() + ":" + dto.getBookId();

        // 尝试设置 Key，3 秒过期。如果已存在，说明是重复提交
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(debounceKey, "1", 3, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(success)) {
            // Key 已存在，说明 3 秒内有重复提交
            return RestResp.fail(ErrorCodeEnum.USER_REQ_MANY);
        }

        try {
            // 执行业务逻辑
            BookComment bookComment = new BookComment();
            bookComment.setBookId(dto.getBookId());
            bookComment.setUserId(dto.getUserId());
            bookComment.setCommentContent(dto.getCommentContent());
            bookComment.setCreateTime(LocalDateTime.now());
            bookComment.setUpdateTime(LocalDateTime.now());
            bookCommentMapper.insert(bookComment);
            return RestResp.ok();
        } catch (Exception e) {
            // 如果插入失败，删除防抖 Key，允许重试
            stringRedisTemplate.delete(debounceKey);
            throw e;
        }
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
