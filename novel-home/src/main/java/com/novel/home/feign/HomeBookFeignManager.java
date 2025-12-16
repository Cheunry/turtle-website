package com.novel.home.feign;

import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.feign.BookFeign;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 小说微服务调用 Fegin 客户端管理
 */
@Component
@AllArgsConstructor
public class HomeBookFeignManager {
    private final BookFeign bookFeign;

    public List<BookInfoRespDto> listBookInfoById (List<Long> bookIds) {
        RestResp<List<BookInfoRespDto>> resp = bookFeign.listBookInfoByIds(bookIds);
        if(Objects.equals(ErrorCodeEnum.OK.getCode(), resp.getCode())) {
            return resp.getData();
        }
        return new ArrayList<>(0);

    }

}
