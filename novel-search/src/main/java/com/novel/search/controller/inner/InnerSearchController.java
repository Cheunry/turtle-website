package com.novel.search.controller.inner;

import com.novel.common.resp.RestResp;
import com.novel.search.dto.req.AuditExperienceSearchReqDto;
import com.novel.search.dto.resp.AuditExperienceSearchRespDto;
import com.novel.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "内部搜索接口")
@RestController
@RequestMapping("/api/inner/search")
@RequiredArgsConstructor
public class InnerSearchController {

    private final SearchService searchService;

    @Operation(summary = "搜索相似审核经验")
    @PostMapping("/audit-experience")
    public RestResp<List<AuditExperienceSearchRespDto>> searchAuditExperience(@RequestBody AuditExperienceSearchReqDto reqDto) {
        return searchService.searchAuditExperience(reqDto);
    }
}
