package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.entity.Classic;
import com.bogutongjin.entity.ClassicChapter;
import com.bogutongjin.entity.ClassicParagraph;
import com.bogutongjin.entity.ClassicGlossary;
import com.bogutongjin.mapper.ClassicMapper;
import com.bogutongjin.mapper.ClassicChapterMapper;
import com.bogutongjin.mapper.ClassicParagraphMapper;
import com.bogutongjin.mapper.ClassicGlossaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassicService {

    private final ClassicMapper classicMapper;
    private final ClassicChapterMapper classicChapterMapper;
    private final ClassicParagraphMapper classicParagraphMapper;
    private final ClassicGlossaryMapper classicGlossaryMapper;

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

    /**
     * 获取经典著作详情（含章节、段落、典故注释）
     * @param classicId 经典著作ID
     * @return 嵌套结构的完整经典数据
     */
    public Map<String, Object> getClassicDetail(Long classicId) {
        Classic classic = classicMapper.selectById(classicId);
        if (classic == null) {
            throw new RuntimeException("经典不存在");
        }

        // 查询章节
        LambdaQueryWrapper<ClassicChapter> chapterQw = new LambdaQueryWrapper<ClassicChapter>()
                .eq(ClassicChapter::getClassicId, classicId)
                .orderByAsc(ClassicChapter::getSortOrder);
        List<ClassicChapter> chapters = classicChapterMapper.selectList(chapterQw);

        // 查询所有段落
        LambdaQueryWrapper<ClassicParagraph> paraQw = new LambdaQueryWrapper<ClassicParagraph>()
                .in(ClassicParagraph::getChapterId,
                    chapters.stream().map(ClassicChapter::getId).collect(Collectors.toList()))
                .orderByAsc(ClassicParagraph::getSortOrder);
        List<ClassicParagraph> allParagraphs = classicParagraphMapper.selectList(paraQw);

        // 查询所有注释
        LambdaQueryWrapper<ClassicGlossary> glossQw = new LambdaQueryWrapper<ClassicGlossary>()
                .in(ClassicGlossary::getParagraphId,
                    allParagraphs.stream().map(ClassicParagraph::getId).collect(Collectors.toList()))
                .orderByAsc(ClassicGlossary::getSortOrder);
        List<ClassicGlossary> allGlossaries = classicGlossaryMapper.selectList(glossQw);

        // 按 paragraphId 分组注释
        Map<Long, List<Map<String, String>>> glossaryMap = allGlossaries.stream()
                .collect(Collectors.groupingBy(
                    ClassicGlossary::getParagraphId,
                    Collectors.mapping(g -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("word", g.getWord());
                        m.put("explanation", g.getExplanation());
                        return m;
                    }, Collectors.toList())
                ));

        // 按 chapterId 分组段落
        Map<Long, List<ClassicParagraph>> paraMap = allParagraphs.stream()
                .collect(Collectors.groupingBy(ClassicParagraph::getChapterId));

        // 组装嵌套结构
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", classic.getId());
        result.put("name", classic.getName());
        result.put("author", resolveAuthor(classic));
        result.put("era", classic.getEra());
        result.put("category", classic.getCategory());
        result.put("description", classic.getDescription());

        List<Map<String, Object>> chapterList = chapters.stream().map(ch -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", ch.getId());
            cm.put("title", ch.getTitle());

            List<ClassicParagraph> paras = paraMap.getOrDefault(ch.getId(), List.of());
            List<Map<String, Object>> paraList = paras.stream().map(p -> {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("text", p.getText());
                pm.put("translation", p.getTranslation());
                pm.put("glossary", glossaryMap.getOrDefault(p.getId(), List.of()));
                return pm;
            }).collect(Collectors.toList());
            cm.put("paragraphs", paraList);
            return cm;
        }).collect(Collectors.toList());
        result.put("chapters", chapterList);

        return result;
    }

    /**
     * 解析作者信息。部分经典在"经典著作"表中仅存书名，作者信息后续完善。
     */
    private String resolveAuthor(Classic classic) {
        // 目前 classic 表无 author 字段，暂根据 ID 返回已知作者
        Map<Long, String> knownAuthors = Map.of(
            1L, "孔子及其弟子",
            2L, "孟子及其弟子",
            17L, "荀子",
            18L, "老子",
            19L, "庄子",
            20L, "韩非",
            21L, "墨子",
            22L, "孙武"
        );
        return knownAuthors.getOrDefault(classic.getId(), "佚名");
    }
}
