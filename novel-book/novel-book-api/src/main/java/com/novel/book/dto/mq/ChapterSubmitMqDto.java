package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 章节提交 MQ 消息 DTO（作者提交章节更新/创建请求）
 * 用于将章节更新操作完全异步化，网关只需发送此消息即可立即返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterSubmitMqDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 书籍ID
     */
    private Long bookId;

    /**
     * 作者ID（用于权限校验）
     */
    private Long authorId;

    /**
     * 章节ID（更新时使用，新增时为null）
     */
    private Long chapterId;

    /**
     * 旧章节号（用于定位原章节，更新时使用）
     */
    private Integer oldChapterNum;

    /**
     * 新章节号
     */
    private Integer chapterNum;

    /**
     * 章节名
     */
    private String chapterName;

    /**
     * 章节内容
     */
    private String content;

    /**
     * 是否收费;1-收费 0-免费
     */
    private Integer isVip;

    /**
     * 操作类型：CREATE-新增, UPDATE-更新
     */
    private String operationType;

    /**
     * 是否开启审核
     */
    private Boolean auditEnable;

}

