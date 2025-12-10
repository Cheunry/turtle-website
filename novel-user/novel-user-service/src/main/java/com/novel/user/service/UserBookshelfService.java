package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.UserBookshelfRespDto;

import java.util.List;

public interface UserBookshelfService {

    /**
     * 加入书架接口
     *
     * @param userId 用户ID
     * @param bookId 小说ID
     * @return void
     */
    RestResp<Void> addToBookshelf(Long userId, Long bookId);

    /**
     * 查询书架状态接口
     *
     * @param userId 用户ID
     * @param bookId 小说ID
     * @return 0-不在书架 1-已在书架
     */
    RestResp<Integer> getBookshelfStatus(Long userId, String bookId);

    /**
     * 查询书架列表接口
     *
     * @param userId 用户ID
     * @return 书架列表
     */
    RestResp<List<UserBookshelfRespDto>> listBookshelf(Long userId);

    /**
     * 更新上次阅读的章节
     * @param userId 用户ID
     * @param bookId 小说ID
     * @param chapterNum 章节号
     * @return void
     */
    RestResp<Void> updatePreChapterId(Long userId, Long bookId, Integer chapterNum);

    /**
     * 从书架删除某本书
     * @param userId 用户ID
     * @param bookId 书籍ID
     * @return void
     */
    RestResp<Void> deleteBookshelf(Long userId, Long bookId);

}
