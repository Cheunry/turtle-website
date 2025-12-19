package com.novel.ai.controller.front;

import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.ai.service.ImageService;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.novel.ai.service.TextService;
import com.novel.common.constant.ApiRouterConsts;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "前台AI接口")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_AI_URL_PREFIX)
@RequiredArgsConstructor
public class FrontAiController {

    private final TextService textService;
    private final ImageService imageService;

    @Operation(summary = "文本润色")
    @PostMapping("polish")
    public RestResp<TextPolishRespDto> polishText(@Valid @RequestBody TextPolishReqDto reqDto) {
        return textService.polishText(reqDto);
    }

    @Operation(summary = "生成小说封面提示词")
    @PostMapping("cover-prompt")
    public RestResp<String> generateCoverPrompt(@Valid @RequestBody BookCoverReqDto reqDto) {
        return textService.getBookCoverPrompt(reqDto);
    }

    @Operation(summary = "根据提示词生成图片")
    @PostMapping("generate-image")
    public RestResp<String> generateImage(
            @Parameter(description = "提示词") @RequestParam("prompt") String prompt) {
        return imageService.generateImage(prompt);
    }

}
