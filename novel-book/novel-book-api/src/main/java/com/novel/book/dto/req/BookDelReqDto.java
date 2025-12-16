package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class BookDelReqDto {

    @Schema(description = "小说ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long bookId;

    @Schema(description = "作家ID")
    private Long authorId;
}
