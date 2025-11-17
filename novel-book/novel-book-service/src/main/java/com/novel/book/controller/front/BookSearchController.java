package com.novel.book.controller.front;

import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dto.req.BookSearchReqDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import com.novel.book.service.BookSearchService;

import java.util.List;

public class BookSearchController {

    private BookSearchService bookSearchService;

    @Operation(summary = "批量查询小说信息")
    @PostMapping("book")
    public ResponseEntity<List<BookInfo>> searchByBookName(@Valid BookSearchReqDto dto) {

        List<BookInfo> result = bookSearchService.searchByBookName(dto);
        return ResponseEntity.ok(result);
    }

}
