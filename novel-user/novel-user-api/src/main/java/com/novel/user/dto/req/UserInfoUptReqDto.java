package com.novel.user.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
// 恢复 Pattern 引入
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;

@Data
@Builder
public class UserInfoUptReqDto {

    private Long userId;

    @Schema(description = "手机号/用户名")
    // 恢复手机号正则校验
    @Pattern(regexp="^1[3|4|5|6|7|8|9][0-9]{9}$",message="手机号格式不正确！")
    private String username;

    @Schema(description = "昵称")
    @Length(min = 2,max = 10)
    private String nickName;

    @Schema(description = "头像地址")
    private String userPhoto;

    @Schema(description = "性别")
    @Min(value = 0)
    @Max(value = 1)
    private Integer userSex;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
