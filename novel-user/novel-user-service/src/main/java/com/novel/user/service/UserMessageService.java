package com.novel.user.service;

import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.MessageRespDto;

import java.util.Map;

/**
 * 消息服务接口
 */
public interface UserMessageService {

    /**
     * 发送系统消息（单发）
     * @param receiverId 接收者ID
     * @param receiverType 接收者类型 (1:用户, 2:作者)
     * @param title 标题
     * @param content 正文
     * @param type 消息类型
     * @param extension 扩展数据
     */
    void sendMessage(Long receiverId, Integer receiverType, String title, String content, Integer type, Map<String, Object> extension);

    /**
     * 获取当前用户的消息列表
     * @param pageReqDto 分页参数
     * @param isAuthor 是否查询作者信箱
     * @return 消息列表
     */
    RestResp<PageRespDto<MessageRespDto>> listMessages(PageReqDto pageReqDto, boolean isAuthor);

    /**
     * 获取未读消息数量
     * @param isAuthor 是否查询作者信箱
     * @return 未读数量
     */
    RestResp<Long> getUnReadCount(boolean isAuthor);

    /**
     * 标记消息为已读
     * @param id 消息接收表ID
     * @param isAuthor 身份校验
     */
    RestResp<Void> readMessage(Long id, boolean isAuthor);
}

