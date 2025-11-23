package com.novel.book.service;

import com.novel.book.dto.resp.BookContentAboutRespDto;
import com.novel.common.resp.RestResp;

public interface BookReadService {

    RestResp<BookContentAboutRespDto> getBookContentAbout(Long chapterId);

}
