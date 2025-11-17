package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.common.constant.DatabaseConsts;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.req.BookSearchReqDto;
import lombok.Getter;
import com.novel.book.service.BookSearchService;

import java.util.List;

@Getter
public class BookSearchServiceImpl implements BookSearchService {


    private final BookInfoMapper bookInfoMapper;

    public BookSearchServiceImpl(BookInfoMapper bookInfoMapper) {
        this.bookInfoMapper = bookInfoMapper;
    }

    @Override
    public List<BookInfo> searchByBookName(BookSearchReqDto dto) {
        String inputString = dto.getInputString();
        /*
          QueryWrapper：可以理解为"SQL查询语句的构建器"
          <BookInfo>：指定要查询的是"书籍信息"表
          这行代码相当于：我要开始构建一个查询书籍表的SQL语句了
         */
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(DatabaseConsts.BookTable.COLUMN_BOOK_NAME,inputString)
                .orderByDesc(DatabaseConsts.BookTable.COLUMN_WORD_COUNT)
                .last("limit 20");
        return bookInfoMapper.selectList(queryWrapper);

    }
}
