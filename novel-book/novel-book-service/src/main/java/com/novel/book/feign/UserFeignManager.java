package com.novel.book.feign;

import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.MessageSendReqDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.feign.SseNotificationFeign;
import com.novel.user.feign.UserFeign;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@AllArgsConstructor
public class UserFeignManager {

    private final UserFeign userFeign;
    private final SseNotificationFeign sseNotificationFeign;

    /**
     * 获取用户基础信息列表
     * @param userIds 用户ID List
     * @return 用户基础信息列表
     */
    public List<UserInfoRespDto> listUserInfoByIds(List<Long> userIds) {

        RestResp<List<UserInfoRespDto>> resp = userFeign.listUserInfoByIds(userIds);
        if (Objects.equals(ErrorCodeEnum.OK.getCode(), resp.getCode())) {
            return resp.getData();
        }
        return new ArrayList<>(0);
    }

    /**
     * 发送消息
     */
    public void sendMessage(MessageSendReqDto dto) {
        userFeign.sendMessage(dto);
    }

    /**
     * 通过SSE推送通知给作者
     * @param authorId 作者ID
     * @param eventType 事件类型（如：audit_pass, audit_reject等）
     * @param data JSON格式的消息数据
     */
    public void pushNotificationToAuthor(Long authorId, String eventType, String data) {
        try {
            Boolean success = sseNotificationFeign.pushToAuthor(authorId, eventType, data);
            if (Boolean.TRUE.equals(success)) {
                log.debug("SSE通知已推送给作者，authorId: {}, eventType: {}", authorId, eventType);
            } else {
                log.debug("作者未建立SSE连接，无法推送实时通知，authorId: {}", authorId);
            }
        } catch (Exception e) {
            log.error("推送SSE通知失败，authorId: {}, eventType: {}", authorId, eventType, e);
            // 不抛出异常，避免影响主流程
        }
    }

}
