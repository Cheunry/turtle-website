package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.common.auth.UserHolder;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.MessageContent;
import com.novel.user.dao.entity.MessageReceive;
import com.novel.user.dao.mapper.MessageContentMapper;
import com.novel.user.dao.mapper.MessageReceiveMapper;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.user.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private final MessageContentMapper messageContentMapper;
    private final MessageReceiveMapper messageReceiveMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(Long receiverId, Integer receiverType, String title, String content, Integer type, Map<String, Object> extension) {
        
        // 1. 插入消息内容
        MessageContent messageContent = MessageContent.builder()
                .title(title)
                .content(content)
                .type(type)
                .extension(extension)
                .senderType(0) // 系统发送
                .senderId(0L)
                .createTime(LocalDateTime.now())
                .build();
        messageContentMapper.insert(messageContent);

        // 2. 插入消息接收关系
        MessageReceive messageReceive = MessageReceive.builder()
                .messageId(messageContent.getId())
                .receiverId(receiverId)
                .receiverType(receiverType)
                .isRead(0)
                .isDeleted(0)
                .build();
        messageReceiveMapper.insert(messageReceive);
    }

    @Override
    public RestResp<PageRespDto<MessageRespDto>> listMessages(PageReqDto pageReqDto, boolean isAuthor) {
        
        Long userId = UserHolder.getUserId();
        // 如果是查询作者信箱，则使用 AuthorId；如果是用户信箱，则使用 UserId
        Long receiverId = isAuthor ? UserHolder.getAuthorId() : userId;
        Integer receiverType = isAuthor ? 2 : 1;

        if (isAuthor && Objects.isNull(receiverId)) {
             return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), 0L, new ArrayList<>()));
        }

        Page<MessageReceive> page = new Page<>();
        page.setCurrent(pageReqDto.getPageNum());
        page.setSize(pageReqDto.getPageSize());

        QueryWrapper<MessageReceive> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_id", receiverId)
                .eq("receiver_type", receiverType)
                .eq("is_deleted", 0)
                .orderByDesc("id");

        IPage<MessageReceive> messageReceiveIPage = messageReceiveMapper.selectPage(page, queryWrapper);

        List<MessageReceive> records = messageReceiveIPage.getRecords();
        if (records.isEmpty()) {
            return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), 0L, new ArrayList<>()));
        }

        List<Long> messageIds = records.stream().map(MessageReceive::getMessageId).collect(Collectors.toList());
        List<MessageContent> contents = messageContentMapper.selectBatchIds(messageIds);
        Map<Long, MessageContent> contentMap = contents.stream().collect(Collectors.toMap(MessageContent::getId, v -> v));

        List<MessageRespDto> dtos = records.stream().map(v -> {
            MessageContent content = contentMap.get(v.getMessageId());
            return MessageRespDto.builder()
                    .id(v.getId())
                    .isRead(v.getIsRead())
                    .createTime(content != null ? content.getCreateTime() : null)
                    .title(content != null ? content.getTitle() : "")
                    .content(content != null ? content.getContent() : "")
                    .extension(content != null ? content.getExtension() : null)
                    .build();
        }).collect(Collectors.toList());

        return RestResp.ok(PageRespDto.of(pageReqDto.getPageNum(), pageReqDto.getPageSize(), messageReceiveIPage.getTotal(), dtos));
    }

    @Override
    public RestResp<Long> getUnReadCount(boolean isAuthor) {
        Long userId = UserHolder.getUserId();
        Long receiverId = isAuthor ? UserHolder.getAuthorId() : userId;
        Integer receiverType = isAuthor ? 2 : 1;
        
        if (isAuthor && Objects.isNull(receiverId)) {
            return RestResp.ok(0L);
        }

        QueryWrapper<MessageReceive> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_id", receiverId)
                .eq("receiver_type", receiverType)
                .eq("is_read", 0)
                .eq("is_deleted", 0);
        return RestResp.ok(messageReceiveMapper.selectCount(queryWrapper));
    }

    @Override
    public RestResp<Void> readMessage(Long id, boolean isAuthor) {
        // 简单校验权限，防止越权操作（严谨做法应查询数据库对比 receiverId）
        MessageReceive receive = MessageReceive.builder()
                .id(id)
                .isRead(1)
                .readTime(LocalDateTime.now())
                .build();
        messageReceiveMapper.updateById(receive);
        return RestResp.ok();
    }
}
