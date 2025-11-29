package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.req.*;
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

        // 更新书籍字数和最新章节信息
        updateBookInfo(chapter.getBookId(), chapter, true, 0);

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



    /**
     * 删除章节
     */
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> deleteBookChapter(ChapterDelReqDto dto) {

        // 校验该作品是否属于当前作家
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_ID, dto.getChapterId());
        BookChapter bookChapter = bookChapterMapper.selectOne(queryWrapper);
        int count = bookChapter.getWordCount() == null ? 0 : bookChapter.getWordCount();

        BookInfo book = bookInfoMapper.selectById(dto.getBookId());

        if (Objects.nonNull(book)) {

            book.setWordCount(book.getWordCount() - count);

            // 如果删除的章节是该本小说的最新章节
            if (book.getWordCount() > 0 && book.getLastChapterId().equals(bookChapter.getId())) {
                QueryWrapper<BookChapter> bookChapterQueryWrapper = new QueryWrapper<>();
                bookChapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, book.getId())
                        .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_UPDATE_TIME)
                        .last("limit 1");
                BookChapter bookChapter1 = bookChapterMapper.selectOne(bookChapterQueryWrapper);
                book.setLastChapterId(bookChapter1.getId());
                book.setLastChapterName(bookChapter1.getChapterName());
                book.setLastChapterUpdateTime(LocalDateTime.now());
            } else if (book.getWordCount() <= 0 && book.getLastChapterId().equals(bookChapter.getId())) {
                book.setWordCount(0);
                book.setLastChapterId(null);
                book.setLastChapterName(null);
                book.setLastChapterUpdateTime(null);
            }
            bookInfoMapper.updateById(book);
        }

        bookChapterMapper.deleteById(dto.getChapterId());

        return RestResp.ok();
    }

    /**
     * 获取章节内容
     * @param id 章节ID
     * @return 章节内容
     */
    @Override
    public RestResp<BookChapterRespDto> getBookChapter(Long id) {
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_ID, id);
        BookChapter bookChapter = bookChapterMapper.selectOne(queryWrapper);
        return RestResp.ok(BookChapterRespDto.builder()
                .id(bookChapter.getId())
                .bookId(bookChapter.getBookId())
                .chapterNum(bookChapter.getChapterNum())
                .chapterName(bookChapter.getChapterName())
                .content(bookChapter.getContent())
                .chapterWordCount(bookChapter.getWordCount())
                .chapterUpdateTime(bookChapter.getUpdateTime())
                .isVip(bookChapter.getIsVip())
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> updateBookChapter(ChapterUptReqDto dto) {

        // 校验该作品是否属于当前作家
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_ID, dto.getChapterId());
        BookChapter chapter = bookChapterMapper.selectOne(queryWrapper);
        
        // 记录旧字数
        int oldWordCount = chapter.getWordCount() == null ? 0 : chapter.getWordCount();
        
        chapter.setChapterName(dto.getChapterName());
        chapter.setContent(dto.getContent());
        chapter.setIsVip(dto.getIsVip());
        chapter.setUpdateTime(LocalDateTime.now());
        chapter.setWordCount(dto.getContent().length()); // 确保字数被更新
        bookChapterMapper.updateById(chapter);

        // 更新书籍字数和最新章节信息
        updateBookInfo(chapter.getBookId(), chapter, false, oldWordCount);

        return RestResp.ok();
    }

    /**
     * 更新书籍信息辅助函数
     * @param bookId 书籍ID
     * @param chapter 变更的章节
     * @param isNew 是否是新增章节
     * @param oldChapterWordCount 旧章节字数（仅更新时使用）
     */
    private void updateBookInfo(Long bookId, BookChapter chapter, boolean isNew, int oldChapterWordCount) {
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (bookInfo == null) return;

        BookInfo updateBook = new BookInfo();
        updateBook.setId(bookId);

        // 计算新总字数
        int currentTotal = bookInfo.getWordCount() == null ? 0 : bookInfo.getWordCount();
        int newChapterWordCount = chapter.getWordCount() == null ? 0 : chapter.getWordCount();
        
        if (isNew) {
            updateBook.setWordCount(currentTotal + newChapterWordCount);
        } else {
            updateBook.setWordCount(currentTotal - oldChapterWordCount + newChapterWordCount);
        }

        // 更新最新章节信息
        updateBook.setLastChapterId(chapter.getId());
        updateBook.setLastChapterName(chapter.getChapterName());
        updateBook.setLastChapterUpdateTime(LocalDateTime.now());
        updateBook.setUpdateTime(LocalDateTime.now());

        bookInfoMapper.updateById(updateBook);
    }
}
