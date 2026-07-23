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
}
