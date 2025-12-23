package com.novel.user.service;

import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.req.MessageSendReqDto;
import com.novel.user.dto.resp.MessageRespDto;

/**
 * 消息服务接口
 */
public interface MessageService {

    /**
     * 发送系统消息（单发）
     * @param dto 发送消息请求DTO
     */
    void sendMessage(MessageSendReqDto dto);

    /**
     * 获取当前用户的消息列表
     * @param pageReqDto 分页请求参数（包含分页信息和消息类型）
     * @return 消息列表
     */
    RestResp<PageRespDto<MessageRespDto>> listMessages(MessagePageReqDto pageReqDto);

    /**
     * 获取未读消息数量
     * @param messageType 消息类型，null则查全部
     * @return 未读数量
     */
    RestResp<Long> getUnReadCount(Integer messageType);
    
    /**
     * 获取指定接收者类型的未读消息数量
     * @param messageType 消息类型，null则查全部
     * @param receiverType 接收者类型 (1:普通用户, 2:作者)
     * @return 未读数量
     */
    RestResp<Long> getUnReadCountByReceiverType(Integer messageType, Integer receiverType);

    /**
     * 标记消息为已读
     * @param id 消息接收表ID
     */
    RestResp<Void> readMessage(Long id);

    /**
     * 删除消息
     * @param id 消息接收表ID
     */
    RestResp<Void> deleteMessage(Long id);

    /**
     * 批量标记已读
     */
    RestResp<Void> batchReadMessages(Integer receiverType, java.util.List<Long> ids);

    /**
     * 批量删除
     */
    RestResp<Void> batchDeleteMessages(Integer receiverType, java.util.List<Long> ids);

    /**
     * 全部标记已读
     */
    RestResp<Void> allReadMessages(Integer receiverType);

    /**
     * 全部删除
     */
    RestResp<Void> allDeleteMessages(Integer receiverType);
}
