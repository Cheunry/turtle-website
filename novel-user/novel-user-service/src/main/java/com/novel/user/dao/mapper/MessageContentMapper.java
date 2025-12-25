package com.novel.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.user.dao.entity.MessageContent;
import com.novel.user.dto.resp.MessageRespDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消息内容 Mapper
 */
@Mapper
public interface MessageContentMapper extends BaseMapper<MessageContent> {

    /**
     * 查询系统公告消息列表（拉取模式，不需要 message_receive）
     * @param page 分页参数
     * @param messageType 消息类型（0:系统公告）
     * @return 消息列表
     */
    IPage<MessageRespDto> selectSystemMessageList(Page<MessageRespDto> page, @Param("messageType") Integer messageType);

    /**
     * 统计系统公告数量（未过期的）
     * @param messageType 消息类型（0:系统公告）
     * @return 系统公告数量
     */
    Long countSystemMessages(@Param("messageType") Integer messageType);

    /**
     * 查询全部消息（包括系统公告和其他消息，使用 UNION 合并）
     * @param page 分页参数
     * @param receiverId 接收者ID
     * @param receiverType 接收者类型
     * @param busType 业务类型（可选）
     * @return 消息列表
     */
    IPage<MessageRespDto> selectAllMessagesList(Page<MessageRespDto> page, 
                                                 @Param("receiverId") Long receiverId,
                                                 @Param("receiverType") Integer receiverType,
                                                 @Param("busType") String busType);

}
