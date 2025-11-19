package com.novel.book.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dto.req.BookSearchReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookInfoMapper extends BaseMapper<BookInfo> {
}
