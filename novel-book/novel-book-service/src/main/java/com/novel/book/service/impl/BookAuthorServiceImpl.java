package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.mq.BookSubmitMqDto;
import com.novel.book.dto.mq.ChapterSubmitMqDto;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.service.BookAuthorService;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class BookAuthorServiceImpl implements BookAuthorService {

    @Value("${novel.audit.enable:true}")
    private Boolean auditEnable;

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;


    /**
     * 作家新增书籍
     * @param dto 新增书籍请求dto
     * @return 响应
     */
    @Override
    public RestResp<Void> saveBook(BookAddReqDto dto) {
        // 校验小说名是否已存在 (同步校验，提升体验)
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookTable.COLUMN_BOOK_NAME, dto.getBookName());
        if (bookInfoMapper.selectCount(queryWrapper) > 0) {
            return RestResp.fail(ErrorCodeEnum.AUTHOR_BOOK_NAME_EXIST);
        }

        BookSubmitMqDto submitDto = BookSubmitMqDto.builder()
                .operationType("ADD")
                .authorId(dto.getAuthorId())
                .penName(dto.getPenName())
                .workDirection(dto.getWorkDirection())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .picUrl(dto.getPicUrl())
                .bookName(dto.getBookName())
                .bookDesc(dto.getBookDesc())
                .isVip(dto.getIsVip())
                .bookStatus(dto.getBookStatus())
                .auditEnable(auditEnable)
                .build();

        // 发送MQ消息，立即返回
        try {
            // Updated for async submit
            rocketMQTemplate.convertAndSend(AmqpConsts.BookSubmitMq.TOPIC + ":" + AmqpConsts.BookSubmitMq.TAG_SUBMIT, submitDto);
        } catch (Exception e) {
            log.error("发送书籍新增MQ消息失败，bookName: {}", dto.getBookName(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
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
        // 1. 校验小说是否存在且属于该作者 (同步校验)
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (Objects.isNull(bookInfo)) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        BookSubmitMqDto submitDto = BookSubmitMqDto.builder()
                .operationType("UPDATE")
                .bookId(dto.getBookId())
                .authorId(dto.getAuthorId())
                .workDirection(dto.getWorkDirection())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .picUrl(dto.getPicUrl())
                .bookName(dto.getBookName())
                .bookDesc(dto.getBookDesc())
                .isVip(dto.getIsVip())
                .bookStatus(dto.getBookStatus())
                .auditEnable(auditEnable)
                .build();

        // 发送MQ消息，立即返回
        try {
            // Updated for async submit
            rocketMQTemplate.convertAndSend(AmqpConsts.BookSubmitMq.TOPIC + ":" + AmqpConsts.BookSubmitMq.TAG_SUBMIT, submitDto);
        } catch (Exception e) {
            log.error("发送书籍更新MQ消息失败，bookId: {}", dto.getBookId(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }

        return RestResp.ok();
    }


    /**
     * 作家新增书籍章节（同步版本，保留供内部调用）
     * 
     * 注意：前端新增章节的请求已改为异步MQ处理（见 AuthorController.publishBookChapter）
     * 此方法保留用于内部服务同步调用场景
     * 
     * @param dto 新增章节Dto
     * @return void
     */
    @Override
    public RestResp<Void> saveBookChapter(ChapterAddReqDto dto) {
        // 1. 权限校验（轻量级，只查询authorId字段）
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (bookInfo == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 2. 校验章节号是否重复（轻量级查询）
        QueryWrapper<BookChapter> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                    .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getChapterNum());
        
        if (bookChapterMapper.selectCount(checkWrapper) > 0) {
            return RestResp.fail(ErrorCodeEnum.CHAPTER_NUM_EXIST);
        }

        // 3. 构建章节提交MQ消息（包含所有新增信息）
        ChapterSubmitMqDto submitDto = ChapterSubmitMqDto.builder()
                .bookId(dto.getBookId())
                .authorId(dto.getAuthorId())
                .chapterNum(dto.getChapterNum())
                .chapterName(dto.getChapterName())
                .content(dto.getContent())
                .isVip(dto.getIsVip())
                .operationType("CREATE")
                .auditEnable(auditEnable)
                .build();

        // 4. 发送MQ消息，立即返回（所有数据库操作都在消费者中异步处理）
        try {
            String destination = AmqpConsts.ChapterSubmitMq.TOPIC + ":" 
                    + AmqpConsts.ChapterSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, submitDto);
            log.debug("章节新增请求已发送到MQ，bookId: {}, chapterNum: {}", 
                    dto.getBookId(), dto.getChapterNum());
        } catch (Exception e) {
            log.error("发送章节新增MQ消息失败，bookId: {}, chapterNum: {}", 
                    dto.getBookId(), dto.getChapterNum(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }

        // 5. 立即返回，网关线程快速释放
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
     * 作家更新书籍章节（同步版本，保留供内部调用）
     * 
     * 注意：前端更新章节的请求已改为异步MQ处理（见 AuthorController.updateBookChapter）
     * 此方法保留用于内部服务同步调用场景
     * 
     * @param dto 更新dto
     * @return void
     */
    @Override
    public RestResp<Void> updateBookChapter(ChapterUptReqDto dto) {
        // 1. 权限校验（轻量级，只查询authorId字段）
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (bookInfo == null) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 2. 构建章节提交MQ消息（包含所有更新信息）
        ChapterSubmitMqDto submitDto = ChapterSubmitMqDto.builder()
                .bookId(dto.getBookId())
                .authorId(dto.getAuthorId())
                .oldChapterNum(dto.getOldChapterNum())
                .chapterNum(dto.getChapterNum())
                .chapterName(dto.getChapterName())
                .content(dto.getContent())
                .isVip(dto.getIsVip())
                .operationType("UPDATE")
                .auditEnable(auditEnable)
                .build();

        // 3. 发送MQ消息，立即返回（所有数据库操作都在消费者中异步处理）
        try {
            String destination = AmqpConsts.ChapterSubmitMq.TOPIC + ":" 
                    + AmqpConsts.ChapterSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, submitDto);
            log.debug("章节更新请求已发送到MQ，bookId: {}, chapterNum: {}", 
                    dto.getBookId(), dto.getChapterNum());
        } catch (Exception e) {
            log.error("发送章节更新MQ消息失败，bookId: {}, chapterNum: {}", 
                    dto.getBookId(), dto.getChapterNum(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }

        // 4. 立即返回，网关线程快速释放
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
     * 发送书籍变更消息
     */
    private void sendBookChangeMsg(Long bookId) {
        if (bookId == null) {
            return;
        }
        // 构建 Destination: Topic:Tag
        String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        // 发送消息，消息体就是 bookId
                        rocketMQTemplate.convertAndSend(destination, bookId);
                    } catch (Exception e) {
                        log.error("发送书籍ChangeMsg MQ失败，bookId: {}", bookId, e);
                    }
                }
            });
        } else {
            // 发送消息，消息体就是 bookId
            rocketMQTemplate.convertAndSend(destination, bookId);
        }
    }

    /**
     * 生成任务ID（用于关联审核请求和结果，保证幂等性）
     * @param chapterId 章节ID
     * @return 任务ID
     */
    private String generateTaskId(Long chapterId) {
        return String.format("audit_chapter_%s_%s_%s", 
                chapterId, 
                System.currentTimeMillis(), 
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 作家获取书籍详情（用于编辑，不过滤审核状态）
     * @param bookId 书籍ID
     * @param authorId 作者ID（用于权限校验）
     * @return 书籍详情
     */
    @Override
    public RestResp<BookInfoRespDto> getBookByIdForAuthor(Long bookId, Long authorId) {
        // 1. 校验书籍是否存在且属于该作者
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (bookInfo == null) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        if (!Objects.equals(bookInfo.getAuthorId(), authorId)) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 2. 组装响应对象（只返回编辑需要的字段）
        BookInfoRespDto bookInfoRespDto = BookInfoRespDto.builder()
                .id(bookInfo.getId())
                .bookName(bookInfo.getBookName())
                .bookDesc(bookInfo.getBookDesc())
                .bookStatus(bookInfo.getBookStatus())
                .authorId(bookInfo.getAuthorId())
                .authorName(bookInfo.getAuthorName())
                .categoryId(bookInfo.getCategoryId())
                .categoryName(bookInfo.getCategoryName())
                .workDirection(bookInfo.getWorkDirection())
                .picUrl(bookInfo.getPicUrl())
                .auditStatus(bookInfo.getAuditStatus() != null ? bookInfo.getAuditStatus() : 0)
                .build();

        return RestResp.ok(bookInfoRespDto);
    }
}
