package com.novel.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthorInfoDto {
    private Long id;

    private String penName;

    private Integer status;

    private Integer freePoints;

    private Integer paidPoints;
}
