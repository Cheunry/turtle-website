package com.novel.common.constant;


/**
 * 通用常量
 *
 * @author xiongxiaoyang
 * @date 2022/5/12
 */
public class CommonConsts {

    /**
     * 是
     */
    public static final Integer YES = 1;
    public static final String TRUE = "true";


    /**
     * 否
     */
    public static final Integer NO = 0;
    public static final String FALSE = "false";

    /**
     * 性别常量
     */
//    public enum SexEnum {
//
//        /**
//         * 男
//         */
//        MALE(0, "男"),
//
//        /**
//         * 女
//         */
//        FEMALE(1, "女");
//        UNKNOWN(-1, "未知");
//
//        SexEnum(int code, String desc) {
//            this.code = code;
//            this.desc = desc;
//        }
//
//        private int code;
//        private String desc;
//
//        public int getCode() {
//            return code;
//        }
//
//        public String getDesc() {
//            return desc;
//        }
//
//    }
    public enum SexEnum {
        MALE(0, "男"),
        FEMALE(1, "女"),
        UNKNOWN(-1, "未知");  // 添加未知状态

        SexEnum(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        private final int code;
        private final String desc;

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        // 添加根据code获取枚举的方法
        public static SexEnum getByCode(Integer code) {
            if (code == null) return UNKNOWN;

            for (SexEnum sex : values()) {
                if (sex.code == code) {
                    return sex;
                }
            }
            return UNKNOWN;
        }

        // 添加根据code获取描述的方法
        public static String getDescByCode(Integer code) {
            return getByCode(code).getDesc();
        }
    }
}
