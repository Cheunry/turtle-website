package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.MessageContent;
import com.novel.user.dao.entity.MessageReceive;
import com.novel.user.dao.mapper.MessageContentMapper;
import com.novel.user.dao.mapper.MessageReceiveMapper;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.req.MessageSendReqDto;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.user.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageContentMapper messageContentMapper;
    private final MessageReceiveMapper messageReceiveMapper;

    /**
     * 发送消息
     * @param dto 消息内容
     *            0:系统公告/全员, 1:订阅更新/追更, 2:作家助手/审核, 3:私信
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(MessageSendReqDto dto) {
        
        // 1. 插入消息内容
        MessageContent messageContent = new MessageContent();
        messageContent.setTitle(dto.getTitle());
        messageContent.setContent(dto.getContent());
        messageContent.setType(dto.getType());
        messageContent.setLink(dto.getLink());
        messageContent.setBusId(dto.getBusId());
        messageContent.setBusType(dto.getBusType());
        messageContent.setExtension(dto.getExtension());
        messageContent.setExpireTime(dto.getExpireTime()); // 设置过期时间，NULL表示永不过期
        messageContent.setSenderType(DatabaseConsts.MessageContentTable.SENDER_TYPE_SYSTEM); // 系统发送
        messageContent.setSenderId(0L);
        messageContent.setCreateTime(LocalDateTime.now());
        messageContent.setUpdateTime(LocalDateTime.now());
        
        messageContentMapper.insert(messageContent);

        // 2. 如果是系统公告（type=0），采用拉取模式，不插入 message_receive 记录
        // 其他类型的消息，需要插入消息接收关系
        if (dto.getType() == null || !dto.getType().equals(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT)) {
            MessageReceive messageReceive = new MessageReceive();
            messageReceive.setMessageId(messageContent.getId());
            messageReceive.setReceiverId(dto.getReceiverId());
            messageReceive.setReceiverType(dto.getReceiverType() != null ? dto.getReceiverType() : DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER); // 默认为普通用户
            messageReceive.setIsRead(0);
            messageReceive.setIsDeleted(0);
            
            messageReceiveMapper.insert(messageReceive);
        }
    }
    /**
     * 获取消息列表
     * @param pageReqDto 分页参数
     * @return 消息列表
     */
    @Override
    public RestResp<PageRespDto<MessageRespDto>> listMessages(MessagePageReqDto pageReqDto) {

        Long userId = UserHolder.getUserId();
        Long authorId = UserHolder.getAuthorId();
        
        Page<MessageRespDto> page = new Page<>();
        page.setCurrent(pageReqDto.getPageNum());
        page.setSize(pageReqDto.getPageSize());

        // 如果查询的是系统公告，采用拉取模式，直接从 message_content 表查询
        if (pageReqDto.getMessageType() != null && pageReqDto.getMessageType().equals(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT)) {
            IPage<MessageRespDto> messageRespDtoIPage = messageContentMapper.selectSystemMessageList(page, DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT);
            return RestResp.ok(PageRespDto.of(
                pageReqDto.getPageNum(), 
                pageReqDto.getPageSize(), 
                messageRespDtoIPage.getTotal(), 
                messageRespDtoIPage.getRecords()
            ));
        }
        
        // 确定接收者信息
        Integer receiverType;
        Long receiverId;
        
        if (pageReqDto.getReceiverType() != null) {
            // 使用请求中指定的 receiverType
            receiverType = pageReqDto.getReceiverType();
            receiverId = (receiverType.equals(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR)) ? authorId : userId;
        } else {
            // 自动判断：如果 authorId 不为空，说明是作者，查询作者消息（receiver_type=1）
            // 否则查询普通用户消息（receiver_type=0）
            receiverType = (authorId != null) ? DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR : DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER;
            receiverId = (authorId != null) ? authorId : userId;
        }
        
        // 如果receiverId为null，只查询系统消息
        if (receiverId == null) {
            log.warn("receiverId is null, userId: {}, authorId: {}", userId, authorId);
            if (pageReqDto.getMessageType() != null && pageReqDto.getMessageType().equals(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT)) {
                IPage<MessageRespDto> messageRespDtoIPage = messageContentMapper.selectSystemMessageList(page, DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT);
                return RestResp.ok(PageRespDto.of(
                    pageReqDto.getPageNum(), 
                    pageReqDto.getPageSize(), 
                    messageRespDtoIPage.getTotal(), 
                    messageRespDtoIPage.getRecords()
                ));
            } else {
                // receiverId为null且不是查询系统消息，返回空列表
                return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), 0L, new ArrayList<>()));
            }
        }

        // 如果查询全部消息（messageType 为 null），需要合并系统公告和其他消息
        if (pageReqDto.getMessageType() == null) {
            log.info("查询全部消息 - receiverId: {}, receiverType: {}, busType: {}", receiverId, receiverType, pageReqDto.getBusType());
            IPage<MessageRespDto> messageRespDtoIPage = messageContentMapper.selectAllMessagesList(page, receiverId, receiverType, pageReqDto.getBusType());
            log.info("查询全部消息结果 - total: {}, size: {}", messageRespDtoIPage.getTotal(), messageRespDtoIPage.getRecords().size());
            return RestResp.ok(PageRespDto.of(
                pageReqDto.getPageNum(), 
                pageReqDto.getPageSize(), 
                messageRespDtoIPage.getTotal(), 
                messageRespDtoIPage.getRecords()
            ));
        }

        // 其他类型的消息，使用原有的 message_receive 模式
        // 使用自定义联表查询，支持按 messageType、busType 和 receiverType 过滤和准确分页
        IPage<MessageRespDto> messageRespDtoIPage = messageReceiveMapper.selectMessageList(page, receiverId, receiverType, pageReqDto.getMessageType(), pageReqDto.getBusType());
        
        // Debug Log: Check the first item to see isRead status
        if (!messageRespDtoIPage.getRecords().isEmpty()) {
            log.info("First message isRead status: {}", messageRespDtoIPage.getRecords().get(0).getIsRead());
        }

        return RestResp.ok(PageRespDto.of(
            pageReqDto.getPageNum(), 
            pageReqDto.getPageSize(), 
            messageRespDtoIPage.getTotal(), 
            messageRespDtoIPage.getRecords()
        ));
    }

    /**
     * 获取未读消息数量
     * @param messageType 消息类型
     * @return 未读消息数量
     */
    @Override
    public RestResp<Long> getUnReadCount(Integer messageType) {
        Long userId = UserHolder.getUserId();
        
        // 用户主页的信箱未读数只统计普通用户消息（receiver_type=0），不包含作者消息
        // 作者消息的未读统计应该通过专门的作者接口来查询
        // 这样可以避免在普通用户消息页面显示作者消息的未读数
        Integer receiverType = DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER; // 固定为普通用户
        Long receiverId = userId;
        
        // 如果查询的是系统公告（type=0），统计未过期的系统公告数量（系统公告不支持已读，所以所有未过期的都是"未读"）
        if (messageType != null && messageType.equals(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT)) {
            Long count = messageContentMapper.countSystemMessages(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT);
            log.info("Count system messages (type=0) for user: {}, count: {}", userId, count);
            return RestResp.ok(count);
        }
        
        // 如果查询全部消息（messageType 为 null），需要统计系统公告和其他消息的未读数
        if (messageType == null) {
            // 系统公告的未读数（所有未过期的系统公告都是"未读"）
            Long systemCount = messageContentMapper.countSystemMessages(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT);
            // 其他消息的未读数（只统计普通用户消息，不包含作者消息）
            Long otherCount = messageReceiveMapper.countUnRead(receiverId, receiverType, null, null);
            Long totalCount = systemCount + otherCount;
            log.info("Count all unread messages for user: {}, system: {}, other (user only): {}, total: {}", userId, systemCount, otherCount, totalCount);
            return RestResp.ok(totalCount);
        }
        
        // 其他类型的消息，使用原有的 message_receive 模式统计（只统计普通用户消息）
        Long count = messageReceiveMapper.countUnRead(receiverId, receiverType, messageType, null);
        log.info("Count unread messages for user: {}, count: {}", userId, count);
        return RestResp.ok(count);
    }
    
    /**
     * 获取指定接收者类型的未读消息数量
     * @param messageType 消息类型
     * @param receiverType 接收者类型 (0:普通用户, 1:作者)
     * @return 未读消息数量
     */
    public RestResp<Long> getUnReadCountByReceiverType(Integer messageType, Integer receiverType) {
        Long userId = UserHolder.getUserId();
        Long authorId = UserHolder.getAuthorId();
        
        Long receiverId = (receiverType.equals(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR)) ? authorId : userId;
        if (receiverId == null) {
            return RestResp.ok(0L);
        }
        
        Long count = messageReceiveMapper.countUnRead(receiverId, receiverType, messageType, null);
        return RestResp.ok(count);
    }

    /**
     * 读取消息
     * @param id 消息ID
     * @return 响应结果
     */
    @Override
    public RestResp<Void> readMessage(Long id) {
        log.info("Reading message with id: {}", id);
        
        // 检查是否是系统公告（通过 message_content.id 查询）
        // 如果 message_receive 中不存在该 id，说明是系统公告，直接返回成功（系统公告不支持已读）
        MessageReceive messageReceive = messageReceiveMapper.selectById(id);
        if (messageReceive == null) {
            // 系统公告不支持已读操作，直接返回成功
            log.info("Message id {} is system announcement, read operation ignored", id);
            return RestResp.ok();
        }
        
        // 使用 UpdateWrapper 强制更新，确保不被忽略
        UpdateWrapper<MessageReceive> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id)
                .set("is_read", 1)
                .set("read_time", LocalDateTime.now());
        int rows = messageReceiveMapper.update(null, updateWrapper);
        log.info("Updated message read status, id: {}, rows affected: {}", id, rows);
        return RestResp.ok();
    }
    /**
     * 删除指定消息
     * @param id 消息ID
     * @return 删除结果
     */
    @Override
    public RestResp<Void> deleteMessage(Long id) {
        // 检查是否是系统公告（通过 message_content.id 查询）
        // 如果 message_receive 中不存在该 id，说明是系统公告，直接返回成功（系统公告不支持删除）
        MessageReceive messageReceive = messageReceiveMapper.selectById(id);
        if (messageReceive == null) {
            // 系统公告不支持删除操作，直接返回成功
            log.info("Message id {} is system announcement, delete operation ignored", id);
            return RestResp.ok();
        }
        
        MessageReceive receive = new MessageReceive();
        receive.setId(id);
        receive.setIsDeleted(1);
        messageReceiveMapper.updateById(receive);
        return RestResp.ok();
    }
    /**
     * 获取指定接收者类型的接收者ID
     * @param receiverType 接收者类型 (0:普通用户, 1:作者)
     * @return 接收者ID
     */
    private Long getReceiverId(Integer receiverType) {
        if (receiverType.equals(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR)) {
            return UserHolder.getAuthorId();
        } else {
            return UserHolder.getUserId();
        }
    }

    /**
     * 批量已读消息
     * @param receiverType 接收者类型 (0:普通用户, 1:作者)
     * @param ids 消息ID列表
     * @return 批量已读结果
     */
    @Override
    public RestResp<Void> batchReadMessages(Integer receiverType, java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return RestResp.ok();
        }
        Long receiverId = getReceiverId(receiverType);
        if (receiverId == null) return RestResp.ok();
        
        UpdateWrapper<MessageReceive> correctWrapper = new UpdateWrapper<>();
        correctWrapper.in("id", ids)
                      .eq("receiver_id", receiverId)
                      .eq("receiver_type", receiverType)
                      .set("is_read", 1)
                      .set("read_time", LocalDateTime.now());

        messageReceiveMapper.update(null, correctWrapper);
        return RestResp.ok();
    }

    /**
     * 批量删除消息
     * @param receiverType 接收者类型 (0:普通用户, 1:作者)
     * @param ids 消息ID列表
     * @return 批量删除结果
     */
    @Override
    public RestResp<Void> batchDeleteMessages(Integer receiverType, java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return RestResp.ok();
        }
        Long receiverId = getReceiverId(receiverType);
        if (receiverId == null) return RestResp.ok();

        MessageReceive receive = new MessageReceive();
        receive.setIsDeleted(1);
        
        UpdateWrapper<MessageReceive> updateWrapper = new UpdateWrapper<>();
        updateWrapper.in("id", ids)
                     .eq("receiver_id", receiverId)
                     .eq("receiver_type", receiverType);
                     
        messageReceiveMapper.update(receive, updateWrapper);
        return RestResp.ok();
    }

    /**
     * 全部已读
     * @param receiverType 接收者类型 (0:普通用户, 1:作者)
     * @return 全部已读结果
     */
    @Override
    public RestResp<Void> allReadMessages(Integer receiverType) {
        Long receiverId = getReceiverId(receiverType);
        if (receiverId == null) return RestResp.ok();

        UpdateWrapper<MessageReceive> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("receiver_id", receiverId)
                     .eq("receiver_type", receiverType)
                     .eq("is_read", 0) // 只更新未读的
                     .set("is_read", 1)
                     .set("read_time", LocalDateTime.now());
        
        messageReceiveMapper.update(null, updateWrapper);
        return RestResp.ok();
    }

    /**
     * 全部删除
     * @param receiverType 接收者类型 (0:普通用户, 1:作者)
     * @return 全部删除结果
     */
    @Override
    public RestResp<Void> allDeleteMessages(Integer receiverType) {
        Long receiverId = getReceiverId(receiverType);
        if (receiverId == null) return RestResp.ok();

        MessageReceive receive = new MessageReceive();
        receive.setIsDeleted(1);
        
        UpdateWrapper<MessageReceive> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("receiver_id", receiverId)
                     .eq("receiver_type", receiverType)
                     .eq("is_deleted", 0); // 只删除未删除的
                     
        messageReceiveMapper.update(receive, updateWrapper);
        return RestResp.ok();
    }
}
