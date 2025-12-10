package com.novel.search.manager.feign;

import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.manager.feign.BookFeign;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class BookFeignManager {

    private final BookFeign bookFeign;

    /**
     * 以安全的方式从远程服务获取下一批需要同步到 ES 的书籍数据。
     * 将底层的 Feign 调用和错误码判断封装起来，只暴露成功的数据，并确保在失败时返回一个安全的空列表，提高了代码的健壮性
     * @param maxBookId 最大书籍ID
     * @return 下一批需要同步到 ES 的书籍数据
     */
    public List<BookEsRespDto> listEsBooks(Long maxBookId) {

        RestResp<List<BookEsRespDto>> listRestResp = bookFeign.listNextEsBooks(maxBookId);

        if(Objects.equals(ErrorCodeEnum.OK.getCode(), listRestResp.getCode())) {
            return listRestResp.getData();
        }
        //  打印异常原因
        System.out.println(">>> Feign 调用失败或返回非 OK 状态: " + listRestResp.getMessage());
        return new ArrayList<>(0);
    }

    /**
     * 根据 ID 获取 ES 书籍数据
     */
    public BookEsRespDto getEsBookById(Long bookId) {
        RestResp<BookEsRespDto> resp = bookFeign.getEsBookById(bookId);
        if (Objects.equals(ErrorCodeEnum.OK.getCode(), resp.getCode())) {
            return resp.getData();
        }
        return null;
    }

}
