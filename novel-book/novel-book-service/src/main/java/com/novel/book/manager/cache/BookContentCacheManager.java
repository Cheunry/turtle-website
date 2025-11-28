package com.novel.book.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 *  小说内容：缓存管理类
 */
@Component
@RequiredArgsConstructor
public class BookContentCacheManager {

    private final BookChapterMapper bookChapterMapper;

    /**
     * 查询小说内容，并放入缓存中
     */
//    @Cacheable(cacheManager = CacheConsts.REDIS_CACHE_MANAGER_PLAIN,
//            value = CacheConsts.BOOK_CONTENT_CACHE_NAME)
    public String getBookContent(Long chapterId) {
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        return bookChapter != null ? bookChapter.getContent() : null;
    }


}
