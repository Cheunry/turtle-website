package com.novel.user.dto.req;

import lombok.Data;

@Data
public class UserInfoUptReqDto {
    private Long userId;
    private String nickName;
    private String userPhoto;
    private Integer userSex;
}
