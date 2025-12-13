package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class BookDelReqDto implements Serializable {

    @Schema(description = "小说ID", required = true)
    @NotNull
    private Long bookId;

    @Schema(description = "作家ID")
    private Long authorId;
}
