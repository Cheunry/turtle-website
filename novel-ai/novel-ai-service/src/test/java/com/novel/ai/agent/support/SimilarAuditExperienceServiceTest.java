package com.novel.ai.agent.support;

import com.novel.ai.dto.req.AuditExperienceSearchReqDto;
import com.novel.ai.dto.resp.AuditExperienceSearchRespDto;
import com.novel.ai.feign.SearchFeign;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimilarAuditExperienceServiceTest {

    private final SearchFeign searchFeign = mock(SearchFeign.class);
    private final SimilarAuditExperienceService service = new SimilarAuditExperienceService(searchFeign);

    @Test
    void returns_empty_when_content_blank() {
        assertThat(service.retrieve(null)).isEmpty();
        assertThat(service.retrieve("")).isEmpty();
        assertThat(service.retrieve("   ")).isEmpty();
    }

    @Test
    void returns_empty_when_feign_returns_null_response() {
        when(searchFeign.searchAuditExperience(any())).thenReturn(null);
        assertThat(service.retrieve("some content")).isEmpty();
    }

    @Test
    void returns_empty_when_feign_returns_non_ok() {
        RestResp<List<AuditExperienceSearchRespDto>> resp = RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "err");
        when(searchFeign.searchAuditExperience(any())).thenReturn(resp);
        assertThat(service.retrieve("some content")).isEmpty();
    }

    @Test
    void returns_empty_when_feign_throws() {
        when(searchFeign.searchAuditExperience(any())).thenThrow(new RuntimeException("boom"));
        assertThat(service.retrieve("some content")).isEmpty();
    }

    @Test
    void formats_experiences_into_prompt_friendly_text() {
        AuditExperienceSearchRespDto exp = new AuditExperienceSearchRespDto();
        exp.setKeySnippet("雷人段子");
        exp.setViolationLabel("低俗");
        exp.setAuditRule("禁止低俗表达");
        exp.setAuditStatus(2);

        when(searchFeign.searchAuditExperience(any())).thenReturn(RestResp.ok(List.of(exp)));

        String text = service.retrieve("待检索文本");

        assertThat(text)
                .contains("判例1：")
                .contains("核心争议片段：雷人段子")
                .contains("违规标签：低俗")
                .contains("判例规则总结：禁止低俗表达")
                .contains("历史审核结果：不通过");
    }

    @Test
    void passes_expected_req_params_to_feign() {
        when(searchFeign.searchAuditExperience(any())).thenReturn(RestResp.ok(List.of()));
        service.retrieve("foo");

        ArgumentCaptor<AuditExperienceSearchReqDto> captor =
                ArgumentCaptor.forClass(AuditExperienceSearchReqDto.class);
        org.mockito.Mockito.verify(searchFeign).searchAuditExperience(captor.capture());
        AuditExperienceSearchReqDto req = captor.getValue();
        assertThat(req.getContentText()).isEqualTo("foo");
        assertThat(req.getTopK()).isEqualTo(3);
        assertThat(req.getSimilarityThreshold()).isEqualTo(0.75);
    }
}
