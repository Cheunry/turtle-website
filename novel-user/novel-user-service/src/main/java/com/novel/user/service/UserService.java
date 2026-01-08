package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.UserBookshelfRespDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.dto.resp.UserLoginRespDto;
import com.novel.user.dto.resp.UserRegisterRespDto;

import java.util.List;

public interface UserService {

    /**
     * 用户注册
     *
     * @param dto 注册参数
     * @return JWT
     */
    RestResp<UserRegisterRespDto> register(UserRegisterReqDto dto);

    /**
     * 用户登录
     *
     * @param dto 登录参数
     * @return JWT + 昵称
     */
    RestResp<UserLoginRespDto> login(UserLoginReqDto dto);

    /**
     * 用户登出
     *
     * @param token 用户Token
     * @return void
     */
    RestResp<Void> logout(String token);

    /**
     * 用户信息查询
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    RestResp<UserInfoRespDto> getUserInfo(Long userId);

    /**
     * 批量查询用户信息
     *
     * @param userIds 用户ID列表
     * @return 用户信息列表
     */
    RestResp<List<UserInfoRespDto>> listUserInfoByIds(List<Long> userIds);

    /**
     * 用户信息修改
     *
     * @param dto 修改参数
     * @return void
     */
    RestResp<Void> updateUserInfo(UserInfoUptReqDto dto);

    /**
     * 用户反馈
     *
     * @param userId  反馈用户ID
     * @param content 反馈内容
     * @return void
     */
    RestResp<Void> saveFeedback(Long userId, String content);

    /**
     * 用户反馈删除
     *
     * @param userId 用户ID
     * @param id     反馈ID
     * @return void
     */
    RestResp<Void> deleteFeedback(Long userId, Long id);


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

