package com.novel.book.service;

public interface BookExistBloomService {

    /**
     * 判断书籍ID是否可能存在（布隆过滤器）
     */
    boolean mightContain(Long bookId);

    /**
     * 向线上布隆过滤器添加书籍ID
     */
    void add(Long bookId);

    /**
     * 重建布隆过滤器（全量）
     */
    void rebuildBloomFilter();
}
