package com.novel.user.manager.redis;

public class RedisKeyConstants {

    /**
     * 登录次数限制配置（连续n此输错密码锁定账户一段时间）
     */
    // 用户登录失败次数 key: login_fail_count:{username}
    public static final String LOGIN_FAIL_COUNT = "login_fail_count:%s";

    // 用户锁定状态 key: user_lock:{username}
    public static final String USER_LOCK_STATUS = "user_lock:%s";

    // 过期时间：30分钟
    public static final long LOCK_EXPIRE_TIME = 30 * 60;


}
