package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.entity.Classic;
import com.bogutongjin.mapper.ClassicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassicService {

    private final ClassicMapper classicMapper;

    /**
     * 获取经典著作列表，可按四部分类筛选
     * @param category 四部分类（经/史/子/集），null 表示返回全部
     */
    public List<Map<String, Object>> listClassics(String category) {
        LambdaQueryWrapper<Classic> qw = new LambdaQueryWrapper<Classic>()
                .orderByAsc(Classic::getSortOrder);

        if (category != null && !category.isEmpty() && !"all".equals(category)) {
            qw.eq(Classic::getCategory, category);
        }

        List<Classic> classics = classicMapper.selectList(qw);
        return classics.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("era", c.getEra());
            m.put("icon", c.getIcon());
            m.put("description", c.getDescription());
            m.put("category", c.getCategory());
            return m;
        }).collect(Collectors.toList());
    }
}
