package com.novel.user.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class MessageRespDto {

    @Schema(description = "消息关联ID (用于标记已读/删除)")
    private Long id;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "扩展数据 (JSON)")
    private Map<String, Object> extension;

    @Schema(description = "是否已读 (0:未读, 1:已读)")
    private Integer isRead;

    @Schema(description = "发送时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}

