package com.novel.book.feign;

import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BookFeign 降级工厂
 */
@Component
@Slf4j
public class BookFeignFallbackFactory implements FallbackFactory<BookFeign> {

    @Override
    public BookFeign create(Throwable cause) {
        return new BookFeign() {
            @Override
            public RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds) {
                log.error("调用 listBookInfoByIds 异常", cause);
                return RestResp.ok(new ArrayList<>(0));
            }

            @Override
            public RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds) {
                log.error("调用 listBookInfoByIdsForBookshelf 异常，触发降级逻辑", cause);
                // 构造一个模拟的书籍对象，提示用户
                List<BookInfoRespDto> fallbackList = new ArrayList<>();
                if (bookIds != null && !bookIds.isEmpty()) {
                    for (Long bookId : bookIds) {
                        BookInfoRespDto dto = BookInfoRespDto.builder()
                                .id(bookId)
                                .bookName("系统维护中...")
                                .authorName("系统管理员")
                                .bookDesc("书架服务暂时不可用，请稍后再试。")
                                .picUrl("") // 可以放一个默认的维护图片URL
                                .build();
                        fallbackList.add(dto);
                    }
                }
                return RestResp.ok(fallbackList);
            }

            @Override
            public RestResp<Void> publishBook(BookAddReqDto dto) {
                log.error("调用 publishBook 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<Void> updateBook(BookUptReqDto dto) {
                log.error("调用 updateBook 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<Void> deleteBook(BookDelReqDto dto) {
                log.error("调用 deleteBook 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<Void> publishBookChapter(ChapterAddReqDto dto) {
                log.error("调用 publishBookChapter 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto) {
                log.error("调用 listPublishBooks 异常", cause);
                return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(), 0, new ArrayList<>(0)));
            }

            @Override
            public RestResp<PageRespDto<BookChapterRespDto>> listPublishBookChapters(ChapterPageReqDto dto) {
                log.error("调用 listPublishBookChapters 异常", cause);
                return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(), 0, new ArrayList<>(0)));
            }

            @Override
            public RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum) {
                log.error("调用 getBookChapter 异常", cause);
                return null;
            }

            @Override
            public RestResp<Void> updateBookChapter(ChapterUptReqDto dto) {
                log.error("调用 updateBookChapter 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<Void> deleteBookChapter(ChapterDelReqDto dto) {
                log.error("调用 deleteBookChapter 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<BookInfoRespDto> getBookByIdForAuthor(Long bookId, Long authorId) {
                log.error("调用 getBookByIdForAuthor 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<List<BookEsRespDto>> listNextEsBooks(Long maxBookId) {
                log.error("调用 listNextEsBooks 异常", cause);
                return RestResp.ok(new ArrayList<>(0));
            }

            @Override
            public RestResp<BookEsRespDto> getEsBookById(Long bookId) {
                log.error("调用 getEsBookById 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<Void> publishComment(BookCommentReqDto dto) {
                log.error("调用 publishComment 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<Void> updateComment(BookCommentReqDto dto) {
                log.error("调用 updateComment 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }

            @Override
            public RestResp<Void> deleteComment(BookCommentReqDto dto) {
                log.error("调用 deleteComment 异常", cause);
                return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
            }
        };
    }
}

