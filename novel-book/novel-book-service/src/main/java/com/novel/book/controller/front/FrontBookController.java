package com.novel.book.controller.front;


import com.novel.book.dto.resp.*;
import com.novel.book.dto.req.BookVisitReqDto;
import com.novel.book.service.BookReadService;
import com.novel.book.service.BookSearchService;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.novel.book.service.AuditExperienceExtractService;
import com.novel.book.dto.req.BookCommentPageReqDto;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.servlet.http.HttpServletRequest;


import com.novel.book.job.BookRankCacheJob;

@Tag(name = "FrontBookController", description = "前台门户-小说模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_BOOK_URL_PREFIX)
@RequiredArgsConstructor
@Slf4j
public class FrontBookController {

    private final BookSearchService bookSearchService;
    private final BookReadService bookReadService;
    private final BookRankCacheJob bookRankCacheJob;
    private final AuditExperienceExtractService auditExperienceExtractService;

    /**
     * 手动触发小说点击榜缓存更新（测试用）
     */
    @Operation(summary = "手动触发小说点击榜缓存更新")
    @GetMapping("visit_rank/refresh")
    public RestResp<Void> refreshVisitRankCache() {
        bookRankCacheJob.refreshVisitRankCache();
        return RestResp.ok();
    }

    /**
     * 临时手动触发全量提炼审核经验标签（针对历史数据）
     * http://localhost:8888/api/front/book/sync/extractAuditExperience
     */
    @Operation(summary = "临时手动触发全量提炼审核经验标签")
    @GetMapping("sync/extractAuditExperience")
    public RestResp<String> extractAuditExperience() {
        log.info(">>> 收到全量提炼审核经验标签请求，开始异步执行任务");
        CompletableFuture.runAsync(() -> {
            try {
                auditExperienceExtractService.extractAllMissingAuditExperience();
            } catch (Exception e) {
                log.error(">>> 异步任务执行全量提炼审核经验标签时发生异常", e);
            }
        });
        return RestResp.ok("全量提炼审核经验标签任务已触发，正在后台执行，请观察后台日志");
    }

    /**
     * 小说分类列表查询接口
     */
    @Operation(summary = "小说分类列表查询接口")
    @GetMapping("category/list")
    public RestResp<List<BookCategoryRespDto>> listCategory(
            @Parameter(description = "作品方向，不传则返回所有分类") Integer workDirection) {
        return bookSearchService.listCategory(workDirection);
    }

    /**
     * 小说点击榜（排行榜页表格）
     */
    @Operation(summary = "小说点击榜（排行榜页表格）")
    @GetMapping("visit_rank")
    public RestResp<List<BookRankTableRespDto>> listVisitRankBooks() {
        return bookSearchService.listVisitRankBooks();
    }

    /**
     * 小说点击榜（首页侧栏，仅第一名含封面与简介预览）
     */
    @Operation(summary = "小说点击榜（首页侧栏）")
    @GetMapping("visit_rank/home")
    public RestResp<List<BookRankHomeItemRespDto>> listVisitRankBooksHome() {
        return bookSearchService.listVisitRankBooksHome();
    }

    /**
     * 小说新书榜（排行榜页表格）
     */
    @Operation(summary = "小说新书榜（排行榜页表格）")
    @GetMapping("newest_rank")
    public RestResp<List<BookRankTableRespDto>> listNewestRankBooks() {
        return bookSearchService.listNewestRankBooks();
    }

    /**
     * 小说新书榜（首页侧栏）
     */
    @Operation(summary = "小说新书榜（首页侧栏）")
    @GetMapping("newest_rank/home")
    public RestResp<List<BookRankHomeItemRespDto>> listNewestRankBooksHome() {
        return bookSearchService.listNewestRankBooksHome();
    }

    /**
     * 小说更新榜（排行榜页表格）
     */
    @Operation(summary = "小说更新榜（排行榜页表格）")
    @GetMapping("update_rank")
    public RestResp<List<BookRankTableRespDto>> listUpdateRankBooks() {
        return bookSearchService.listUpdateRankBooks();
    }

    /**
     * 小说更新榜（首页侧栏）
     */
    @Operation(summary = "小说更新榜（首页侧栏）")
    @GetMapping("update_rank/home")
    public RestResp<List<BookRankHomeItemRespDto>> listUpdateRankBooksHome() {
        return bookSearchService.listUpdateRankBooksHome();
    }

    /**
     * 首页「最新更新」表格
     */
    @Operation(summary = "首页最新更新列表")
    @GetMapping("home/latest_updates")
    public RestResp<List<BookHomeLatestUpdateRespDto>> listHomeLatestUpdates() {
        return bookSearchService.listHomeLatestUpdates();
    }

    /**
     * 小说推荐列表查询接口
     */
    @Operation(summary = "小说推荐列表查询接口")
    @GetMapping("rec_list")
    public RestResp<List<BookInfoRespDto>> listRecBooks(
            @Parameter(description = "小说ID") @RequestParam("bookId") Long bookId) {
        return bookSearchService.listRecBooks(bookId);
    }

    /**
     * 增加小说点击量接口
     */
    @Operation(summary = "增加小说点击量接口")
    @PostMapping("visit")
    public RestResp<Void> addVisitCount(@Parameter(description = "小说ID") @RequestBody BookVisitReqDto dto,
                                        HttpServletRequest request) {
        Long userId = UserHolder.getUserId();
        String userIdentity = userId != null ? "u:" + userId : "ip:" + resolveClientIp(request);
        return bookSearchService.addVisitCount(dto.getBookId(), userIdentity);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? "unknown" : remoteAddr;
    }

    /**
     * 小说章节目录查询接口
     */
    @Operation(summary = "小说章节目录查询接口")
    @GetMapping("chapter/list")
    public RestResp<List<BookChapterRespDto>> getChapterList(
            @Parameter(description = "小说ID") @RequestParam("bookId") Long bookId) {
        // 参数验证：如果 bookId 为 null，返回空列表
        if (bookId == null) {
            return RestResp.ok(List.of());
        }
        return bookReadService.getBookChapter(bookId);
    }

    /**
     * 小说内容相关信息查询接口
     */
    @Operation(summary = "小说内容相关信息查询接口")
    @GetMapping("content/{bookId}/{chapterNum}")
    public RestResp<BookContentAboutRespDto> getBookContentAbout(
            @Parameter(description = "书籍ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        // 参数验证：如果 bookId 或 chapterNum 为 null，返回空内容
        if (bookId == null || chapterNum == null) {
            return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(BookContentAboutRespDto.BookInfo.builder()
                    .categoryName(null)
                    .authorName(null)
                    .build())
                .chapterInfo(BookContentAboutRespDto.ChapterInfo.builder()
                    .bookId(null)
                    .chapterNum(null)
                    .chapterName(null)
                    .chapterWordCount(null)
                    .chapterUpdateTime(null)
                    .build())
                .bookContent(null)
                .build());
        }
        return bookReadService.getBookContentAbout(bookId, chapterNum);
    }

    /**
     * 小说最新章节相关信息查询接口
     */
    @Operation(summary = "小说最新章节相关信息查询接口")
    @GetMapping("last_chapter/about")
    public RestResp<BookChapterAboutRespDto> getLastChapterAbout(
            @Parameter(description = "小说ID") Long bookId) {
        return bookSearchService.getLastChapterAbout(bookId);
    }

    /**
     * 获取上一章节ID接口
     */
    @Operation(summary = "获取上一章节ID接口")
    @GetMapping("pre_chapter_id/{bookId}/{chapterNum}")
    public RestResp<Integer> getPreChapterId(
            @Parameter(description = "书籍ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        // 参数验证：如果 bookId 或 chapterNum 为 null，返回 null
        if (bookId == null || chapterNum == null) {
            return RestResp.ok(null);
        }
        return bookReadService.getPreChapterNum(bookId, chapterNum);
    }

    /**
     * 获取下一章节ID接口
     */
    @Operation(summary = "获取下一章节ID接口")
    @GetMapping("next_chapter_id/{bookId}/{chapterNum}")
    public RestResp<Integer> getNextChapterId(
            @Parameter(description = "书籍ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        // 参数验证：如果 bookId 或 chapterNum 为 null，返回 null
        if (bookId == null || chapterNum == null) {
            return RestResp.ok(null);
        }
        return bookReadService.getNextChapterNum(bookId, chapterNum);
    }


    /**
     * 小说评论分页查询接口
     */
    @Operation(summary = "小说评论分页查询接口")
    @GetMapping("comment/list_page")
    public RestResp<PageRespDto<BookCommentRespDto.CommentInfo>> listCommentByPage(
            @ParameterObject BookCommentPageReqDto dto) {
        return bookReadService.listCommentByPage(dto);
    }

    /**
     * 首页小说展示查询接口
     */
    @Operation(summary = "首页小说展示查询接口")
    @GetMapping("home/books")
    public RestResp<List<HomeBookRespDto>> listHomeBooks(){
        return bookSearchService.listHomeBook();
    }


    /**
     * 小说信息查询接口（放在最后，因为路径最宽泛）
     */
    @Operation(summary = "小说信息查询接口")
    @GetMapping("{id}")
    public RestResp<BookInfoRespDto> getBookById(
            @Parameter(description = "小说 ID") @PathVariable("id") Long bookId) {
        // 参数验证：如果 bookId 为 null，返回错误
        if (bookId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "书籍ID不能为空");
        }
        return bookSearchService.getBookById(bookId);
    }

}
