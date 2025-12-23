package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.novel.common.auth.UserHolder;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageContentMapper messageContentMapper;
    private final MessageReceiveMapper messageReceiveMapper;

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
        messageContent.setSenderType(0); // 系统发送
        messageContent.setSenderId(0L);
        messageContent.setCreateTime(LocalDateTime.now());
        messageContent.setUpdateTime(LocalDateTime.now());
        
        messageContentMapper.insert(messageContent);

        // 2. 插入消息接收关系
        MessageReceive messageReceive = new MessageReceive();
        messageReceive.setMessageId(messageContent.getId());
        messageReceive.setReceiverId(dto.getReceiverId());
        messageReceive.setReceiverType(dto.getReceiverType() != null ? dto.getReceiverType() : 1); // 默认为普通用户
        messageReceive.setIsRead(0);
        messageReceive.setIsDeleted(0);
        
        messageReceiveMapper.insert(messageReceive);
    }

    @Override
    public RestResp<PageRespDto<MessageRespDto>> listMessages(MessagePageReqDto pageReqDto) {

        Long userId = UserHolder.getUserId();
        Long authorId = UserHolder.getAuthorId();
        
        // 如果请求中明确指定了 receiverType，则使用指定的值
        // 否则根据 authorId 是否存在自动判断
        Integer receiverType;
        Long receiverId;
        
        if (pageReqDto.getReceiverType() != null) {
            // 使用请求中指定的 receiverType
            receiverType = pageReqDto.getReceiverType();
            receiverId = (receiverType == 2) ? authorId : userId;
        } else {
            // 自动判断：如果 authorId 不为空，说明是作者，查询作者消息（receiver_type=2）
            // 否则查询普通用户消息（receiver_type=1）
            receiverType = (authorId != null) ? 2 : 1;
            receiverId = (authorId != null) ? authorId : userId;
        }

        Page<MessageRespDto> page = new Page<>();
        page.setCurrent(pageReqDto.getPageNum());
        page.setSize(pageReqDto.getPageSize());

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

    @Override
    public RestResp<Long> getUnReadCount(Integer messageType) {
        Long userId = UserHolder.getUserId();
        
        // 对于未读消息统计，默认只统计普通用户消息（receiver_type=1）
        // 作者消息的未读统计应该通过专门的作者接口来查询
        // 这样可以避免在普通用户消息页面显示作者消息的未读数
        Integer receiverType = 1; // 固定为普通用户
        Long receiverId = userId;
        
        // 使用自定义联表查询，支持按 messageType、busType 和 receiverType 统计
        Long count = messageReceiveMapper.countUnRead(receiverId, receiverType, messageType, null);
        log.info("Count unread messages for user: {}, count: {}", userId, count);
        return RestResp.ok(count);
    }
    
    /**
     * 获取指定接收者类型的未读消息数量
     * @param messageType 消息类型
     * @param receiverType 接收者类型 (1:普通用户, 2:作者)
     * @return 未读消息数量
     */
    public RestResp<Long> getUnReadCountByReceiverType(Integer messageType, Integer receiverType) {
        Long userId = UserHolder.getUserId();
        Long authorId = UserHolder.getAuthorId();
        
        Long receiverId = (receiverType == 2) ? authorId : userId;
        if (receiverId == null) {
            return RestResp.ok(0L);
        }
        
        Long count = messageReceiveMapper.countUnRead(receiverId, receiverType, messageType, null);
        return RestResp.ok(count);
    }

    @Override
    public RestResp<Void> readMessage(Long id) {
        log.info("Reading message with id: {}", id);
        // 使用 UpdateWrapper 强制更新，确保不被忽略
        UpdateWrapper<MessageReceive> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id)
                .set("is_read", 1)
                .set("read_time", LocalDateTime.now());
        int rows = messageReceiveMapper.update(null, updateWrapper);
        log.info("Updated message read status, id: {}, rows affected: {}", id, rows);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteMessage(Long id) {
        MessageReceive receive = new MessageReceive();
        receive.setId(id);
        receive.setIsDeleted(1);
        messageReceiveMapper.updateById(receive);
        return RestResp.ok();
    }

    private Long getReceiverId(Integer receiverType) {
        if (receiverType == 2) {
            return UserHolder.getAuthorId();
        } else {
            return UserHolder.getUserId();
        }
    }

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
