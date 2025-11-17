package com.novel.book.manager.feign;

import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.RestResp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 2. Fallback 独立出去
@Component
public class ListBookFeignFallback implements ListBookFeign {
    @Override
    public RestResp<List<BookInfoRespDto>> listBookInfoById(List<Long> bookIds) {
        return RestResp.ok(new ArrayList<>());
    }
}
