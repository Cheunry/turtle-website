package com.novel.user.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
/**
 * 作者积分扣除请求 DTO
 */
@Data
@Builder
public class AuthorPointsConsumeReqDto {

    @Schema(description = "作者ID（若为空，必须提供userId）")
    private Long authorId;
    
    @Schema(description = "用户ID（当authorId为空时使用）")
    private Long userId;

    @Schema(description = "消费类型;0-AI审核 1-AI润色 2-AI封面")
    @NotNull(message = "消费类型不能为空")
    private Integer consumeType;

    @Schema(description = "消费点数")
    @NotNull(message = "消费点数不能为空")
    private Integer consumePoints;

    @Schema(description = "关联ID（如：章节ID、小说ID等）")
    private Long relatedId;

    @Schema(description = "关联描述（如：章节名、小说名等）")
    private String relatedDesc;

    /* ***************** AI 服务扩展字段 ***************** */

    @Schema(description = "文本内容（用于审核、润色）")
    private String content;

    @Schema(description = "标题（章节名等）")
    private String title;

    @Schema(description = "小说名")
    private String bookName;

    @Schema(description = "小说简介")
    private String bookDesc;

    @Schema(description = "章节号")
    private Integer chapterNum;

    @Schema(description = "分类名（用于封面生成）")
    private String categoryName;

    /**
     * 若已调用「AI 封面提示词」接口拿到完整提示词，可在正式生图时回传，避免 user 服务再次 Feign 调 AI 重复生成提示词（省一次 LLM）。
     * 须与该接口返回字符串一致（含 AI 服务拼接的书装后缀）。
     */
    @Schema(description = "封面生图完整提示词（可选；与 ai/cover-prompt 返回一致时可传，跳过一次大模型）")
    private String coverPrompt;

    @Schema(description = "润色风格")
    private String style;

    @Schema(description = "润色要求")
    private String requirement;

    /* ***************** 内部使用字段（用于回滚） ***************** */
    
    @Schema(description = "已使用的免费积分（内部使用，用于精确回滚）")
    private Integer usedFreePoints;
    
    @Schema(description = "已使用的付费积分（内部使用，用于精确回滚）")
    private Integer usedPaidPoints;
}

