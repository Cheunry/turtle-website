package com.novel.user.service;

import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.common.resp.RestResp;
import org.springframework.stereotype.Service;

@Service
public interface AuthorInfoService {

    RestResp<Void> authorRegister(AuthorRegisterReqDto dto);

    RestResp<AuthorInfoDto> getStatus(Long userId);

    /**
     * 扣除作者积分
     * @param dto 扣分请求DTO
     * @return Void
     */
    RestResp<Void> deductPoints(AuthorPointsConsumeReqDto dto);

    /**
     * 回滚作者积分（补偿机制）
     * 当 AI 服务调用失败时，将已扣除的积分加回去
     * @param dto 扣分请求DTO（包含需要回滚的积分信息）
     * @return Void
     */
    RestResp<Void> rollbackPoints(AuthorPointsConsumeReqDto dto);

}

