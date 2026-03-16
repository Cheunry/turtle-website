-- 原子性地增加书籍访问量
-- KEYS[1]: ZSet key (访问排行榜)
-- KEYS[2]: Hash key (访问量缓冲)
-- KEYS[3]: 书籍详情 Hash key (book_info:{bookId})
-- ARGV[1]: bookId (书籍ID)
-- ARGV[2]: increment (增量，默认为1)

local zsetKey = KEYS[1]
local hashKey = KEYS[2]
local bookInfoHashKey = KEYS[3]
local bookId = ARGV[1]
local increment = tonumber(ARGV[2]) or 1

-- 增加 ZSet 中的分数（访问排行榜）
local zsetScore = redis.call('ZINCRBY', zsetKey, increment, bookId)

-- 增加 Hash 中的值（访问量缓冲）
local hashValue = redis.call('HINCRBY', hashKey, bookId, increment)

-- 如果书籍详情 Hash 存在，同步更新其中的 visitCount 字段（使用增量操作，保持总访问量）
if redis.call('EXISTS', bookInfoHashKey) == 1 then
    -- 使用 HINCRBY 增量更新，而不是覆盖（详情Hash存储的是总访问量，需要累加）
    redis.call('HINCRBY', bookInfoHashKey, 'visitCount', increment)
end

-- 返回结果：ZSet分数和Hash值
return {zsetScore, hashValue}
