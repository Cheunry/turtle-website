package com.novel.book.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.book.dao.entity.BookChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BookChapterMapper extends BaseMapper<BookChapter> {
    /**
     * 批量查询书籍的第一章编号
     * 返回 Map 列表，每个 Map 包含 book_id 和 first_chapter_num
     */
    List<Map<String, Object>> selectFirstChapterNums(@Param("bookIds") List<Long> bookIds);
}
