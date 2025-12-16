package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.CommonConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.UserBookshelf;
import com.novel.user.dao.mapper.UserBookshelfMapper;
import com.novel.user.dto.resp.UserBookshelfRespDto;
import com.novel.user.feign.BookFeignManager;
import com.novel.user.service.UserBookshelfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserBookshelfServiceImpl implements UserBookshelfService {

    private final UserBookshelfMapper userBookshelfMapper;
    private final BookFeignManager bookFeignManager;

    @Override
    public RestResp<Void> addToBookshelf(Long userId, Long bookId) {
        // 1. 校验是否已在书架
        QueryWrapper<UserBookshelf> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserBookshelfTable.COLUMN_BOOK_ID, bookId);
        if (userBookshelfMapper.selectCount(queryWrapper) > 0) {
            return RestResp.ok();
        }

        // 2. 加入书架
        UserBookshelf userBookshelf = new UserBookshelf();
        userBookshelf.setUserId(userId);
        userBookshelf.setBookId(bookId);
        userBookshelf.setPreChapterNum(0); // 初始化阅读进度为0
        userBookshelf.setCreateTime(LocalDateTime.now());
        userBookshelf.setUpdateTime(LocalDateTime.now());
        
        userBookshelfMapper.insert(userBookshelf);
        
        return RestResp.ok();
    }

    @Override
    public RestResp<Integer> getBookshelfStatus(Long userId, String bookId) {
        QueryWrapper<UserBookshelf> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserBookshelfTable.COLUMN_BOOK_ID, bookId);
        return RestResp.ok(
                userBookshelfMapper.selectCount(queryWrapper) > 0
                        ? CommonConsts.YES
                        : CommonConsts.NO
        );
    }

    @Override
    public RestResp<List<UserBookshelfRespDto>> listBookshelf(Long userId) {
        QueryWrapper<UserBookshelf> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());
        List<UserBookshelf> userBookshelves = userBookshelfMapper.selectList(queryWrapper);

        if (userBookshelves.isEmpty()) {
            return RestResp.ok(Collections.emptyList());
        }

        List<Long> bookIds = userBookshelves.stream().map(UserBookshelf::getBookId).toList();
        RestResp<List<BookInfoRespDto>> bookInfoResp = bookFeignManager.listBookInfoByIds(bookIds);
        
        if (bookInfoResp == null || bookInfoResp.getData() == null) {
             return RestResp.ok(Collections.emptyList());
        }

        Map<Long, BookInfoRespDto> bookInfoMap = bookInfoResp.getData().stream()
                .collect(Collectors.toMap(BookInfoRespDto::getId, Function.identity()));

        return RestResp.ok(userBookshelves.stream().map(v -> {
            BookInfoRespDto bookInfo = bookInfoMap.get(v.getBookId());
            if (bookInfo == null) {
                return null;
            }
            return UserBookshelfRespDto.builder()
                    .bookId(v.getBookId())
                    .bookName(bookInfo.getBookName())
                    .picUrl(bookInfo.getPicUrl())
                    .authorName(bookInfo.getAuthorName())
                    .firstChapterNum(bookInfo.getFirstChapterNum() == null ? 1  :  bookInfo.getFirstChapterNum())
                    .preChapterNum(v.getPreChapterNum())
                    .build();
        }).filter(Objects::nonNull).toList());
    }

    @Override
    public RestResp<Void> updatePreChapterId(Long userId, Long bookId, Integer chapterNum) {
        // 更新条件：user_id, book_id
        UpdateWrapper<UserBookshelf> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserBookshelfTable.COLUMN_BOOK_ID, bookId)
                // 设置更新内容
                .set("pre_chapter_num", chapterNum)
                .set(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName(), LocalDateTime.now());

        userBookshelfMapper.update(null, updateWrapper);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteBookshelf(Long userId, Long bookId) {
        QueryWrapper<UserBookshelf> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserBookshelfTable.COLUMN_USER_ID, userId)
                .eq(DatabaseConsts.UserBookshelfTable.COLUMN_BOOK_ID, bookId);
        userBookshelfMapper.delete(queryWrapper);
        return RestResp.ok();
    }


}
