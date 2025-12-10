package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.service.BookEsService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookEsServiceImpl implements BookEsService {

    private final BookInfoMapper bookInfoMapper;

    /**
     * 查询下一批保存到 ES 中的小说列表
     *
     * @param maxBookId 已查询的最大小说ID
     * @return 小说列表
     */
    @Override
    public RestResp<List<BookEsRespDto>> listNextEsBooks(Long maxBookId) {

        // 如果 maxBookId 为 null 或者小于等于 0，则从第一条记录开始拉取
        if (maxBookId == null || maxBookId <= 0) {
            maxBookId = 0L; // 确保第一次查询时，ID大于0的记录都能被查到
        }

        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.clear();
        /*
        queryWrapper.怎么查询呢？
            按 ID 升序排列
            查询 ID 大于 maxBookId 的记录（实现“下一批”）
            确保小说 字数大于 0（wordCount > 0）
            限制查询数量（例如 30 条）
         */
        queryWrapper.orderByAsc(DatabaseConsts.CommonColumnEnum.ID.getName())
                .gt(DatabaseConsts.CommonColumnEnum.ID.getName(), maxBookId)
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT,0)
                .last(DatabaseConsts.SqlEnum.LIMIT_30.getSql());

        return RestResp.ok(
                bookInfoMapper.selectList(queryWrapper).stream().map(bookInfo -> BookEsRespDto.builder()
                        .id(bookInfo.getId())
                        .workDirection(bookInfo.getWorkDirection())
                        .categoryId(bookInfo.getCategoryId())
                        .categoryName(bookInfo.getCategoryName())
                        .bookName(bookInfo.getBookName())
                        .picUrl(bookInfo.getPicUrl())
                        .authorName(bookInfo.getAuthorName())
                        .bookDesc(bookInfo.getBookDesc())
                        .score(bookInfo.getScore())
                        .bookStatus(bookInfo.getBookStatus())
                        .visitCount(bookInfo.getVisitCount())
                        .wordCount(bookInfo.getWordCount())
                        .commentCount(bookInfo.getCommentCount())
                        .lastChapterName(bookInfo.getLastChapterName())
                        // 将数据库中的时间转换为一个标准的 Unix 时间戳（毫秒数），这是一个 Long 类型。
                        // 增加判空处理，防止 NPE
                        .lastChapterUpdateTime(bookInfo.getLastChapterUpdateTime() != null
                                ? bookInfo.getLastChapterUpdateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                                : 0L)
                        .isVip(bookInfo.getIsVip())
                        .build()
                ).toList()
        );

    }

    @Override
    public RestResp<BookEsRespDto> getEsBookById(Long bookId) {
        
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (bookInfo == null) {
            return RestResp.ok(null);
        }

        BookEsRespDto bookEsDto = BookEsRespDto.builder()
                .id(bookInfo.getId())
                .workDirection(bookInfo.getWorkDirection())
                .categoryId(bookInfo.getCategoryId())
                .categoryName(bookInfo.getCategoryName())
                .bookName(bookInfo.getBookName())
                .picUrl(bookInfo.getPicUrl())
                .authorName(bookInfo.getAuthorName())
                .bookDesc(bookInfo.getBookDesc())
                .score(bookInfo.getScore())
                .bookStatus(bookInfo.getBookStatus())
                .visitCount(bookInfo.getVisitCount())
                .wordCount(bookInfo.getWordCount())
                .commentCount(bookInfo.getCommentCount())
                .lastChapterName(bookInfo.getLastChapterName())
                // 将数据库中的时间转换为一个标准的 Unix 时间戳（毫秒数），这是一个 Long 类型。
                // 这里也要判空
                .lastChapterUpdateTime(bookInfo.getLastChapterUpdateTime() != null
                        ? bookInfo.getLastChapterUpdateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                        : 0L)
                .isVip(bookInfo.getIsVip())
                .build();

        return RestResp.ok(bookEsDto);
    }
}
