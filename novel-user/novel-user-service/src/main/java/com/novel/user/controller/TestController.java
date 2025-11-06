package com.novel.user.controller;

import com.novel.user.service.UserLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    UserLoginService userLoginService;

    public TestController() {
    }

    @RequestMapping("/login/name/{username}/{password}")
    public String testMessage(@PathVariable String username, @PathVariable String password) {
        String result = userLoginService.login(username, password);
        return result;
    }

    @RequestMapping("/login/dick/{usersex}/{userdick}")
    public String test(@PathVariable String usersex, @PathVariable String userdick) {
        return "usersex:" + usersex + ", userdick:" + userdick;
    }

    @RequestMapping("/pay/wechat")
    public String fuckMe() {
        return "I'll fuck u!";
    }
}
