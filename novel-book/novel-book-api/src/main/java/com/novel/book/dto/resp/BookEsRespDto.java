package com.novel.book.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elasticsearch 存储小说 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookEsRespDto {

    /**
     * 主键
     */
    private Long id;

    /**
     * 作品方向;0-男频 1-女频
     */
    private Integer workDirection;

    /**
     * 类别ID
     */
    private Long categoryId;

    /**
     * 类别名
     */
    private String categoryName;

    /**
     * 小说封面地址
     */
    private String picUrl;

    /**
     * 小说名
     */
    private String bookName;

    /**
     * 作家名
     */
    private String authorName;

    /**
     * 书籍描述
     */
    private String bookDesc;

    /**
     * 评分;总分:10 ，真实评分 = score/10
     */
    private Integer score;

    /**
     * 书籍状态;0-连载中 1-已完结
     */
    private Integer bookStatus;

    /**
     * 点击量
     */
    private Long visitCount;

    /**
     * 总字数
     */
    private Integer wordCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 最新章节名
     */
    private String lastChapterName;

    /**
     * 最新章节更新时间
     */
    private Long lastChapterUpdateTime;

    /**
     * 是否收费;1-收费 0-免费
     */
    private Integer isVip;

    /**
     * 向量数据 (用于混合检索)
     */
    private java.util.List<Float> embedding;

}

/*
关于lastChapterUpdateTime 的字段类型，做以下2点解释：

1.BookEsRespDto中用Long，数据传输层。为了配合 epoch_millis 格式，需要用 Long 来承载毫秒时间戳。
2，ES Mapping中用date，存储层。为了开启时间功能，必须告诉 ES 这不是一个普通数字，而是一个时间点。

为什么这是最佳实践？原因有如下2点

1.功能完整性 (type: date):
    只有定义为 date 类型，才能在 Elasticsearch 中使用强大的时间功能，例如：
        时间范围查询: updateTime > now-1h (查找过去一小时的更新)
        日期直方图聚合: 按天、按月统计书籍更新量

2.数据传输 (format: epoch_millis):
    这告诉 Elasticsearch 你的 Java 代码发送的是一个 Long 类型的毫秒数。
    这个方法是最可靠的，因为它避免了 JSON 字符串格式化带来的所有潜在问题。
 */
