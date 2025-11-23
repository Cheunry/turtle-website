package com.novel.user.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoRespDto {

    private Long id;

    private String username;

    /**
     * 昵称
     * */
    @Schema(description = "昵称")
    private String nickName;

    /**
     * 用户头像
     * */
    @Schema(description = "用户头像")
    private String userPhoto;

    /**
     * 用户性别
     * */
    @Schema(description = "用户性别")
    private Integer userSex;
}
