package com.novel.common.constant;

/**
 * 缓存相关常量
 */
public class CacheConsts {

    /**
     * 本项目 Redis 缓存前缀
     */
    public static final String REDIS_CACHE_PREFIX = "Cache::Novel::";


    /**
     * Caffeine 缓存管理器
     */
    public static final String CAFFEINE_CACHE_MANAGER = "caffeineCacheManager";

    /**
     * Redis 缓存管理器1
     */
    public static final String REDIS_CACHE_MANAGER_PLAIN = "plainJsonCacheManager";

    /**
     * Redis 缓存管理器2
     */
    public static final String REDIS_CACHE_MANAGER_TYPED = "typedJsonCacheManager";



    /**
     * 首页小说推荐缓存
     */
    public static final String HOME_BOOK_CACHE_NAME = "homeBookCache";

    /**
     * 最新新闻缓存
     */
    public static final String LATEST_NEWS_CACHE_NAME = "latestNewsCache";

    /**
     * 小说点击榜缓存
     */
    public static final String BOOK_VISIT_RANK_CACHE_NAME = "bookVisitRankCache";

    /**
     * 小说新书榜缓存
     */
    public static final String BOOK_NEWEST_RANK_CACHE_NAME = "bookNewestRankCache";

    /**
     * 小说更新榜缓存
     */
    public static final String BOOK_UPDATE_RANK_CACHE_NAME = "bookUpdateRankCache";

    /**
     * 首页友情链接缓存
     */
    public static final String HOME_FRIEND_LINK_CACHE_NAME = "homeFriendLinkCache";

    /**
     * 小说分类列表缓存
     */
    public static final String BOOK_CATEGORY_LIST_CACHE_NAME = "bookCategoryListCache";

    /**
     * 小说信息缓存
     */
    public static final String BOOK_INFO_CACHE_NAME = "bookInfoCache";

    /**
     * 小说章节缓存
     */
    public static final String BOOK_CHAPTER_CACHE_NAME = "bookChapterCache";

    /**
     * 小说内容缓存
     */
    public static final String BOOK_CONTENT_CACHE_NAME = "bookContentCache";

    /**
     * 最近更新小说ID列表缓存
     */
    public static final String LAST_UPDATE_BOOK_ID_LIST_CACHE_NAME = "lastUpdateBookIdListCache";

    /**
     * 图片验证码缓存 KEY
     */
    public static final String IMG_VERIFY_CODE_CACHE_KEY =
        REDIS_CACHE_PREFIX + "imgVerifyCodeCache::";

    /**
     * 用户登录次数限制配置
     */
    // 用户登录失败次数 key: login_fail_count:{username}
    public static final String LOGIN_FAIL_COUNT = "login_fail_count:%s";

    // 用户锁定状态 key: user_lock:{username}
    public static final String USER_LOCK_STATUS = "user_lock:%s";

    // 过期时间：30分钟
    public static final long LOCK_EXPIRE_TIME = 30 * 60;

    /**
     * 用户信息缓存
     */
    public static final String USER_INFO_CACHE_NAME = "userInfoCache";

    /**
     * 作家信息缓存
     */
    public static final String AUTHOR_INFO_CACHE_NAME = "authorInfoCache";

    /**
     * 作者积分 Redis Key 前缀
     */
    public static final String AUTHOR_POINTS_PREFIX = REDIS_CACHE_PREFIX + "author:points:";

    /**
     * 作者免费积分 Redis Key 模板: Cache::Novel::author:points:free:{authorId}
     */
    public static final String AUTHOR_FREE_POINTS_KEY = AUTHOR_POINTS_PREFIX + "free:%s";

    /**
     * 作者付费积分 Redis Key 模板: Cache::Novel::author:points:paid:{authorId}
     */
    public static final String AUTHOR_PAID_POINTS_KEY = AUTHOR_POINTS_PREFIX + "paid:%s";

    /**
     * 作者免费积分重置标记 Redis Key 模板: Cache::Novel::author:points:reset:{authorId}:{date}
     */
    public static final String AUTHOR_FREE_POINTS_RESET_KEY = AUTHOR_POINTS_PREFIX + "reset:%s:%s";

    /**
     * 作者积分消费幂等性控制 Redis Key 模板: Cache::Novel::author:points:deduct:idempotent:{authorId}:{consumeType}:{relatedId}:{timestamp}
     */
    public static final String AUTHOR_POINTS_DEDUCT_IDEMPOTENT_KEY = AUTHOR_POINTS_PREFIX + "deduct:idempotent:%s:%s:%s:%s";

    /**
     * 小说点击榜 ZSet 缓存 Key
     */
    public static final String BOOK_VISIT_RANK_ZSET = REDIS_CACHE_PREFIX + "visit_rank";

    /**
     * 小说信息 Hash 缓存 Key 前缀
     */
    public static final String BOOK_INFO_HASH_PREFIX = REDIS_CACHE_PREFIX + "book_info:";

    /**
     * 小说点击量缓冲 Hash Key (用于批量更新 DB)
     */
    public static final String BOOK_VISIT_COUNT_HASH = REDIS_CACHE_PREFIX + "book_visit_buffer";

    /**
     * 缓存配置常量
     */
    public enum CacheEnum {

        HOME_BOOK_CACHE(0, HOME_BOOK_CACHE_NAME, 60 * 60 * 24, 1),

        LATEST_NEWS_CACHE(0, LATEST_NEWS_CACHE_NAME, 60 * 10, 1),

        BOOK_VISIT_RANK_CACHE(2, BOOK_VISIT_RANK_CACHE_NAME, 60 * 60 * 6, 1),

        BOOK_NEWEST_RANK_CACHE(0, BOOK_NEWEST_RANK_CACHE_NAME, 60 * 30, 1),

        BOOK_UPDATE_RANK_CACHE(0, BOOK_UPDATE_RANK_CACHE_NAME, 60, 1),

        HOME_FRIEND_LINK_CACHE(2, HOME_FRIEND_LINK_CACHE_NAME, 0, 1),

        BOOK_CATEGORY_LIST_CACHE(2, BOOK_CATEGORY_LIST_CACHE_NAME, 0, 2),

        BOOK_INFO_CACHE(0, BOOK_INFO_CACHE_NAME, 60 * 60 * 18, 500),

        BOOK_CHAPTER_CACHE(0, BOOK_CHAPTER_CACHE_NAME, 60 * 60 * 6, 5000),

        BOOK_CONTENT_CACHE(2, BOOK_CONTENT_CACHE_NAME, 60 * 60 * 12, 3000),

        LAST_UPDATE_BOOK_ID_LIST_CACHE(0, LAST_UPDATE_BOOK_ID_LIST_CACHE_NAME, 60 * 60, 10),

        USER_INFO_CACHE(2, USER_INFO_CACHE_NAME, 60 * 60 * 24, 10000),

        AUTHOR_INFO_CACHE(2, AUTHOR_INFO_CACHE_NAME, 60 * 60 * 48, 1000);

        /**
         * 缓存类型 0-本地 1-本地和远程 2-远程
         */
        private int type;
        /**
         * 缓存的名字
         */
        private String name;
        /**
         * 失效时间（秒） 0-永不失效
         */
        private int ttl;
        /**
         * 最大容量
         */
        private int maxSize;

        CacheEnum(int type, String name, int ttl, int maxSize) {
            this.type = type;
            this.name = name;
            this.ttl = ttl;
            this.maxSize = maxSize;
        }

        public boolean isLocal() {
            return type <= 1;
        }

        public boolean isRemote() {
            return type >= 1;
        }

        public String getName() {
            return name;
        }

        public int getTtl() {
            return ttl;
        }

        public int getMaxSize() {
            return maxSize;
        }

    }

}
