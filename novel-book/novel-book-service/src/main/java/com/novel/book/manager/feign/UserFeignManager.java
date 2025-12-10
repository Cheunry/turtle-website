package com.novel.book.manager.feign;

import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.feign.UserFeign;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@AllArgsConstructor
public class UserFeignManager {

    private final UserFeign userFeign;

    public List<UserInfoRespDto> listUserInfoByIds(List<Long> userIds) {

        RestResp<List<UserInfoRespDto>> resp = userFeign.listUserInfoByIds(userIds);
        if (Objects.equals(ErrorCodeEnum.OK.getCode(), resp.getCode())) {
            return resp.getData();
        }
        return new ArrayList<>(0);
    }
}
