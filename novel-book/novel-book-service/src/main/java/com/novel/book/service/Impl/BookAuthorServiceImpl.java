package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.req.BookAddReqDto;
import com.novel.book.dto.req.BookPageReqDto;
import com.novel.book.dto.req.ChapterAddReqDto;
import com.novel.book.dto.req.ChapterPageReqDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.service.BookAuthorService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookAuthorServiceImpl implements BookAuthorService {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;


    /**
     * 书籍保存
     * @param dto 新增书籍请求dto
     * @return 响应
     */
    @Override
    public RestResp<Void> saveBook(BookAddReqDto dto) {

        // 校验小说名是否已存在
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookTable.COLUMN_BOOK_NAME, dto.getBookName());
        if (bookInfoMapper.selectCount(queryWrapper) > 0) {
            return RestResp.fail(ErrorCodeEnum.AUTHOR_BOOK_NAME_EXIST);
        }

        BookInfo bookInfo = BookInfo.builder()
                .workDirection(dto.getWorkDirection())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .picUrl(dto.getPicUrl())
                .bookName(dto.getBookName())
                .authorId(dto.getAuthorId())
                .authorName(dto.getPenName())
                .bookDesc(dto.getBookDesc())
                .score(0)
                .isVip(dto.getIsVip())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 保存小说信息
        bookInfoMapper.insert(bookInfo);

        return RestResp.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> saveBookChapter(ChapterAddReqDto dto) {

        // 校验该作品是否属于当前作家
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        int chapterNum = 1;
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookChapter bookChapter = bookChapterMapper.selectOne(chapterQueryWrapper);
        if (Objects.nonNull(bookChapter)) {
            chapterNum = bookChapter.getChapterNum() + 1;
        }

        BookChapter chapter = BookChapter.builder()
                .bookId(dto.getBookId())
                .chapterNum(chapterNum)
                .chapterName(dto.getChapterName())
                .content(dto.getContent())
                .wordCount(dto.getContent().length())
                .isVip(dto.getIsVip())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        bookChapterMapper.insert(chapter);

        BookInfo book = new BookInfo();
        book.setId(chapter.getBookId());
        // 使用 bookInfo 获取当前字数，若为 null 则默认为 0
        Integer currentWordCount = bookInfo.getWordCount() == null ? 0 : bookInfo.getWordCount();
        book.setWordCount(currentWordCount + chapter.getWordCount());
        
        book.setLastChapterId(chapter.getId());
        book.setLastChapterName(chapter.getChapterName());
        book.setLastChapterUpdateTime(LocalDateTime.now());
        book.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(book);

        return RestResp.ok();

    }

    /**
     * 作家小说列表查看
     * @param dto 作家小说列表分页请求
     * @return 分页数据体，其中的每一项都是一个书本信息的响应 DTO
     */
    @Override
    public RestResp<PageRespDto<BookInfoRespDto>> listAuthorBooks(BookPageReqDto dto) {

        IPage<BookInfo> page = new Page<>();
        page.setCurrent(dto.getPageNum());
        page.setSize(dto.getPageSize());

        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookTable.AUTHOR_ID, dto.getAuthorId())
                .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());

        IPage<BookInfo> bookInfoPage = bookInfoMapper.selectPage(page, queryWrapper);

        return RestResp.ok(
                PageRespDto.of(
                    dto.getPageNum(),
                    dto.getPageSize(),
                    page.getTotal(),
                    bookInfoPage.getRecords().stream().map(v -> BookInfoRespDto.builder()
                        .id(v.getId())
                        .bookName(v.getBookName())
                        .picUrl(v.getPicUrl())
                        .categoryName(v.getCategoryName())
                        .wordCount(v.getWordCount())
                        .visitCount(v.getVisitCount())
                        .updateTime(v.getUpdateTime())
                        .build()).toList()
                )
        );
    }

    /**
     * 作家章节列表查看
     * @param dto 作家章节列表分页请求
     * @return 分页数据体，其中的每一项都是一个章节信息的响应 DTO
     */
    @Override
    public RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(ChapterPageReqDto dto) {

        IPage<BookChapter> page = new Page<>();
        page.setCurrent(dto.getPageNum());
        page.setSize(dto.getPageSize());

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM);

        IPage<BookChapter> bookChapterPage = bookChapterMapper.selectPage(page, queryWrapper);

        return RestResp.ok(
                PageRespDto.of(dto.getPageNum(), dto.getPageSize(), page.getTotal(),
                bookChapterPage.getRecords().stream().map(v -> BookChapterRespDto.builder()
                        .id(v.getId())
                        .chapterName(v.getChapterName())
                        .chapterUpdateTime(v.getUpdateTime())
                        .isVip(v.getIsVip())
                        .build()).toList()));
    }


}
