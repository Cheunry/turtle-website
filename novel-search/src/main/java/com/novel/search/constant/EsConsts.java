package com.novel.search.constant;

import com.novel.common.constant.SystemConfigConsts;

/**
 * 提供一个集中的地方来定义和管理 Elasticsearch 索引名称 和 文档字段名
 */
public class EsConsts {

    //
    /*为什么要创建一个私有的构造方法，并且抛出异常？？？？？？
        回答：这是一个惯用法，它向所有开发者明确声明：“这个类只用于存放静态常量，绝对禁止创建它的对象！
            1.阻止实例化:在 Java 中，如果不提供任何构造函数，编译器会自动为你添加一个公共的、无参数的默认构造函数。
                如果没有这个私有构造函数，用户就可以用默认构造函数创建这个常量类的实例；有了这个私有构造函数，用户就不能创建实例
            2.避免误用和资源浪费
                误用： 实例化一个工具类可能会让人误以为这个对象本身具有某种状态或方法，从而导致不必要的困惑。
                资源浪费： 每次创建实例都会消耗少量的内存。对于一个只需要静态访问的类，创建实例是完全多余的。
            3.抛出异常的作用
                双重保险 (防止反射攻击): 尽管构造函数是 private 的，但高级用户仍然可以通过 Java 反射机制强制调用私有构造函数。
                明确意图： 一旦有人尝试通过反射创建实例，代码会立即抛出 IllegalStateException（非法状态异常），
                        并带有明确的错误信息（可能是“这是一个常量类，禁止实例化”），从而强制阻止了这种非预期的行为。
     */
    private EsConsts() {
        // 常量类实例化异常信息
        throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
    }

    public class BookIndex {

        private BookIndex() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        /**
         * 索引名
         */
        public static final String INDEX_NAME = "book";

        /**
         * id
         */
        public static final String FIELD_ID = "id";

        /**
         * 作品方向;0-男频 1-女频
         */
        public static final String FIELD_WORK_DIRECTION = "workDirection";

        /**
         * 类别ID
         */
        public static final String FIELD_CATEGORY_ID = "categoryId";

        /**
         * 类别名
         */
        public static final String FIELD_CATEGORY_NAME = "categoryName";

        /**
         * 小说名
         */
        public static final String FIELD_BOOK_NAME = "bookName";


        /**
         * 作家名
         */
        public static final String FIELD_AUTHOR_NAME = "authorName";

        /**
         * 书籍描述
         */
        public static final String FIELD_BOOK_DESC = "bookDesc";

        /**
         * 评分;总分:10 ，真实评分 = score/10
         */
        public static final String FIELD_SCORE = "score";

        /**
         * 书籍状态;0-连载中 1-已完结
         */
        public static final String FIELD_BOOK_STATUS = "bookStatus";

        /**
         * 点击量
         */
        public static final String FIELD_VISIT_COUNT = "visitCount";

        /**
         * 总字数
         */
        public static final String FIELD_WORD_COUNT = "wordCount";

        /**
         * 评论数
         */
        public static final String FIELD_COMMENT_COUNT = "commentCount";

        /**
         * 最新章节名
         */
        public static final String FIELD_LAST_CHAPTER_NAME = "lastChapterName";

        /**
         * 最新章节更新时间
         */
        public static final String FIELD_LAST_CHAPTER_UPDATE_TIME = "lastChapterUpdateTime";

        /**
         * 是否收费;1-收费 0-免费
         */
        public static final String FIELD_IS_VIP = "isVip";
    }

}
