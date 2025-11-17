package com.novel.book.service;

import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dto.req.BookSearchReqDto;

import java.util.List;

public interface BookSearchService {

    List<BookInfo> searchByBookName(BookSearchReqDto dto);

}
