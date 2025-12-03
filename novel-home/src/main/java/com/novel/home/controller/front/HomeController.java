package com.novel.home.controller.front;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import com.novel.home.dto.resp.HomeBookRespDto;
import com.novel.home.dto.resp.HomeFriendLinkRespDto;
import com.novel.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@Tag(name = "HomeController", description = "前台门户-首页模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_HOME_URL_PREFIX)
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 首页小说展示查询接口
     */
    @Operation(summary = "首页小说展示查询接口")
    @GetMapping("books")
    public RestResp<List<HomeBookRespDto>> listHomeBooks(){
        return homeService.listHomeBook();
    }

    /**
     * 首页友情链接列表查询接口
     */
    @Operation(summary = "首页友情链接列表查询接口")
    @GetMapping("friend_Link/list")
    public RestResp<List<HomeFriendLinkRespDto>>  listHomeFriendLinks(){
        return homeService.listHomeFriendLink();
    }


}
