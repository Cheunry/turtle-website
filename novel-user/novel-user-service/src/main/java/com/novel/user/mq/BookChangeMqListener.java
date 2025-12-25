package com.novel.user.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel.book.dto.mq.BookChapterUpdateDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.user.dao.entity.MessageContent;
import com.novel.user.dao.entity.MessageReceive;
import com.novel.user.dao.entity.UserBookshelf;
import com.novel.user.dao.mapper.MessageContentMapper;
import com.novel.user.dao.mapper.MessageReceiveMapper;
import com.novel.user.dao.mapper.UserBookshelfMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍更新消息消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = AmqpConsts.BookChangeMq.TOPIC, consumerGroup = "group-book-update-notification", selectorExpression = AmqpConsts.BookChangeMq.TAG_CHAPTER_UPDATE)
public class BookChangeMqListener implements RocketMQListener<BookChapterUpdateDto> {

    private final UserBookshelfMapper userBookshelfMapper;
    private final MessageContentMapper messageContentMapper;
    private final MessageReceiveMapper messageReceiveMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(BookChapterUpdateDto dto) {
        log.info("收到书籍更新消息: {}", dto);

        // 1. 查询订阅了该书的所有用户
        LambdaQueryWrapper<UserBookshelf> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBookshelf::getBookId, dto.getBookId())
                    .select(UserBookshelf::getUserId);
        
        List<UserBookshelf> subscriptions = userBookshelfMapper.selectList(queryWrapper);
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }

        // 2. 创建消息内容
        MessageContent content = new MessageContent();
        content.setTitle("书籍更新提醒");
        content.setContent(String.format("您订阅的小说《%s》更新了新章节：%s，快去看看吧！", dto.getBookName(), dto.getChapterName()));
        content.setType(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_SUBSCRIBE_UPDATE); // 订阅更新/作品更新
        // 前端路由地址
        content.setLink("/book/" + dto.getBookId() + "/" + dto.getChapterNum()); 
        content.setBusId(dto.getBookId());
        content.setBusType("book");
        content.setSenderType(DatabaseConsts.MessageContentTable.SENDER_TYPE_SYSTEM); // 系统发送
        content.setCreateTime(LocalDateTime.now());
        content.setUpdateTime(LocalDateTime.now());
        
        messageContentMapper.insert(content);

        // 3. 批量插入接收记录
        // 如果订阅量大，应考虑使用 Batch Insert
        for (UserBookshelf sub : subscriptions) {
            MessageReceive receive = new MessageReceive();
            receive.setMessageId(content.getId());
            receive.setReceiverId(sub.getUserId());
            receive.setReceiverType(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER); // 普通用户
            receive.setIsRead(0);
            receive.setIsDeleted(0);
            messageReceiveMapper.insert(receive);
        }
        
        log.info("书籍更新通知发送完成，涉及用户数: {}", subscriptions.size());
    }
}

