# 作者点数系统数据库设计说明

## 一、系统概述

点数系统用于限制作家对AI工具的使用，包括：
- **AI审核**：1点/次（轻量级，几乎瞬间完成）
- **AI润色*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               *：10点/次（中量级，涉及上下文分析）
- **AI封面**：100点/次（重量级，消耗GPU资源且时间长）

## 二、点数类型

### 1. 免费点数
- 每天每个作者免费获得 **500点**
- **当天用不完的免费积分不保留到第二天**
- 每天0点重置

### 2. 永久积分
- 通过充值获得
- **永久有效，不会过期**
- 可以累积使用

## 三、数据库表设计

### 1. `author_info` 表扩展字段
在现有的`author_info`表中添加点数字段，简化设计。

**新增字段说明：**
- `free_points`: 免费积分（每天重置为500点，默认值500）
- `paid_points`: 付费积分（永久积分，充值获得，默认值0）
- `free_points_update_time`: 免费积分更新时间（date类型，用于判断是否需要重置）

**设计优势：**
- 简化表结构，减少JOIN查询
- 点数信息与作者基本信息集中管理
- 查询效率更高

### 2. `author_points_consume_log` - 作者点数消费记录表
记录所有点数消费记录，用于统计和审计。

**字段说明：**
- `id`: 主键
- `author_id`: 作者ID
- `consume_type`: 消费类型（0-AI审核，1-AI润色，2-AI封面）
- `consume_points`: 消费点数
- `points_type`: 使用的点数类型（0-免费点数，1-永久积分）
- `related_id`: 关联ID（如：章节ID、小说ID等）
- `related_desc`: 关联描述（如：章节名、小说名等）
- `consume_date`: 消费日期（用于统计和查询）
- `create_time`: 创建时间
- `update_time`: 更新时间

**索引：**
- 普通索引：`idx_authorId`、`idx_consumeType`、`idx_consumeDate`、`idx_authorId_consumeDate`

**消费类型枚举：**
- `0`: AI审核（1点/次）
- `1`: AI润色（10点/次）
- `2`: AI封面（100点/次）

### 3. `author_points_recharge_log` - 作者点数充值记录表
记录所有充值记录。

**字段说明：**
- `id`: 主键
- `author_id`: 作者ID
- `recharge_amount`: 充值金额（单位：分）
- `recharge_points`: 充值获得的永久积分
- `pay_channel`: 充值方式（0-支付宝，1-微信）
- `out_trade_no`: 商户订单号（唯一）
- `trade_no`: 第三方交易号（支付宝/微信交易号）
- `recharge_status`: 充值状态（0-待支付，1-支付成功，2-支付失败）
- `recharge_time`: 充值完成时间
- `create_time`: 创建时间
- `update_time`: 更新时间

**索引：**
- 唯一索引：`uk_outTradeNo` - 确保订单号唯一
- 普通索引：`idx_authorId`、`idx_rechargeStatus`、`idx_rechargeTime`

## 四、业务逻辑说明

### 1. 点数消费流程

1. **检查点数是否足够**
   - 优先使用当天的免费点数
   - 如果免费点数不足，使用永久积分
   - 如果两者都不足，返回错误

2. **扣除点数**
   - 检查并更新`author_info`表中的`free_points`或`paid_points`
   - 记录消费日志到`author_points_consume_log`

3. **消费优先级**
   - 优先使用免费点数
   - 免费点数不足时，使用永久积分

### 2. 每日免费点数重置

- 在每次使用点数前，检查`free_points_update_time`字段
- 如果`free_points_update_time`不是今天，则重置`free_points`为500，并更新`free_points_update_time`为今天
- 也可以通过定时任务（如XXL-Job）在每天0点批量重置所有作者的免费点数

### 3. 充值流程

1. 创建充值订单，记录到`author_points_recharge_log`（状态：待支付）
2. 调用支付接口（支付宝/微信）
3. 支付成功后，更新`author_points_recharge_log`（状态：支付成功）
4. 增加`author_info`表中的`paid_points`字段值

## 五、常用查询示例

### 1. 查询作者当前可用点数
```sql
-- 查询作者点数和更新时间
SELECT free_points, paid_points, free_points_update_time 
FROM author_info 
WHERE id = ?;

-- 如果需要判断是否需要重置免费点数
SELECT 
    free_points,
    paid_points,
    CASE 
        WHEN free_points_update_time != CURDATE() THEN 500 
        ELSE free_points 
    END as current_free_points
FROM author_info 
WHERE id = ?;
```

### 2. 查询作者点数消费统计
```sql
-- 按消费类型统计
SELECT 
    consume_type,
    SUM(consume_points) as total_points,
    COUNT(*) as consume_count
FROM author_points_consume_log
WHERE author_id = ? AND consume_date >= ?
GROUP BY consume_type;
```

### 3. 查询作者充值记录
```sql
SELECT * FROM author_points_recharge_log
WHERE author_id = ?
ORDER BY create_time DESC;
```

## 六、注意事项

1. **并发控制**：在扣除点数时，需要使用数据库事务和乐观锁/悲观锁，防止并发问题
2. **免费点数重置**：在每次使用点数前检查`free_points_update_time`，如果不是今天则重置为500点
3. **定时任务**：可选，可以设置定时任务每天0点批量重置所有作者的免费点数（更新`free_points`为500，`free_points_update_time`为当天）
4. **充值比例**：需要在业务代码中定义充值金额与永久积分的兑换比例
5. **退款处理**：如果支持退款，需要相应处理永久积分的退还

