package com.bogutongjin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogutongjin.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    /** 查询用户已处理但未读的反馈数量 */
    @Select("SELECT COUNT(*) FROM feedback WHERE user_id = #{userId} AND resolved = 1 AND read_at IS NULL")
    int countResolvedUnread(@Param("userId") Long userId);
}
