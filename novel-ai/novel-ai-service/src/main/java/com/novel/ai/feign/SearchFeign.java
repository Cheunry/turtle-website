package com.novel.ai.feign;

import com.novel.ai.dto.req.AuditExperienceSearchReqDto;
import com.novel.ai.dto.resp.AuditExperienceSearchRespDto;
import com.novel.common.resp.RestResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "novel-search-service", fallbackFactory = SearchFeignFallbackFactory.class)
public interface SearchFeign {

    @PostMapping("/api/inner/search/audit-experience")
    RestResp<List<AuditExperienceSearchRespDto>> searchAuditExperience(@RequestBody AuditExperienceSearchReqDto reqDto);
}
