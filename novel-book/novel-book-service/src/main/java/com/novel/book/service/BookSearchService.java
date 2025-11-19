package com.novel.book.service;

import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dto.req.BookSearchReqDto;
import com.novel.book.dto.resp.BookCategoryRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.RestResp;

import java.util.List;

public interface BookSearchService {


    /**
     * 小说信息查询
     *
     * @param bookId 小说ID
     * @return 小说信息
     */
    RestResp<BookInfoRespDto> getBookById(Long bookId);

    /**
     * 小说分类列表查询
     *
     * @param workDirection 作品方向;0-男频 1-女频
     * @return 分类列表
     */
    RestResp<List<BookCategoryRespDto>> listCategory(Integer workDirection);


    /**
     * 批量查询小说信息
     *
     * @param bookIds 小说ID列表
     * @return 小说信息列表
     */
    RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds);
}
