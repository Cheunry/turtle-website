package com.novel.common.util;

/**
 * 排行榜接口书籍简介：去 HTML 后截断为固定字数，减小响应体积。
 */
public final class RankBookDescUtils {

    /** 排行榜简介最大字符数（按 Unicode 码点计，汉字计 1 字） */
    public static final int RANK_BOOK_DESC_MAX_CHARS = 40;

    private RankBookDescUtils() {
    }

    /**
     * 将存库的 HTML/富文本简介转为纯文本预览，超长则截断并加省略号。
     *
     * @param htmlOrText 可能含 HTML 的简介，可为 null
     * @return 截断后的纯文本；入参为 null 时返回 null
     */
    public static String toRankPreview(String htmlOrText) {
        if (htmlOrText == null) {
            return null;
        }
        String plain = stripToPlainText(htmlOrText);
        plain = plain.replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) {
            return "";
        }
        int count = plain.codePointCount(0, plain.length());
        if (count <= RANK_BOOK_DESC_MAX_CHARS) {
            return plain;
        }
        int end = plain.offsetByCodePoints(0, RANK_BOOK_DESC_MAX_CHARS);
        return plain.substring(0, end) + "…";
    }

    private static String stripToPlainText(String s) {
        String t = s;
        t = t.replaceAll("(?i)<br\\s*/?>", " ");
        t = t.replaceAll("(?i)</p>", " ");
        t = t.replaceAll("<[^>]+>", "");
        t = t.replace("&nbsp;", " ");
        t = t.replace("&amp;", "&");
        t = t.replace("&lt;", "<");
        t = t.replace("&gt;", ">");
        t = t.replace("&quot;", "\"");
        return t;
    }
}
