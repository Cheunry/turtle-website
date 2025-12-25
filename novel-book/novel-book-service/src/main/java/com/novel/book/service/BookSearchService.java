package com.novel.book.service;
import com.novel.book.dto.resp.*;
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

    /**
     * 查询首页小说展示列表
     * @return 首页小说展示列表的rest响应结果
     */
    RestResp<List<HomeBookRespDto>> listHomeBook();

    /**
     * 查询下一批保存到 ES 中的小说列表
     * @param maxBookId 已查询的最大小说ID
     * @return 小说列表
     */
    RestResp<List<BookEsRespDto>> listNextEsBooks(Long maxBookId);

    /**
     * 根据 ID 获取 ES 书籍数据
     * @param bookId 书籍ID
     * @return Elasticsearch 存储小说 DTO
     */
    RestResp<BookEsRespDto> getEsBookById(Long bookId);

    /**
     * 小说点击榜查询
     * @return 小说点击排行列表
     */
    RestResp<List<BookRankRespDto>> listVisitRankBooks();

    /**
     * 小说新书榜查询
     * @return 小说新书排行列表
     */
    RestResp<List<BookRankRespDto>> listNewestRankBooks();

    /**
     * 小说更新榜查询
     * @return 小说更新排行列表
     */
    RestResp<List<BookRankRespDto>> listUpdateRankBooks();


    /**
     * 小说分类列表查询
     * @param workDirection 作品方向;0-男频 1-女频
     * @return 分类列表
     */
    RestResp<List<BookCategoryRespDto>> listCategory(Integer workDirection);

    /**
     * 小说推荐列表查询
     * @param bookId 小说ID
     * @return 推荐列表
     */
    RestResp<List<BookInfoRespDto>> listRecBooks(Long bookId);
}
