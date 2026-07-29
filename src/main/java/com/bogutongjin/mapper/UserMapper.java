package com.bogutongjin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogutongjin.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 更新用户最后活跃时间（LoginInterceptor 中调用） */
    @Update("UPDATE `user` SET last_active_at = NOW() WHERE id = #{userId}")
    void updateLastActiveAt(Long userId);

    /** 累计学习天数 +1（SQL 级别原子操作，与 completeStudy 中首次打卡同步触发） */
    @Update("UPDATE `user` SET checkin_days = checkin_days + 1 WHERE id = #{userId}")
    void updateCheckinDays(Long userId);
}
