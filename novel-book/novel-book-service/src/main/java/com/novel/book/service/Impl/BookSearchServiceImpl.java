package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookChapterAboutRespDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.manager.cache.BookContentCacheManager;
import com.novel.book.manager.cache.BookInfoCacheManager;
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

    private final BookInfoCacheManager bookInfoCacheManager;
    private final BookInfoMapper bookInfoMapper;
    private final BookContentCacheManager bookContentCacheManager;
    private final BookChapterMapper bookChapterMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public RestResp<BookInfoRespDto> getBookById(Long bookId) {
        return RestResp.ok(bookInfoCacheManager.getBookInfo(bookId));
    }


    @Override
    public RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds) {
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds);
        return RestResp.ok(
                bookInfoMapper.selectList(queryWrapper).stream().map(v -> BookInfoRespDto.builder()
                        .id(v.getId())
                        .bookName(v.getBookName())
                        .authorName(v.getAuthorName())
                        .picUrl(v.getPicUrl())
                        .bookDesc(v.getBookDesc())
                        .build()).collect(Collectors.toList()));
    }

    @Override
    public RestResp<Void> addVisitCount(Long bookId) {
        bookInfoMapper.addVisitCount(bookId);
        // 发送消息更新 ES
        rocketMQTemplate.convertAndSend(AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE, bookId);
        return RestResp.ok();
    }

    @Override
    public RestResp<BookChapterAboutRespDto> getLastChapterAbout(Long bookId) {
        // 1. 查询小说信息
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookId);

        // 2. 查询最新章节信息（不依赖 bookInfo.getLastChapterNum()，直接查表获取真实最大章节号）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookChapter latestChapter = bookChapterMapper.selectOne(queryWrapper);

        // 3. 如果章节为空，直接返回空对象
        if (latestChapter == null) {
            return RestResp.ok(BookChapterAboutRespDto.builder()
                    .chapterTotal(0L)
                    .contentSummary("暂无章节")
                    .build());
        }

        // 4. 将 entity 转换为 dto
        BookChapterRespDto bookChapter = BookChapterRespDto.builder()
                .bookId(latestChapter.getBookId())
                .chapterNum(latestChapter.getChapterNum())
                .chapterName(latestChapter.getChapterName())
                .chapterWordCount(latestChapter.getWordCount())
                .chapterUpdateTime(latestChapter.getUpdateTime())
                .isVip(latestChapter.getIsVip())
                .build();

        // 5. 查询章节内容
        String content = bookContentCacheManager.getBookContent(bookId, latestChapter.getChapterNum());
        
        // 6. 查询章节总数
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId);
        Long chapterTotal = bookChapterMapper.selectCount(chapterQueryWrapper);

        // 7. 组装数据并返回
        return RestResp.ok(BookChapterAboutRespDto.builder()
                .chapterInfo(bookChapter)
                .chapterTotal(chapterTotal)
                .contentSummary(content != null ? content.substring(0, Math.min(content.length(), 30)) : "暂无内容")
                .build());
    }

}
