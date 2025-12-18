package com.novel.book.service.impl;

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
import com.novel.book.service.BookAuditService;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookAuthorServiceImpl implements BookAuthorService {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final BookAuditService bookAuditService;


    /**
     * 作家新增书籍
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
                .auditStatus(0) // 初始状态为待审核
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 保存小说信息
        bookInfoMapper.insert(bookInfo);

        // 立即触发AI审核（先写入审核表，然后进行AI审核）
        try {
            bookAuditService.auditBookInfo(bookInfo);
        } catch (Exception e) {
            log.error("AI审核失败，书籍ID: {}", bookInfo.getId(), e);
            // 审核失败不影响保存，保持待审核状态
        }

        return RestResp.ok();
    }

    /**
     * 作家修改书籍信息
     * @param dto 更新书籍请求dto
     * @return Void
     */
    @Override
    public RestResp<Void> updateBook(BookUptReqDto dto) {
        // 1. 校验小说是否存在且属于该作者
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (Objects.isNull(bookInfo)) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 2. 更新信息
        BookInfo updateBook = new BookInfo();
        updateBook.setId(dto.getBookId());
        
        boolean hasUpdate = false;
        boolean needAudit = false; // 标记是否需要重新审核
        
        if (StringUtils.isNotBlank(dto.getBookName())) {
            updateBook.setBookName(dto.getBookName());
            hasUpdate = true;
            needAudit = true; // 小说名变更需要重新审核
        }
        if (StringUtils.isNotBlank(dto.getPicUrl())) {
            updateBook.setPicUrl(dto.getPicUrl());
            hasUpdate = true;
        }
        if (StringUtils.isNotBlank(dto.getBookDesc())) {
            updateBook.setBookDesc(dto.getBookDesc());
            hasUpdate = true;
            needAudit = true; // 简介变更需要重新审核
        }
        if (dto.getCategoryId() != null) {
            updateBook.setCategoryId(dto.getCategoryId());
            hasUpdate = true;
        }
        if (StringUtils.isNotBlank(dto.getCategoryName())) {
            updateBook.setCategoryName(dto.getCategoryName());
            hasUpdate = true;
        }
        if (dto.getWorkDirection() != null) {
            updateBook.setWorkDirection(dto.getWorkDirection());
            hasUpdate = true;
        }
        if (dto.getIsVip() != null) {
            updateBook.setIsVip(dto.getIsVip());
            hasUpdate = true;
        }
        if (dto.getBookStatus() != null) {
            updateBook.setBookStatus(dto.getBookStatus());
            hasUpdate = true;
        }

        if (hasUpdate) {
            // 如果小说名或简介有变更，重置审核状态为待审核
            if (needAudit) {
                updateBook.setAuditStatus(0);
                updateBook.setAuditReason(null);
            }
            
            updateBook.setUpdateTime(LocalDateTime.now());
            bookInfoMapper.updateById(updateBook);
            
            // 如果小说名或简介有变更，触发AI审核
            if (needAudit) {
                // 重新查询完整的书籍信息（包含更新后的内容）
                BookInfo updatedBookInfo = bookInfoMapper.selectById(dto.getBookId());
                try {
                    bookAuditService.auditBookInfo(updatedBookInfo);
                } catch (Exception e) {
                    log.error("AI审核失败，书籍ID: {}", dto.getBookId(), e);
                    // 审核失败不影响更新，保持待审核状态
                }
            }
            
            // 移除 sendBookChangeMsg 调用，审核通过后再发送
        }

        return RestResp.ok();
    }


    /**
     * 作家新增书籍章节
     * @param dto 新增章节Dto
     * @return void
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public RestResp<Void> saveBookChapter(ChapterAddReqDto dto) {

        // 校验该作品是否属于当前作家
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (bookInfo == null) {
             return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 1. 校验章节号是否重复
        QueryWrapper<BookChapter> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                    .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getChapterNum());
        
        if (bookChapterMapper.selectCount(checkWrapper) > 0) {
            return RestResp.fail(ErrorCodeEnum.CHAPTER_NUM_EXIST);
        }

        // 2. 直接使用 DTO 里的 chapterNum
        BookChapter chapter = new BookChapter();
        chapter.setBookId(dto.getBookId());
        chapter.setChapterNum(dto.getChapterNum()); // 使用用户输入的章节号
        chapter.setChapterName(dto.getChapterName());
        chapter.setContent(dto.getContent());
        chapter.setWordCount(dto.getContent().length());
        chapter.setIsVip(dto.getIsVip());
        chapter.setAuditStatus(0); // 初始状态为待审核
        chapter.setCreateTime(LocalDateTime.now());
        chapter.setUpdateTime(LocalDateTime.now());

        bookChapterMapper.insert(chapter);

        // 更新书籍字数和最新章节信息
        updateBookInfo(chapter.getBookId(), chapter, true, 0);

        // 立即触发AI审核（先写入审核表，然后进行AI审核）
        try {
            bookAuditService.auditChapter(chapter);
        } catch (Exception e) {
            log.error("AI审核失败，章节ID: {}", chapter.getId(), e);
            // 审核失败不影响保存，保持待审核状态
        }

        // 移除 sendBookChangeMsg 调用，审核通过后再发送

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
                        .authorId(v.getAuthorId())
                        .bookName(v.getBookName())
                        .picUrl(v.getPicUrl())
                        .categoryName(v.getCategoryName())
                        .wordCount(v.getWordCount())
                        .visitCount(v.getVisitCount())
                        .updateTime(v.getUpdateTime())
                        .auditStatus(v.getAuditStatus() != null ? v.getAuditStatus() : 0)
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

        // 校验该作品是否属于当前作家
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (bookInfo == null) {
            return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(), 0, null));
        }
        // 如果传递了 authorId，则进行校验
        if (dto.getAuthorId() != null && !Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

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
                        .bookId(v.getBookId())
                        .chapterWordCount(v.getWordCount())
                        .chapterName(v.getChapterName())
                        .chapterNum(v.getChapterNum())
                        .chapterUpdateTime(v.getUpdateTime())
                        .isVip(v.getIsVip())
                        .auditStatus(v.getAuditStatus() != null ? v.getAuditStatus() : 0)
                        .build()).toList()));
    }


    /**
     * 作家删除章节
     * @param dto 删除请求
     * @return void
     */
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> deleteBookChapter(ChapterDelReqDto dto) {

        // 校验该作品是否属于当前作家
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getChapterNum());
        BookChapter bookChapter = bookChapterMapper.selectOne(queryWrapper);
        int count = bookChapter.getWordCount() == null ? 0 : bookChapter.getWordCount();

        BookInfo book = bookInfoMapper.selectById(dto.getBookId());

        if (Objects.nonNull(book)) {

            book.setWordCount(book.getWordCount() - count);

            // 如果删除的章节是该本小说的最新章节，需要重新查找最新的审核通过的章节
            if (book.getWordCount() > 0 && book.getLastChapterNum().equals(bookChapter.getChapterNum())) {
                QueryWrapper<BookChapter> bookChapterQueryWrapper = new QueryWrapper<>();
                bookChapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, book.getId())
                        .eq("audit_status", 1) // 只查询审核通过的章节
                        .ne(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getChapterNum()) // 明确排除正在删除的章节
                        .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_UPDATE_TIME)
                        .last("limit 1");
                BookChapter bookChapter1 = bookChapterMapper.selectOne(bookChapterQueryWrapper);
                if (bookChapter1 != null) {
                    book.setLastChapterNum(bookChapter1.getChapterNum());
                    book.setLastChapterName(bookChapter1.getChapterName());
                    book.setLastChapterUpdateTime(bookChapter1.getUpdateTime());
                } else {
                    // 如果没有审核通过的章节了，清空最新章节信息
                    book.setLastChapterNum(null);
                    book.setLastChapterName(null);
                    book.setLastChapterUpdateTime(null);
                }
            } else if (book.getWordCount() <= 0 && book.getLastChapterNum().equals(bookChapter.getChapterNum())) {
                book.setWordCount(0);
                book.setLastChapterNum(null);
                book.setLastChapterName(null);
                book.setLastChapterUpdateTime(null);
            }
            bookInfoMapper.updateById(book);
        }

        bookChapterMapper.delete(queryWrapper);

        // 发送 MQ 消息
        sendBookChangeMsg(dto.getBookId());

        return RestResp.ok();
    }

    /**
     * 作家获取章节内容
     * @param bookId,chapterNum 书籍id，章节号
     * @return 章节内容
     */
    @Override
    public RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum) {

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum);
        BookChapter bookChapter = bookChapterMapper.selectOne(queryWrapper);

        return RestResp.ok(BookChapterRespDto.builder()
                .bookId(bookChapter.getBookId())
                .chapterNum(bookChapter.getChapterNum())
                .chapterName(bookChapter.getChapterName())
                .content(bookChapter.getContent())
                .chapterWordCount(bookChapter.getWordCount())
                .chapterUpdateTime(bookChapter.getUpdateTime())
                .isVip(bookChapter.getIsVip())
                .build());
    }

    /**
     * 作家更新书籍章节
     * @param dto 更新dto
     * @return void
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> updateBookChapter(ChapterUptReqDto dto) {

        // 校验该作品是否属于当前作家
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                    .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getOldChapterNum());
        BookChapter chapter = bookChapterMapper.selectOne(queryWrapper);

        if (chapter == null) {
            // 如果没找到章节，可能是参数不对
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR); // 或者具体的业务错误
        }
        
        // 记录旧字数
        int oldWordCount = chapter.getWordCount() == null ? 0 : chapter.getWordCount();

        // 校验章节号是否变更且已存在
        if (!Objects.equals(chapter.getChapterNum(), dto.getChapterNum())) {
            QueryWrapper<BookChapter> checkWrapper = new QueryWrapper<>();
            checkWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                    .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getChapterNum());
            if (bookChapterMapper.selectCount(checkWrapper) > 0) {
                return RestResp.fail(ErrorCodeEnum.CHAPTER_NUM_EXIST);
            }
        }
        
        chapter.setChapterNum(dto.getChapterNum());
        chapter.setChapterName(dto.getChapterName());
        chapter.setContent(dto.getContent());
        chapter.setIsVip(dto.getIsVip());
        chapter.setUpdateTime(LocalDateTime.now());
        chapter.setWordCount(dto.getContent().length()); // 确保字数被更新
        
        // 如果章节内容有变更，重置审核状态为待审核
        chapter.setAuditStatus(0);
        chapter.setAuditReason(null);

        bookChapterMapper.update(chapter, queryWrapper);

        // 更新书籍字数和最新章节信息
        updateBookInfo(chapter.getBookId(), chapter, false, oldWordCount);

        // 如果章节内容有变更，触发AI审核
        // 重新查询完整的章节信息（包含更新后的内容）
        BookChapter updatedChapter = bookChapterMapper.selectById(chapter.getId());
        try {
            bookAuditService.auditChapter(updatedChapter);
        } catch (Exception e) {
            log.error("AI审核失败，章节ID: {}", chapter.getId(), e);
            // 审核失败不影响更新，保持待审核状态
        }

        // 移除 sendBookChangeMsg 调用，审核通过后再发送（如果需要的话）

        return RestResp.ok();
    }

    /**
     * 作者删除书籍
     * @param dto 删除请求
     * @return Void
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> deleteBook(BookDelReqDto dto) {
        // 1. 校验小说是否存在且属于该作者
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (Objects.isNull(bookInfo)) {
            return RestResp.ok();
        }
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 2. 删除章节
        QueryWrapper<BookChapter> chapterWrapper = new QueryWrapper<>();
        chapterWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId());
        bookChapterMapper.delete(chapterWrapper);

        // 3. 删除书籍
        bookInfoMapper.deleteById(dto.getBookId());

        // 4. 发送 MQ 消息 (通知搜索引擎等更新)
        sendBookChangeMsg(dto.getBookId());

        return RestResp.ok();
    }

    /**
     * 更新书籍信息辅助函数
     *
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

        // 查询当前该书真正的最新章节（只查询审核通过的章节，auditStatus=1）
        // 因为用户可能插入的是中间章节，或者修改了旧章节，不能简单认为当前操作的章节就是最新的
        QueryWrapper<BookChapter> lastChapterQuery = new QueryWrapper<>();
        lastChapterQuery.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last("limit 1");
        BookChapter realLastChapter = bookChapterMapper.selectOne(lastChapterQuery);

        if (realLastChapter != null) {
            updateBook.setLastChapterNum(realLastChapter.getChapterNum());
            updateBook.setLastChapterName(realLastChapter.getChapterName());
            updateBook.setLastChapterUpdateTime(realLastChapter.getUpdateTime());
        }

        updateBook.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(updateBook);
    }

    /**
     * 发送书籍变更消息
     */
    private void sendBookChangeMsg(Long bookId) {
        if (bookId == null) {
            return;
        }
        // 构建 Destination: Topic:Tag
        String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;
        // 发送消息，消息体就是 bookId
        rocketMQTemplate.convertAndSend(destination, bookId);
    }
}
