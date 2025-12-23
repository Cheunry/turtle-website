package com.novel.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 用户信息 DTO
 */

@Data
@Builder
public class UserInfoDto {

    private Long id;

    private String username;

    private String nickName;

    private String userPhoto;

    private Integer userSex;

    private Integer status;

    private Long userId;

}
