package com.novel.book.feign;

import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@Component
@FeignClient(value = "novel-book-service", fallback = BookFeign.BookFeignFallback.class)
public interface BookFeign {

    /**
     * 批量查询小说信息
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listBookInfoByIds")
    RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds);

    /**
     * 批量查询小说信息（用于书架，不过滤审核状态）
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listBookInfoByIdsForBookshelf")
    RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds);

    /**
     * 小说发布接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/publishBook")
    RestResp<Void> publishBook(BookAddReqDto dto);

    /**
     * 更新小说接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/updateBook")
    RestResp<Void> updateBook(BookUptReqDto dto);

    /**
     * 删除小说接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/deleteBook")
    RestResp<Void> deleteBook(BookDelReqDto dto);

    /**
     * 小说章节发布接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/publishBookChapter")
    RestResp<Void> publishBookChapter(ChapterAddReqDto dto);

    /**
     * 小说发布列表查询接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listPublishBooks")
    RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto);

    /**
     * 小说章节列表查询
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listPublishBookChapters")
    RestResp<PageRespDto<BookChapterRespDto>> listPublishBookChapters(ChapterPageReqDto dto);

    /**
     * 获取单个章节信息
     */
    @GetMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/getBookChapter")
    RestResp<BookChapterRespDto> getBookChapter(@RequestParam("bookId") Long bookId, @RequestParam("chapterNum") Integer chapterNum);

    /**
     * 更新某章节信息
     */
    @PutMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/updateBookChapter")
    RestResp<Void> updateBookChapter(ChapterUptReqDto dto);

    /**
     * 删除某章节
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/deleteBookChapter")
    RestResp<Void> deleteBookChapter(@RequestBody ChapterDelReqDto dto);

    /**
     * 查询下一批保存到 ES 中的小说列表
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listNextEsBooks")
    RestResp<List<BookEsRespDto>> listNextEsBooks(Long maxBookId);

    /**
     * 根据 ID 获取 ES 书籍数据
     */
    @GetMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/getEsBookById")
    RestResp<BookEsRespDto> getEsBookById(@RequestParam("bookId") Long bookId);


    /**
     * 发表评论
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/publishComment")
    RestResp<Void> publishComment(BookCommentReqDto dto);

    /**
     * 修改评论
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/updateComment")
    RestResp<Void> updateComment(BookCommentReqDto dto);

    /**
     * 删除评论接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/deleteComment")
    RestResp<Void> deleteComment(@RequestBody BookCommentReqDto dto);


    @Component
    class BookFeignFallback implements BookFeign {

        @Override
        public RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds) {

            return RestResp.ok(new ArrayList<>(0));
        }

        @Override
        public RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds) {
            return RestResp.ok(new ArrayList<>(0));
        }

        @Override
        public RestResp<Void> publishBook(BookAddReqDto dto) {

            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<Void> updateBook(BookUptReqDto dto) {
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<Void> deleteBook(BookDelReqDto dto) {
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<Void> publishBookChapter(ChapterAddReqDto dto) {

            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto) {

            return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(), 0, new ArrayList<>(0)));
        }

        @Override
        public RestResp<PageRespDto<BookChapterRespDto>> listPublishBookChapters(ChapterPageReqDto dto) {
            return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(), 0, new ArrayList<>(0)));
        }

        @Override
        public RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum) {
            return null;
        }

        @Override
        public RestResp<Void> updateBookChapter(ChapterUptReqDto dto) {
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<Void> deleteBookChapter(ChapterDelReqDto dto) {
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<List<BookEsRespDto>> listNextEsBooks(Long maxBookId) {
            return RestResp.ok(new ArrayList<>(0));
        }

        @Override
        public RestResp<BookEsRespDto> getEsBookById(Long bookId) {
            // 现在的 fail 方法会自动推断 T 为 BookEsRespDto
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<Void> publishComment(BookCommentReqDto dto) {
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<Void> updateComment(BookCommentReqDto dto) {
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<Void> deleteComment(BookCommentReqDto dto) {
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

    }


}
