package com.novel.book.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.book.dao.entity.BookInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface BookInfoMapper extends BaseMapper<BookInfo> {

    /**
     * 最初的原始方法，已经换用redis！
     * 增加小说点击量
     *
     * @param bookId 小说ID
     */
    void addVisitCount(@Param("bookId") Long bookId);
}
