package com.novel.user.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoRespDto {
    private Long userId;
    private String username;

    @Schema(description = "用户昵称")
    private String nickName;

    @Schema(description = "用户照片")
    private String userPhoto;

    @Schema(description = "用户性别")
    private Integer userSex;
}
