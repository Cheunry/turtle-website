package com.novel.resource.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
// 移除 @RequiredArgsConstructor，除非 WebConfig 还有其他需要注入的 final 字段
// 如果没有其他 final 字段，你可以保留，但需要移除 fileInterceptor 的 final 关键字
// 最安全的做法是：直接移除 FileInterceptor 字段
public class WebConfig implements WebMvcConfigurer {

    // ----------------------------------------------------------------------
    // 必须删除：这一行是导致 UnsatisfiedDependencyException 的直接原因
    // private final FileInterceptor fileInterceptor;
    // ----------------------------------------------------------------------

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // ----------------------------------------------------------------------
        // 必须删除：移除对本地文件访问的拦截器注册
        // registry.addInterceptor(fileInterceptor)
        //         .addPathPatterns(SystemConfigConsts.IMAGE_UPLOAD_DIRECTORY + "**")
        //         .order(1);
        // ----------------------------------------------------------------------

        // 如果你的项目有其他拦截器，可以在这里继续注册它们
    }

}