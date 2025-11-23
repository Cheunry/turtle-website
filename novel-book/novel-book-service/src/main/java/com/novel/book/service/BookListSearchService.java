package com.novel.book.service;

import com.novel.book.dto.resp.BookCategoryRespDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookRankRespDto;
import com.novel.common.resp.RestResp;

import java.util.List;

public interface BookListSearchService {



    /**
     * 小说点击榜查询
     *
     * @return 小说点击排行列表
     */
    RestResp<List<BookRankRespDto>> listVisitRankBooks();

    /**
     * 小说新书榜查询
     *
     * @return 小说新书排行列表
     */
    RestResp<List<BookRankRespDto>> listNewestRankBooks();

    /**
     * 小说更新榜查询
     *
     * @return 小说更新排行列表
     */
    RestResp<List<BookRankRespDto>> listUpdateRankBooks();

    /**
     * 小说章节列表查询
     *
     * @param bookId 小说ID
     * @return 小说章节列表
     */
    RestResp<List<BookChapterRespDto>> listChapters(Long bookId);

    /**
     * 小说分类列表查询
     *
     * @param workDirection 作品方向;0-男频 1-女频
     * @return 分类列表
     */
    RestResp<List<BookCategoryRespDto>> listCategory(Integer workDirection);


}
