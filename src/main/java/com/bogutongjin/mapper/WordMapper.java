package com.bogutongjin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogutongjin.entity.Word;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WordMapper extends BaseMapper<Word> {
}
