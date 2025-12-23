package com.novel.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.user.dao.entity.MessageReceive;
import com.novel.user.dto.resp.MessageRespDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消息接收 Mapper
 */
@Mapper
public interface MessageReceiveMapper extends BaseMapper<MessageReceive> {

    /**
     * 查询消息列表（联表查询）
     * @param page 分页参数
     * @param receiverId 接收者ID
     * @param receiverType 接收者身份类型 (1:普通用户/UserID, 2:作者/AuthorID)
     * @param messageType 消息类型
     * @param busType 业务类型（用于进一步筛选，如：BOOK_AUDIT, CHAPTER_AUDIT, BOOK_COVER等）
     * @return 消息DTO列表
     */
    IPage<MessageRespDto> selectMessageList(Page<MessageRespDto> page, @Param("receiverId") Long receiverId, @Param("receiverType") Integer receiverType, @Param("messageType") Integer messageType, @Param("busType") String busType);

    /**
     * 统计未读数量（联表查询）
     * @param receiverId 接收者ID
     * @param receiverType 接收者身份类型 (1:普通用户/UserID, 2:作者/AuthorID)
     * @param messageType 消息类型
     * @param busType 业务类型（用于进一步筛选，如：BOOK_AUDIT, CHAPTER_AUDIT, BOOK_COVER等）
     * @return 未读数量
     */
    Long countUnRead(@Param("receiverId") Long receiverId, @Param("receiverType") Integer receiverType, @Param("messageType") Integer messageType, @Param("busType") String busType);

    /**
     * 批量标记已读
     */
    int updateBatchRead(@Param("receiverId") Long receiverId, @Param("receiverType") Integer receiverType, @Param("ids") java.util.List<Long> ids);

    /**
     * 批量删除
     */
    int updateBatchDelete(@Param("receiverId") Long receiverId, @Param("receiverType") Integer receiverType, @Param("ids") java.util.List<Long> ids);

    /**
     * 全部标记已读
     */
    int updateAllRead(@Param("receiverId") Long receiverId, @Param("receiverType") Integer receiverType);

    /**
     * 全部删除
     */
    int updateAllDelete(@Param("receiverId") Long receiverId, @Param("receiverType") Integer receiverType);
}
