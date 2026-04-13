-- 原子性地增加书籍访问量（带窗口去重）
-- KEYS[1]: ZSet key (访问排行榜)
-- KEYS[2]: Hash key (访问量缓冲)
-- KEYS[3]: 书籍详情 Hash key (book_info:{bookId})
-- KEYS[4]: UV 去重 Set key (book_visit_uv:{bookId}:{bucket})
-- ARGV[1]: bookId (书籍ID)
-- ARGV[2]: userIdentity (用户标识，优先 userId，匿名时可为ip/device)
-- ARGV[3]: uvSetTtlSeconds (UV去重集合TTL秒数)

local zsetKey = KEYS[1]
local hashKey = KEYS[2]
local bookInfoHashKey = KEYS[3]
local uvSetKey = KEYS[4]
local bookId = ARGV[1]
local userIdentity = ARGV[2]
local uvSetTtlSeconds = tonumber(ARGV[3]) or 300

-- 去重：只有当前窗口内首次访问才会被统计
local isNewVisitor = redis.call('SADD', uvSetKey, userIdentity)

-- 窗口key设置TTL，依赖自然过期避免大key清理抖动
redis.call('EXPIRE', uvSetKey, uvSetTtlSeconds)

if isNewVisitor == 1 then
    -- 增加 ZSet 中的分数（访问排行榜）
    local zsetScore = redis.call('ZINCRBY', zsetKey, 1, bookId)

    -- 增加 Hash 中的值（访问量缓冲）
    local hashValue = redis.call('HINCRBY', hashKey, bookId, 1)

    -- 如果书籍详情 Hash 存在，同步更新其中的 visitCount 字段（使用增量操作，保持总访问量）
    if redis.call('EXISTS', bookInfoHashKey) == 1 then
        redis.call('HINCRBY', bookInfoHashKey, 'visitCount', 1)
    end

    -- 返回结果：是否计数、ZSet分数、Hash值
    return {1, zsetScore, hashValue}
end
-- 重复访问未计数，返回标记和占位值
return {0, -1, -1}
