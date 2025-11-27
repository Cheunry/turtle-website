package com.novel.author.config;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.config.interceptor.TokenParseInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final TokenParseInterceptor tokenParseInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 权限认证拦截
        registry.addInterceptor(authInterceptor)
                // 拦截作家后台相关请求接口
                .addPathPatterns(
                        ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/**")

                // 放行注册相关请求接口
                .excludePathPatterns(ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/register",
                        ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/status")

                .order(2);

        // Token 解析
        registry.addInterceptor(tokenParseInterceptor)

                .addPathPatterns(
                        ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/register", ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/status")

                .order(3);

    }

}
