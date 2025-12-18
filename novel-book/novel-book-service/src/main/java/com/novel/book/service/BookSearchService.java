package com.novel.book.service;
import com.novel.book.dto.resp.BookChapterAboutRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.RestResp;

import java.util.List;

public interface BookSearchService {

    /**
     * 小说信息查询
     * @param bookId 小说ID
     * @return 小说信息
     */
    RestResp<BookInfoRespDto> getBookById(Long bookId);


    /**
     * 小说信息查询--批量
     * @param bookIds 小说ID列表
     * @return 小说信息列表
     */
    RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds);

    /**
     * 小说信息查询--批量（用于书架，不过滤审核状态）
     * @param bookIds 小说ID列表
     * @return 小说信息列表（包含所有审核状态的书籍）
     */
    RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds);

    /**
     * 增加小说点击量
     * @param bookId 小说ID
     * @return void
     */
    RestResp<Void> addVisitCount(Long bookId);

    /**
     * 小说最新章节相关信息查询
     * @param bookId 小说ID
     * @return 章节相关联的信息
     */
    RestResp<BookChapterAboutRespDto> getLastChapterAbout(Long bookId);
}
