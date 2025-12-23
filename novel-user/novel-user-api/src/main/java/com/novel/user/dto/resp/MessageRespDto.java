package com.novel.user.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRespDto {

    @Schema(description = "消息关联ID (用于标记已读/删除)")
    private Long id;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型 (0:系统公告, 1:订阅更新, 2:作家助手)")
    private Integer type;

    @Schema(description = "跳转链接")
    private String link;

    @Schema(description = "扩展数据 (JSON String)")
    private String extension;

    @Schema(description = "业务类型 (如：BOOK_AUDIT, CHAPTER_AUDIT, BOOK_COVER等)")
    private String busType;

    @Schema(description = "是否已读 (0:未读, 1:已读)")
    @com.fasterxml.jackson.annotation.JsonProperty("isRead")
    private Integer isRead;

    @Schema(description = "发送时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
