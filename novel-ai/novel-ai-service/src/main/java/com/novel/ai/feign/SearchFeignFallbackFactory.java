package com.novel.ai.feign;

import com.novel.ai.dto.req.AuditExperienceSearchReqDto;
import com.novel.ai.dto.resp.AuditExperienceSearchRespDto;
import com.novel.common.resp.RestResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SearchFeignFallbackFactory implements FallbackFactory<SearchFeign> {

    @Override
    public SearchFeign create(Throwable cause) {
        return new SearchFeign() {
            @Override
            public RestResp<List<AuditExperienceSearchRespDto>> searchAuditExperience(AuditExperienceSearchReqDto reqDto) {
                log.error("调用搜索服务查询相似审核经验失败", cause);
                return RestResp.ok(Collections.emptyList());
            }
        };
    }
}
