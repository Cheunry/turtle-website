package com.novel.author.manager.feign;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.author.dao.entity.AuthorInfo;
import com.novel.author.dao.mapper.AuthorInfoMapper;
import com.novel.author.dto.AuthorInfoDto;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.manager.feign.BookFeign;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@AllArgsConstructor
public class BookFeignManager {

    private final BookFeign bookFeign;
    private final AuthorInfoMapper authorInfoMapper;

    /**
     * 作家发布书籍
     * @param dto 新增书籍响应体
     * @return void
     */
    public RestResp<Void> publishBook(BookAddReqDto dto) {

        AuthorInfoDto author = getAuthorInfoByUserId(UserHolder.getUserId());

        dto.setAuthorId(author.getId());
        dto.setPenName(author.getPenName());

        return bookFeign.publishBook(dto);
    }

    /**
     * 作家章节发布接口
     * @param dto 新增章节响应体
     * @return void
     */
    public RestResp<Void> publishBookChapter(ChapterAddReqDto dto) {

        return bookFeign.publishBookChapter(dto);
    }

    /**
     * 作家书籍列表
     * @param dto 分页dto
     * @return 书籍列表
     */
    public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto) {

        getAuthorInfoByUserId(UserHolder.getUserId());

        return bookFeign.listPublishBooks(dto);
    }

    /**
     * 作家章节列表
     */
    public RestResp<PageRespDto<BookChapterRespDto>> listPublishBookChapters(ChapterPageReqDto dto) {

        return bookFeign.listPublishBookChapters(dto);
    }

    /**
     * 获取单个章节信息
     */
    public RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum) {
        return bookFeign.getBookChapter(bookId, chapterNum);
    }

    /**
     * 更新某章节信息
     */
    public RestResp<Void> updateBookChapter(ChapterUptReqDto dto) {

        dto.setAuthorId(UserHolder.getAuthorId());
        return bookFeign.updateBookChapter(dto);
    }

    /**
     * 删除某章节
     */
    public RestResp<Void> deleteBookChapter(ChapterDelReqDto dto) {
        // 确保 AuthorId 被正确设置，防止越权删除
        dto.setAuthorId(UserHolder.getAuthorId()); 
        return bookFeign.deleteBookChapter(dto);
    }

    /**
     * 更新书籍信息
     */
    public RestResp<Void> updateBook(BookUptReqDto dto) {
        return bookFeign.updateBook(dto);
    }

    /**
     * 查询作家信息
     */
    public AuthorInfoDto getAuthorInfoByUserId(Long userId) {
        QueryWrapper<AuthorInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        AuthorInfo authorInfo = authorInfoMapper.selectOne(queryWrapper);
        if (Objects.isNull(authorInfo)) {
            return null;
        }
        return AuthorInfoDto.builder()
                .id(authorInfo.getId())
                .penName(authorInfo.getPenName())
                .status(authorInfo.getStatus()).build();
    }



}
