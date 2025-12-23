package com.novel.user.feign;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 作家微服务 Feign 客户端
 */
@FeignClient(value = "novel-user-service", contextId = "authorFeign")
public interface AuthorFeign {

    /**
     * 扣除作者积分
     */
    @PostMapping(ApiRouterConsts.API_INNER_AUTHOR_URL_PREFIX + "/points/deduct")
    RestResp<Void> deductPoints(@RequestBody AuthorPointsConsumeReqDto dto);

}

