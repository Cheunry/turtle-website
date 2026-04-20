create table author_income
(
    id               bigint unsigned auto_increment comment '主键'
        primary key,
    author_id        bigint unsigned              not null comment '作家ID',
    book_id          bigint unsigned              not null comment '小说ID',
    income_month     date                         not null comment '收入月份',
    pre_tax_income   int unsigned     default '0' not null comment '税前收入;单位：分',
    after_tax_income int unsigned     default '0' not null comment '税后收入;单位：分',
    pay_status       tinyint unsigned default '0' not null comment '支付状态;0-待支付 1-已支付',
    confirm_status   tinyint unsigned default '0' not null comment '稿费确认状态;0-待确认 1-已确认',
    detail           varchar(255)                 null comment '详情',
    create_time      datetime                     null comment '创建时间',
    update_time      datetime                     null comment '更新时间',
    constraint pk_id
        unique (id)
)
    comment '稿费收入统计';

create table author_income_detail
(
    id             bigint unsigned auto_increment comment '主键'
        primary key,
    author_id      bigint unsigned             not null comment '作家ID',
    book_id        bigint unsigned default '0' not null comment '小说ID;0表示全部作品',
    income_date    date                        not null comment '收入日期',
    income_account int unsigned    default '0' not null comment '订阅总额',
    income_count   int unsigned    default '0' not null comment '订阅次数',
    income_number  int unsigned    default '0' not null comment '订阅人数',
    create_time    datetime                    null comment '创建时间',
    update_time    datetime                    null comment '更新时间',
    constraint pk_id
        unique (id)
)
    comment '稿费收入明细统计';

create table author_info
(
    id                      bigint unsigned auto_increment comment '主键'
        primary key,
    user_id                 bigint unsigned                not null comment '用户ID',
    pen_name                varchar(20)                    not null comment '笔名',
    tel_phone               varchar(20)                    null comment '手机号码',
    chat_account            varchar(50)                    null comment 'QQ或微信账号',
    email                   varchar(50)                    null comment '电子邮箱',
    work_direction          tinyint unsigned               null comment '作品方向;0-男频 1-女频',
    status                  tinyint unsigned default '0'   not null comment '0：正常;1-封禁',
    free_points             int unsigned     default '500' not null comment '免费积分（每天重置为500点）',
    paid_points             int unsigned     default '0'   not null comment '付费积分（永久积分，充值获得）',
    free_points_update_time date                           null comment '免费积分更新时间（用于判断是否需要重置）',
    create_time             datetime                       null comment '创建时间',
    update_time             datetime                       null comment '更新时间',
    constraint pk_id
        unique (id),
    constraint uk_userId
        unique (user_id)
)
    comment '作者信息';

create table author_points_consume_log
(
    id             bigint unsigned auto_increment comment '主键'
        primary key,
    author_id      bigint unsigned  not null comment '作者ID（关联author_info.id）',
    consume_type   tinyint unsigned not null comment '消费类型;0-AI审核 1-AI润色 2-AI封面',
    consume_points int unsigned     not null comment '消费点数',
    points_type    tinyint unsigned not null comment '使用的点数类型;0-免费点数 1-永久积分',
    related_id     bigint unsigned  null comment '关联ID（如：章节ID、小说ID等，根据消费类型不同而不同）',
    related_desc   varchar(255)     null comment '关联描述（如：章节名、小说名等）',
    consume_date   date             not null comment '消费日期（用于统计和查询）',
    create_time    datetime         null comment '创建时间',
    update_time    datetime         null comment '更新时间',
    idempotent_key varchar(64)      null comment '幂等性Key（MQ消费去重）',
    constraint pk_id
        unique (id),
    constraint uk_idempotentKey
        unique (idempotent_key)
)
    comment '作者点数消费记录表';

create index idx_authorId
    on author_points_consume_log (author_id);

create index idx_authorId_consumeDate
    on author_points_consume_log (author_id, consume_date);

create index idx_consumeDate
    on author_points_consume_log (consume_date);

create index idx_consumeType
    on author_points_consume_log (consume_type);

create table author_points_recharge_log
(
    id              bigint unsigned auto_increment comment '主键'
        primary key,
    author_id       bigint unsigned              not null comment '作者ID（关联author_info.id）',
    recharge_amount int unsigned                 not null comment '充值金额;单位：分',
    recharge_points int unsigned                 not null comment '充值获得的永久积分',
    pay_channel     tinyint unsigned default '1' not null comment '充值方式;0-支付宝 1-微信',
    out_trade_no    varchar(64)                  not null comment '商户订单号',
    trade_no        varchar(64)                  null comment '第三方交易号（支付宝/微信交易号）',
    recharge_status tinyint unsigned default '0' not null comment '充值状态;0-待支付 1-支付成功 2-支付失败',
    recharge_time   datetime                     null comment '充值完成时间',
    create_time     datetime                     null comment '创建时间',
    update_time     datetime                     null comment '更新时间',
    constraint pk_id
        unique (id),
    constraint uk_outTradeNo
        unique (out_trade_no)
)
    comment '作者点数充值记录表';

create index idx_authorId
    on author_points_recharge_log (author_id);

create index idx_rechargeStatus
    on author_points_recharge_log (recharge_status);

create index idx_rechargeTime
    on author_points_recharge_log (recharge_time);

create table book_category
(
    id             bigint unsigned auto_increment
        primary key,
    work_direction tinyint unsigned              not null comment '作品方向;0-男频 1-女频',
    name           varchar(20)                   not null comment '类别名',
    sort           tinyint unsigned default '10' not null comment '排序',
    create_time    datetime                      null comment '创建时间',
    update_time    datetime                      null comment '更新时间'
)
    comment '小说类别';

create table book_chapter
(
    id                     bigint unsigned auto_increment,
    book_id                bigint unsigned              not null comment '小说ID',
    chapter_num            smallint unsigned            not null comment '章节号',
    chapter_name           varchar(100)                 not null comment '章节名',
    word_count             int unsigned     default '0' not null comment '章节字数',
    content                mediumtext                   not null comment '小说章节内容',
    is_vip                 tinyint unsigned default '0' not null comment '是否收费;1-收费 0-免费',
    create_time            datetime                     null,
    update_time            datetime                     null,
    audit_status           tinyint unsigned default '0' not null comment '审核状态;0-待审核 1-审核通过 2-审核不通过',
    audit_reason           varchar(500)                 null comment '审核不通过原因',
    reject_sensitive_words varchar(500)                 null comment '审核不通过时命中违禁词（顿号拼接，本地AC拦截时有值）',
    primary key (book_id, chapter_num),
    constraint book_chapter_id_uindex
        unique (id)
)
    comment '小说章节';

create index book_chapter_audit
    on book_chapter (chapter_num, audit_status, book_id);

create table book_comment
(
    id              bigint unsigned auto_increment comment '主键'
        primary key,
    book_id         bigint unsigned              not null comment '评论小说ID',
    user_id         bigint unsigned              not null comment '评论用户ID',
    comment_content varchar(512)                 not null comment '评价内容',
    reply_count     int unsigned     default '0' not null comment '回复数量',
    audit_status    tinyint unsigned default '0' not null comment '审核状态;0-待审核 1-审核通过 2-审核不通过',
    create_time     datetime                     null comment '创建时间',
    update_time     datetime                     null comment '更新时间',
    constraint pk_id
        unique (id),
    constraint uk_bookId_userId
        unique (book_id asc, user_id asc, update_time desc)
)
    comment '小说评论';

create table book_info
(
    id                       bigint unsigned auto_increment comment '主键'
        primary key,
    work_direction           tinyint unsigned               null comment '作品方向;0-男频 1-女频',
    category_id              bigint unsigned                null comment '类别ID',
    category_name            varchar(50)                    null comment '类别名',
    pic_url                  varchar(200)                   not null comment '小说封面地址',
    book_name                varchar(50)                    not null comment '小说名',
    author_id                bigint unsigned                not null comment '作家id',
    author_name              varchar(50)                    not null comment '作家名',
    book_desc                varchar(2000)                  not null comment '书籍描述',
    score                    tinyint unsigned               not null comment '评分;总分:10 ，真实评分 = score/10',
    book_status              tinyint unsigned default '0'   not null comment '书籍状态;0-连载中 1-已完结',
    visit_count              bigint unsigned  default '103' not null comment '点击量',
    word_count               int unsigned     default '0'   not null comment '总字数',
    comment_count            int unsigned     default '0'   not null comment '评论数',
    last_chapter_name        varchar(50)                    null comment '最新章节名',
    last_chapter_update_time datetime                       null comment '最新章节更新时间',
    is_vip                   tinyint unsigned default '0'   not null comment '是否收费;1-收费 0-免费',
    create_time              datetime                       null comment '创建时间',
    update_time              datetime                       null comment '更新时间',
    last_chapter_num         smallint unsigned              null comment '最新章节号',
    audit_status             tinyint unsigned default '0'   not null comment '审核状态;0-待审核 1-审核通过 2-审核不通过',
    audit_reason             varchar(500)                   null comment '审核不通过原因',
    reject_sensitive_words   varchar(500)                   null comment '审核不通过时命中违禁词（顿号拼接，本地AC拦截时有值）',
    constraint uk_bookName_authorName
        unique (book_name, author_name)
)
    comment '小说信息';

create index idx_author_book
    on book_info (author_name asc, audit_status asc, create_time desc);

create index idx_createtime
    on book_info (audit_status asc, create_time desc, id asc, category_name asc, book_name asc, last_chapter_name asc,
                  author_name asc);

create index idx_updateTime
    on book_info (audit_status asc, update_time desc);

create table content_audit
(
    id              bigint unsigned auto_increment comment '顺序id'
        primary key,
    source_type     tinyint unsigned             not null comment '数据来源;0-小说基本信息表 1-小说章节表',
    source_id       bigint unsigned              not null comment '数据来源ID',
    content_text    mediumtext                   not null comment '内容文本',
    ai_confidence   decimal(5, 2)                null comment 'AI审核置信度;范围0-1，NULL表示未进行AI审核',
    audit_status    tinyint unsigned default '0' not null comment '审核状态;0-待审核 1-通过 2-不通过',
    is_human_final  tinyint(1)                   null comment '是否人工最终裁决;NULL-非人工最终裁决(或历史未标记),1-人工最终裁决',
    audit_reason    varchar(500)                 null comment '通过/不通过原因',
    violation_label varchar(100)                 null comment '争议/违规标签（由AI提炼）',
    key_snippet     text                         null comment '核心争议片段（由AI提炼）',
    audit_rule      varchar(500)                 null comment '判例规则总结（由AI提炼）',
    create_time     datetime                     null comment '创建时间',
    update_time     datetime                     null comment '更新时间'
)
    comment '内容审核表（保存所有审核记录）';

create index idx_auditStatus
    on content_audit (audit_status)
    comment '审核状态索引，方便查询待审核内容';

create index idx_human_final_status
    on content_audit (is_human_final, audit_reason);

create index pk_id
    on content_audit (id);

create index source_type
    on content_audit (source_type, source_id)
    comment '同一数据源允许多条审核记录';

create table home_book
(
    id          bigint unsigned auto_increment
        primary key,
    type        tinyint unsigned not null comment '推荐类型;0-轮播图 1-顶部栏 2-本周强推 3-热门推荐 4-精品推荐',
    sort        tinyint unsigned not null comment '推荐排序',
    book_id     bigint unsigned  not null comment '推荐小说ID',
    create_time datetime         null comment '创建时间',
    update_time datetime         null comment '更新时间',
    constraint pk_id
        unique (id)
)
    comment '小说推荐';

create table message_content
(
    id          bigint unsigned auto_increment comment '主键ID'
        primary key,
    title       varchar(150)                       not null comment '消息标题',
    content     text                               null comment '消息正文(支持HTML)',
    type        tinyint  default 1                 not null comment '消息类型 (0:系统公告/全员, 1:订阅更新/追更, 2:作家助手/审核, 3:私信)',
    link        varchar(255)                       null comment '跳转链接(如: /author/appeal?id=123)',
    bus_id      bigint                             null comment '业务ID(关联的书籍ID、章节ID等)',
    bus_type    varchar(50)                        null comment '业务类型(如: book, chapter, author)',
    extension   json                               null comment '扩展数据(JSON)',
    expire_time datetime                           null comment '消息/链接过期时间(NULL表示永不过期)',
    sender_type tinyint  default 0                 not null comment '发送者类型 (0:系统, 1:用户)',
    sender_id   bigint   default 0                 not null comment '发送者ID',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '消息内容表';

create index idx_create_time
    on message_content (create_time);

create index idx_sender
    on message_content (sender_id, sender_type);

create index message_content_id_index
    on message_content (id);

create index message_type_index
    on message_content (type);

create table message_receive
(
    id            bigint unsigned auto_increment comment '主键ID'
        primary key,
    message_id    bigint unsigned   not null comment '关联内容表ID',
    receiver_id   bigint unsigned   not null comment '接收者ID (对应UserID或AuthorID)',
    receiver_type tinyint default 1 not null comment '接收者身份类型 (0:普通用户/UserID, 1:作者/AuthorID)',
    is_read       tinyint default 0 not null comment '阅读状态 (0:未读, 1:已读)',
    read_time     datetime          null comment '阅读时间',
    is_deleted    tinyint default 0 not null comment '是否逻辑删除 (0:正常, 1:删除)'
)
    comment '消息接收关联表';

create index idx_message_id
    on message_receive (message_id);

create index idx_receiver_type_id
    on message_receive (receiver_type, receiver_id, is_read, is_deleted);

create table user_bookshelf
(
    id              bigint unsigned auto_increment comment '主键'
        primary key,
    user_id         bigint unsigned not null comment '用户ID',
    book_id         bigint unsigned not null comment '小说ID',
    pre_chapter_num bigint unsigned null comment '上一次阅读的章节号',
    create_time     datetime        null comment '创建时间;',
    update_time     datetime        null comment '更新时间;',
    constraint pk_id
        unique (id),
    constraint uk_userId_bookId
        unique (user_id, book_id)
)
    comment '用户书架';

create table user_feedback
(
    id          bigint unsigned auto_increment
        primary key,
    user_id     bigint unsigned not null comment '反馈用户id',
    content     varchar(512)    not null comment '反馈内容',
    create_time datetime        null comment '创建时间',
    update_time datetime        null comment '更新时间',
    constraint pk_id
        unique (id)
)
    comment '用户反馈';

create table user_info
(
    id              bigint unsigned auto_increment
        primary key,
    username        varchar(50)                  not null comment '登录名',
    password        varchar(100)                 not null comment '登录密码-加密',
    salt            varchar(8)                   not null comment '加密盐值',
    nick_name       varchar(50)                  null comment '昵称',
    user_photo      varchar(1024)                null comment '用户头像',
    user_sex        tinyint unsigned             null comment '用户性别;0-男 1-女',
    account_balance bigint unsigned  default '0' not null comment '账户余额',
    status          tinyint unsigned default '0' not null comment '用户状态;0-正常',
    create_time     datetime                     null comment '创建时间',
    update_time     datetime                     null comment '更新时间',
    constraint pk_id
        unique (id),
    constraint uk_username
        unique (username)
)
    comment '用户信息';

