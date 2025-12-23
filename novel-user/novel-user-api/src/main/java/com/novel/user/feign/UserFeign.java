package com.novel.user.feign;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.dto.req.MessageSendReqDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;
import java.util.List;

@Component
@FeignClient(value = "novel-user-service", fallback = UserFeign.UserFeignFallback.class)
public interface UserFeign {

    /**
     * 批量查询用户信息
     */
    @PostMapping(ApiRouterConsts.API_INNER_USER_URL_PREFIX + "/listUserInfoByIds")
    RestResp<List<UserInfoRespDto>> listUserInfoByIds(@RequestBody List<Long> userIds);

    /**
     * 发送消息
     */
    @PostMapping(ApiRouterConsts.API_INNER_USER_URL_PREFIX + "/sendMessage")
    RestResp<Void> sendMessage(@RequestBody MessageSendReqDto dto);


    @Component
    class UserFeignFallback implements UserFeign {

        @Override
        public RestResp<List<UserInfoRespDto>> listUserInfoByIds(List<Long> userIds) {

            return RestResp.ok(new ArrayList<>(0));

        }

        @Override
        public RestResp<Void> sendMessage(MessageSendReqDto dto) {
            return RestResp.fail(com.novel.common.constant.ErrorCodeEnum.SYSTEM_ERROR);
        }


    }

}