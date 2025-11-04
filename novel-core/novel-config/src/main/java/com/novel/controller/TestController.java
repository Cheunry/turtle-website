package com.novel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 在任意服务模块中创建测试接口
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/status")
    public String status() {
        return "Service is running! " ;
    }
}
