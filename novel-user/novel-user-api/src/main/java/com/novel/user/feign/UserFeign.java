package com.novel.user.feign;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.UserInfoRespDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.List;

@Component
@FeignClient(value = "novel-user-service", fallback = UserFeign.UserFeignFallback.class)
public interface UserFeign {

    /**
     * 批量查询用户信息
     */
    @PostMapping(ApiRouterConsts.API_INNER_USER_URL_PREFIX + "/listUserInfoByIds")
    RestResp<List<UserInfoRespDto>> listUserInfoByIds(List<Long> userIds);

    @Component
    class UserFeignFallback implements UserFeign {

        @Override
        public RestResp<List<UserInfoRespDto>> listUserInfoByIds(List<Long> userIds) {

            return RestResp.ok(new ArrayList<>(0));

        }
    }

}