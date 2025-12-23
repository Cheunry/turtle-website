package com.novel.user.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendReqDto {

    @Schema(description = "接收者ID")
    private Long receiverId;

    @Schema(description = "接收者身份类型 (1:普通用户/UserID, 2:作者/AuthorID)")
    private Integer receiverType;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型 (0:系统公告, 1:订阅更新, 2:作家助手/审核, 3:私信)")
    private Integer type;

    @Schema(description = "跳转链接")
    private String link;

    @Schema(description = "业务ID")
    private Long busId;

    @Schema(description = "业务类型")
    private String busType;

    @Schema(description = "扩展数据")
    private String extension;
}

