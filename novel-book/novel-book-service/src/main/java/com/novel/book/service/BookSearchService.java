package com.novel.book.service;
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
     * 小说信息查询--批量
     *
     * @param bookIds 小说ID列表
     * @return 小说信息列表
     */
    RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds);
}
