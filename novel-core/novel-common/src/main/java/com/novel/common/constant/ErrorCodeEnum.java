package com.novel.common.constant;

import lombok.Getter;

/**
 * 错误码枚举类。
 * <p>
 * 错误码为字符串类型，共 5 位，分成两个部分：错误产生来源+四位数字编号。 错误产生来源分为 A/B/C，
 * A 表示错误来源于用户，比如参数错误，用户安装版本过低，用户支付 超时等问题；
 * B表示错误来源于当前系统，往往是业务逻辑出错，或程序健壮性差等问题；
 * C 表示错误来源 于第三方服务，比如 CDN 服务出错，消息投递超时等问题；
 * 四位数字编号从 0001 到 9999，大类之间的步长间距预留 100。
 * <p>
 * 错误码分为一级宏观错误码、二级宏观错误码、三级宏观错误码。
 * 在无法更加具体确定的错误场景中，可以直接使用一级宏观错误码。
 */
@Getter
public enum ErrorCodeEnum {

    /**
     * 正确执行后的返回
     */
    OK("00000", "一切 ok"),

    /**
     * 一级宏观错误码，用户端错误
     */
    USER_ERROR("A0001", "用户端错误"),

    /**
     * 二级宏观错误码，用户注册错误
     */
    USER_REGISTER_ERROR("A0100", "用户注册错误"),

    /**
     * 用户未同意隐私协议
     */
    USER_NO_AGREE_PRIVATE_ERROR("A0101", "用户未同意隐私协议"),

    /**
     * 注册国家或地区受限
     */
    USER_REGISTER_AREA_LIMIT_ERROR("A0102", "注册国家或地区受限"),

    /**
     * 用户验证码错误
     */
    USER_VERIFY_CODE_ERROR("A0240", "用户验证码错误"),

    /**
     * 用户名已存在
     */
    USER_NAME_EXIST("A0111", "用户名已存在"),

    /**
     * 用户账号不存在
     */
    USER_ACCOUNT_NOT_EXIST("A0201", "用户账号不存在"),

    /**
     * 登录限制
     */
    USER_LOGIN_LIMIT("A0202", "登录尝试次数过多，请稍后再试"),

    /**
     * 用户密码错误
     */
    USER_PASSWORD_ERROR("A0210", "用户密码错误"),


    /**
     * 二级宏观错误码，用户请求参数错误
     */
    USER_REQUEST_PARAM_ERROR("A0400", "用户请求参数错误"),

    /**
     * 图片URL不能为空
     */
    USER_IMAGE_URL_NULL("A0401", "图片URL不能为空"),

    /**
     * 图片URL不完整
     */
    USER_IMAGE_URL_BROKEN("A0402", "图片URL不完整"),

    /**
     * 图片URL格式不正确或与当前存储配置不匹配
     */
    USER_IMAGE_URL_FALSE("A0403", "图片URL格式不正确或与当前存储配置不匹配"),

    /**
     * 用户登录已过期
     */
    USER_LOGIN_EXPIRED("A0230", "用户登录已过期"),

    /**
     * 认证 Token 缺失
     */
    AUTH_TOKEN_MISSING("A0231","Token 缺失"),

    /**
     * Token 解析失败/无效
     */
    AUTH_TOKEN_INVALID("A0232", "认证 Token 无效 (解析失败或签名错误)"),


    /**
     * 访问未授权
     */
    USER_UN_AUTH("A0301", "访问未授权"),


    /**
     * 用户请求服务异常
     */
    USER_REQ_EXCEPTION("A0500", "用户请求服务异常"),

    /**
     * 请求超出限制
     */
    USER_REQ_MANY("A0501", "请求超出限制"),

    /**
     * 用户评论异常
     */
    USER_COMMENT("A2000", "用户评论异常"),

    /**
     * 用户评论异常
     */
    USER_COMMENTED("A2001", "用户已发表评论"),

    /**
     * 作家发布异常
     */
    AUTHOR_PUBLISH("A3000", "作家发布异常"),


    /**
     * 审核相关错误
     */
    AUDIT_ERROR("A4000", "审核异常"),

    /**
     * 审核记录不存在
     */
    AUDIT_RECORD_NOT_EXIST("A4001", "审核记录不存在"),

    /**
     * 审核状态参数错误
     */
    AUDIT_STATUS_PARAM_ERROR("A4002", "审核状态参数错误"),

    /**
     * AI审核服务异常
     */
    AI_AUDIT_SERVICE_ERROR("C4000", "AI审核服务异常"),

    /**
     * AI生成图片提示词服务异常
     */
    AI_COVER_TEXT_SERVICE_ERROR("C4001", "AI生成图片提示词服务异常"),

    /**
     * 小说名已存在
     */
    AUTHOR_BOOK_NAME_EXIST("A3001", "小说名已存在"),

    /**
     * 章节号已存在
     */
    CHAPTER_NUM_EXIST("A3003","章节号已存在"),

    /**
     * 作家重复注册
     */
    AUTHOR_DUPLICATE_REGISTRATION("A3002", "您已经是作家了，无需重复注册"),

    /**
     * 书籍提交频率限制
     */
    AUTHOR_BOOK_SUBMIT_FREQUENCY_LIMIT("A3004", "提交频率过高，请稍后再试"),

    /**
     * 书籍更新频率限制
     */
    AUTHOR_BOOK_UPDATE_FREQUENCY_LIMIT("A3005", "更新频率过高，请稍后再试"),

    /**
     * 用户上传文件异常
     */
    USER_UPLOAD_FILE_ERROR("A0700", "用户上传文件异常"),

    /**
     * 用户上传文件类型不匹配
     */
    USER_UPLOAD_FILE_TYPE_NOT_MATCH("A0701", "用户上传文件类型不匹配"),

    /**
     * 一级宏观错误码，系统执行出错
     */
    SYSTEM_ERROR("B0001", "系统执行出错"),

    /**
     * 二级宏观错误码，系统执行超时
     */
    SYSTEM_TIMEOUT_ERROR("B0100", "系统执行超时"),

    /**
     * 一级宏观错误码，调用第三方服务出错
     */
    THIRD_SERVICE_ERROR("C0001", "调用第三方服务出错"),

    /**
     * 一级宏观错误码，中间件服务出错
     */
    MIDDLEWARE_SERVICE_ERROR("C0100", "中间件服务出错"),

    /**
     * COS 对象存储服务调用失败
     */
    THIRD_SERVICE_COS_ERROR("C0002", "COS 对象存储服务调用失败");


    /**
     * 错误码
     */
    private final String code;

    /**
     * 中文描述
     */
    private final String message;

    /**
     * 构造器
     */
    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     * @return 错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取错误消息
     * @return 错误消息
     */
    public String getMessage() {
        return message;
    }

}
