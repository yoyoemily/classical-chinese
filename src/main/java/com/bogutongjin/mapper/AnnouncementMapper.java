package com.bogutongjin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogutongjin.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {

    /** 查询最新公告 ID（无公告时返回 0） */
    @Select("SELECT COALESCE(MAX(id), 0) FROM announcement")
    long selectLatestId();
}
