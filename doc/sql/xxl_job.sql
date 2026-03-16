create table xxl_job_group
(
    id           int auto_increment
        primary key,
    app_name     varchar(64)       not null comment '执行器AppName',
    title        varchar(12)       not null comment '执行器名称',
    address_type tinyint default 0 not null comment '执行器地址类型：0=自动注册、1=手动录入',
    address_list text              null comment '执行器地址列表，多地址逗号分隔',
    update_time  datetime          null
)
    charset = utf8mb4;

create table xxl_job_info
(
    id                        int auto_increment
        primary key,
    job_group                 int                              not null comment '执行器主键ID',
    job_desc                  varchar(255)                     not null,
    add_time                  datetime                         null,
    update_time               datetime                         null,
    author                    varchar(64)                      null comment '作者',
    alarm_email               varchar(255)                     null comment '报警邮件',
    schedule_type             varchar(50) default 'NONE'       not null comment '调度类型',
    schedule_conf             varchar(128)                     null comment '调度配置，值含义取决于调度类型',
    misfire_strategy          varchar(50) default 'DO_NOTHING' not null comment '调度过期策略',
    executor_route_strategy   varchar(50)                      null comment '执行器路由策略',
    executor_handler          varchar(255)                     null comment '执行器任务handler',
    executor_param            varchar(512)                     null comment '执行器任务参数',
    executor_block_strategy   varchar(50)                      null comment '阻塞处理策略',
    executor_timeout          int         default 0            not null comment '任务执行超时时间，单位秒',
    executor_fail_retry_count int         default 0            not null comment '失败重试次数',
    glue_type                 varchar(50)                      not null comment 'GLUE类型',
    glue_source               mediumtext                       null comment 'GLUE源代码',
    glue_remark               varchar(128)                     null comment 'GLUE备注',
    glue_updatetime           datetime                         null comment 'GLUE更新时间',
    child_jobid               varchar(255)                     null comment '子任务ID，多个逗号分隔',
    trigger_status            tinyint     default 0            not null comment '调度状态：0-停止，1-运行',
    trigger_last_time         bigint      default 0            not null comment '上次调度时间',
    trigger_next_time         bigint      default 0            not null comment '下次调度时间'
)
    charset = utf8mb4;

create table xxl_job_lock
(
    lock_name varchar(50) not null comment '锁名称'
        primary key
)
    charset = utf8mb4;

create table xxl_job_log
(
    id                        bigint auto_increment
        primary key,
    job_group                 int               not null comment '执行器主键ID',
    job_id                    int               not null comment '任务，主键ID',
    executor_address          varchar(255)      null comment '执行器地址，本次执行的地址',
    executor_handler          varchar(255)      null comment '执行器任务handler',
    executor_param            varchar(512)      null comment '执行器任务参数',
    executor_sharding_param   varchar(20)       null comment '执行器任务分片参数，格式如 1/2',
    executor_fail_retry_count int     default 0 not null comment '失败重试次数',
    trigger_time              datetime          null comment '调度-时间',
    trigger_code              int               not null comment '调度-结果',
    trigger_msg               text              null comment '调度-日志',
    handle_time               datetime          null comment '执行-时间',
    handle_code               int               not null comment '执行-状态',
    handle_msg                text              null comment '执行-日志',
    alarm_status              tinyint default 0 not null comment '告警状态：0-默认、1-无需告警、2-告警成功、3-告警失败'
)
    charset = utf8mb4;

create index I_handle_code
    on xxl_job_log (handle_code);

create index I_trigger_time
    on xxl_job_log (trigger_time);

create table xxl_job_log_report
(
    id            int auto_increment
        primary key,
    trigger_day   datetime      null comment '调度-时间',
    running_count int default 0 not null comment '运行中-日志数量',
    suc_count     int default 0 not null comment '执行成功-日志数量',
    fail_count    int default 0 not null comment '执行失败-日志数量',
    update_time   datetime      null,
    constraint i_trigger_day
        unique (trigger_day)
)
    charset = utf8mb4;

create table xxl_job_logglue
(
    id          int auto_increment
        primary key,
    job_id      int          not null comment '任务，主键ID',
    glue_type   varchar(50)  null comment 'GLUE类型',
    glue_source mediumtext   null comment 'GLUE源代码',
    glue_remark varchar(128) not null comment 'GLUE备注',
    add_time    datetime     null,
    update_time datetime     null
)
    charset = utf8mb4;

create table xxl_job_registry
(
    id             int auto_increment
        primary key,
    registry_group varchar(50)  not null,
    registry_key   varchar(255) not null,
    registry_value varchar(255) not null,
    update_time    datetime     null
)
    charset = utf8mb4;

create index i_g_k_v
    on xxl_job_registry (registry_group, registry_key, registry_value);

create table xxl_job_user
(
    id         int auto_increment
        primary key,
    username   varchar(50)  not null comment '账号',
    password   varchar(50)  not null comment '密码',
    role       tinyint      not null comment '角色：0-普通用户、1-管理员',
    permission varchar(255) null comment '权限：执行器ID列表，多个逗号分割',
    constraint i_username
        unique (username)
)
    charset = utf8mb4;

