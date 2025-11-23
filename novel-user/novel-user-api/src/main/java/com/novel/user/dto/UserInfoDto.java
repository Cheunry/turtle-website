package com.novel.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;

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
