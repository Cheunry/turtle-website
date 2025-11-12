package com.novel.user.service;

import org.springframework.stereotype.Service;

@Service
public class UserLoginService {
    public UserLoginService() {

    }

    public  String login(String username, String password) {
        if(username == null || password == null) {
            return "fuck u";
        } else if(username == "pie") {
            return "pie fuck me";
        }
        return "你不是小饼，不能提供服务";
    }
}
