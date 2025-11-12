package com.novel.user.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.user.dao.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

}

/*
  Mapper层（数据持久层） 负责与数据库直接交互，执行SQL操作。
  继承后自动获得的方法：
  insert()- 插入记录
  deleteById()- 根据ID删除
  updateById()- 根据ID更新
  selectById()- 根据ID查询
  selectList()- 查询所有记录
  selectCount()- 统计记录数
  等等20+个常用CRUD方法

  如果不继承BaseMapper的话，就需要手动编写每个方法和实现对应的xml/sql
  所以，继承 BaseMapper可以免去编写基础CRUD的重复代码，提高开发效率。
 */
