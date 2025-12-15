package com.novel.common.auth;

import lombok.experimental.UtilityClass;

/**
 * 用户信息 持有类
 */
@UtilityClass
public class UserHolder {

    /**
     * 当前线程用户ID
     */
    private static final ThreadLocal<Long> userIdTL = new ThreadLocal<>();

    /**
     * 当前线程作家ID
     */
    private static final ThreadLocal<Long> authorIdTL = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userIdTL.set(userId);
    }

    public static Long getUserId() {
        return userIdTL.get();
    }

    public static void setAuthorId(Long authorId) {
        authorIdTL.set(authorId);
    }

    public static Long getAuthorId() {
        return authorIdTL.get();
    }

    public static void clear() {
        userIdTL.remove();
        authorIdTL.remove();
    }

}
