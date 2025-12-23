package com.novel.user.dto.req;

import com.novel.common.req.PageReqDto;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息分页请求 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessagePageReqDto extends PageReqDto {

    /**
     * 消息类型 (0:系统公告, 1:订阅更新, 2:作家助手/审核, 3:私信)，null则查全部
     */
    @Parameter(description = "消息类型 (0:系统公告, 1:订阅更新, 2:作家助手/审核, 3:私信)，null则查全部")
    private Integer messageType;

    /**
     * 业务类型，用于在相同消息类型下进一步筛选（如：BOOK_AUDIT, CHAPTER_AUDIT, BOOK_COVER等），null则查全部
     */
    @Parameter(description = "业务类型，用于在相同消息类型下进一步筛选（如：BOOK_AUDIT, CHAPTER_AUDIT, BOOK_COVER等），null则查全部")
    private String busType;

    /**
     * 接收者身份类型 (1:普通用户/UserID, 2:作者/AuthorID)，null则根据authorId自动判断
     */
    @Parameter(description = "接收者身份类型 (1:普通用户/UserID, 2:作者/AuthorID)，null则根据authorId自动判断")
    private Integer receiverType;

}

