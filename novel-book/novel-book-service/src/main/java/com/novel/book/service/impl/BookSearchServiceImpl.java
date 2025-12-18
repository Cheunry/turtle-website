package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookChapterAboutRespDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import com.novel.book.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.novel.common.constant.AmqpConsts;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 查询书籍信息
     * @param bookId 小说ID
     * @return 书籍基础信息相应
     */
    @Override
    public RestResp<BookInfoRespDto> getBookById(Long bookId) {

        // 查询基础信息
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        
        // 检查书籍审核状态：只有审核通过的书籍才能被读者查看
        if (bookInfo == null || bookInfo.getAuditStatus() == null || bookInfo.getAuditStatus() != 1) {
            // 书籍不存在或未审核通过，返回错误
            return RestResp.fail(com.novel.common.constant.ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        
        // 查询首章ID（只查询审核通过的章节）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM) // Asc 正序
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookChapter firstBookChapter = bookChapterMapper.selectOne(queryWrapper);

        // 组装响应对象
        BookInfoRespDto bookInfoRespDto = BookInfoRespDto.builder()
                .id(bookInfo.getId())
                .bookName(bookInfo.getBookName())
                .bookDesc(bookInfo.getBookDesc())
                .bookStatus(bookInfo.getBookStatus())
                .authorId(bookInfo.getAuthorId())
                .authorName(bookInfo.getAuthorName())
                .categoryId(bookInfo.getCategoryId())
                .categoryName(bookInfo.getCategoryName())
                .workDirection(bookInfo.getWorkDirection()) // Add this line
                .commentCount(bookInfo.getCommentCount())
                .firstChapterNum(firstBookChapter != null ? firstBookChapter.getChapterNum() : 1) // 增加判空处理
                .lastChapterNum(bookInfo.getLastChapterNum())     // 使用 bookInfo 中的数据
                .lastChapterName(bookInfo.getLastChapterName())   // 使用 bookInfo 中的数据
                .picUrl(bookInfo.getPicUrl())
                .visitCount(bookInfo.getVisitCount())
                .wordCount(bookInfo.getWordCount())
                .updateTime(bookInfo.getUpdateTime())
                .build();

        return RestResp.ok(bookInfoRespDto);
    }


    /**
     * 获取书籍列表
     * @param bookIds 小说ID列表
     * @return 书籍基础信息列表
     */
    @Override
    public RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds) {
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds)
                .eq("audit_status", 1); // 只返回审核通过的书籍
        return RestResp.ok(
                bookInfoMapper.selectList(queryWrapper).stream().map(v -> BookInfoRespDto.builder()
                        .id(v.getId())
                        .bookName(v.getBookName())
                        .authorName(v.getAuthorName())
                        .picUrl(v.getPicUrl())
                        .bookDesc(v.getBookDesc())
                        .auditStatus(v.getAuditStatus()) // 包含审核状态
                        .build()).collect(Collectors.toList()));
    }

    @Override
    public RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds) {
        // 用于书架查询，不过滤审核状态，返回所有书籍
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds);
        return RestResp.ok(
                bookInfoMapper.selectList(queryWrapper).stream().map(v -> {
                    // 查询首章ID（只查询审核通过的章节）
                    QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
                    chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, v.getId())
                            .eq("audit_status", 1) // 只查询审核通过的章节
                            .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
                    BookChapter firstBookChapter = bookChapterMapper.selectOne(chapterQueryWrapper);
                    
                    return BookInfoRespDto.builder()
                            .id(v.getId())
                            .bookName(v.getBookName())
                            .authorName(v.getAuthorName())
                            .picUrl(v.getPicUrl())
                            .bookDesc(v.getBookDesc())
                            .auditStatus(v.getAuditStatus() != null ? v.getAuditStatus() : 0) // 包含审核状态
                            .firstChapterNum(firstBookChapter != null ? firstBookChapter.getChapterNum() : null)
                            .build();
                }).collect(Collectors.toList()));
    }

    /**
     * 增加书籍访问量
     * @param bookId 小说ID
     * @return void
     */
    @Override
    public RestResp<Void> addVisitCount(Long bookId) {
        bookInfoMapper.addVisitCount(bookId);
        // 发送消息更新 ES
        rocketMQTemplate.convertAndSend(AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE, bookId);
        return RestResp.ok();
    }

    /**
     * 获取书籍最新章节信息
     * @param bookId 小说ID
     * @return 书籍章节基础信息
     */
    @Override
    public RestResp<BookChapterAboutRespDto> getLastChapterAbout(Long bookId) {
        // 查询小说信息
//        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookId);


        // 查询最新章节信息（只查询审核通过的章节，auditStatus=1）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookChapter latestChapter = bookChapterMapper.selectOne(queryWrapper);

        // 如果章节为空，直接返回空对象
        if (latestChapter == null) {
            return RestResp.ok(BookChapterAboutRespDto.builder()
                    .chapterTotal(0L)
                    .contentSummary("暂无章节")
                    .build());
        }

        // 将 entity 转换为 dto
        BookChapterRespDto bookChapter = BookChapterRespDto.builder()
                .bookId(latestChapter.getBookId())
                .chapterNum(latestChapter.getChapterNum())
                .chapterName(latestChapter.getChapterName())
                .chapterWordCount(latestChapter.getWordCount())
                .chapterUpdateTime(latestChapter.getUpdateTime())
                .isVip(latestChapter.getIsVip())
                .content(latestChapter.getContent())
                .build();

        // 章节内容
        String content = bookChapter.getContent();
        
        // 查询章节总数（只统计审核通过的章节）
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1); // 只统计审核通过的章节
        Long chapterTotal = bookChapterMapper.selectCount(chapterQueryWrapper);

        // 组装数据并返回
        return RestResp.ok(BookChapterAboutRespDto.builder()
                .chapterInfo(bookChapter)
                .chapterTotal(chapterTotal)
                .contentSummary(content != null ? content.substring(0, Math.min(content.length(), 30)) : "暂无内容")
                .build());
    }

}
