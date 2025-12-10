package com.novel.book.service;

import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.common.resp.RestResp;

import java.util.List;

public interface BookEsService {

    /**
     * 查询下一批保存到 ES 中的小说列表
     *
     * @param maxBookId 已查询的最大小说ID
     * @return 小说列表
     */
    RestResp<List<BookEsRespDto>> listNextEsBooks(Long maxBookId);

    /**
     * 根据 ID 获取 ES 书籍数据
     */
    RestResp<BookEsRespDto> getEsBookById(Long bookId);

}
