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
            m.put("loadMode", c.getLoadMode());
            m.put("navMode", c.getNavMode());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 获取经典著作基本信息（轻量，不含全文内容）
     * @param classicId 经典著作ID
     * @return 基本信息 + 目录树
     */
    public Map<String, Object> getClassicMeta(Long classicId) {
        Classic classic = classicMapper.selectById(classicId);
        if (classic == null) {
            throw new RuntimeException("经典不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", classic.getId());
        result.put("name", classic.getName());
        result.put("author", resolveAuthor(classic));
        result.put("era", classic.getEra());
        result.put("category", classic.getCategory());
        result.put("description", classic.getDescription());
        result.put("structureType", classic.getStructureType());
        result.put("loadMode", classic.getLoadMode());
        result.put("navMode", classic.getNavMode());

        // 目录树（轻量，仅标题不含内容）
        result.put("toc", buildToc(classicId, classic.getStructureType(), classic.getNavMode()));

        // full 模式下顺带返回全文
        if ("full".equals(classic.getLoadMode())) {
            result.put("chapters", buildFullContent(classicId));
        }

        return result;
    }

    /**
     * 获取内容块（按需加载的叶子节点）
     * @param classicId 经典著作ID
     * @param nodeId    目录树叶子节点 ID（章节/篇/卷 ID）
     * @return 内容块（原文+译文+典故注释）
     */
    public Map<String, Object> getClassicContent(Long classicId, String nodeId) {
        Classic classic = classicMapper.selectById(classicId);
        if (classic == null) {
            throw new RuntimeException("经典不存在");
        }

        Long chapterId = Long.parseLong(nodeId);

        // 查询该章节的段落
        LambdaQueryWrapper<ClassicParagraph> paraQw = new LambdaQueryWrapper<ClassicParagraph>()
                .eq(ClassicParagraph::getChapterId, chapterId)
                .orderByAsc(ClassicParagraph::getSortOrder);
        List<ClassicParagraph> paragraphs = classicParagraphMapper.selectList(paraQw);

        // 查询该章节的段落的注释
        List<Long> paraIds = paragraphs.stream().map(ClassicParagraph::getId).collect(Collectors.toList());
        Map<Long, List<Map<String, String>>> glossaryMap = paraIds.isEmpty() ? Map.of()
                : classicGlossaryMapper.selectList(
                    new LambdaQueryWrapper<ClassicGlossary>()
                        .in(ClassicGlossary::getParagraphId, paraIds)
                        .orderByAsc(ClassicGlossary::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(
                    ClassicGlossary::getParagraphId,
                    Collectors.mapping(g -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("word", g.getWord());
                        m.put("explanation", g.getExplanation());
                        return m;
                    }, Collectors.toList())
                ));

        // 查询章节标题
        ClassicChapter chapter = classicChapterMapper.selectById(chapterId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(chapterId));
        result.put("title", chapter != null ? chapter.getTitle() : "");

        List<Map<String, Object>> paraList = paragraphs.stream().map(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("text", p.getText());
            pm.put("translation", p.getTranslation());
            pm.put("glossary", glossaryMap.getOrDefault(p.getId(), List.of()));
            return pm;
        }).collect(Collectors.toList());
        result.put("paragraphs", paraList);

        return result;
    }

    /**
     * 构建目录树
     * accordion 模式（如世说新语）构建二级树：parent chapters → children as leaf nodes
     */
    private List<Map<String, Object>> buildToc(Long classicId, String structureType, String navMode) {
        LambdaQueryWrapper<ClassicChapter> qw = new LambdaQueryWrapper<ClassicChapter>()
                .eq(ClassicChapter::getClassicId, classicId)
                .orderByAsc(ClassicChapter::getSortOrder);
        List<ClassicChapter> chapters = classicChapterMapper.selectList(qw);

        // 按 parent_id 分组：无 parent 的为组（门类），有 parent 的为子节点（条目）
        List<ClassicChapter> parentNodes = chapters.stream()
                .filter(ch -> ch.getParentId() == null)
                .collect(Collectors.toList());
        Map<Long, List<ClassicChapter>> childrenMap = chapters.stream()
                .filter(ch -> ch.getParentId() != null)
                .collect(Collectors.groupingBy(ClassicChapter::getParentId));

        List<Map<String, Object>> nodes = new ArrayList<>();

        if (childrenMap.isEmpty()) {
            // 无二级结构：一级平铺（章节型）
            for (ClassicChapter ch : parentNodes) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", String.valueOf(ch.getId()));
                node.put("title", ch.getTitle());
                node.put("level", 0);
                node.put("isLeaf", true);
                nodes.add(node);
            }
        } else {
            // 有二级结构：accordion 模式（选集型）
            for (ClassicChapter parent : parentNodes) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", String.valueOf(parent.getId()));
                node.put("title", parent.getTitle());
                node.put("level", 0);
                node.put("isLeaf", false);
                List<ClassicChapter> childList = childrenMap.getOrDefault(parent.getId(), List.of());
                List<Map<String, Object>> childNodes = new ArrayList<>();
                for (ClassicChapter child : childList) {
                    Map<String, Object> childNode = new LinkedHashMap<>();
                    childNode.put("id", String.valueOf(child.getId()));
                    childNode.put("title", child.getTitle());
                    childNode.put("level", 1);
                    childNode.put("isLeaf", true);
                    childNodes.add(childNode);
                }
                node.put("children", childNodes);
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * full 模式下一并返回全文
     */
    private List<Map<String, Object>> buildFullContent(Long classicId) {
        LambdaQueryWrapper<ClassicChapter> chapterQw = new LambdaQueryWrapper<ClassicChapter>()
                .eq(ClassicChapter::getClassicId, classicId)
                .orderByAsc(ClassicChapter::getSortOrder);
        List<ClassicChapter> chapters = classicChapterMapper.selectList(chapterQw);
        if (chapters.isEmpty()) return List.of();

        List<Long> chapterIds = chapters.stream().map(ClassicChapter::getId).collect(Collectors.toList());
        if (chapterIds.isEmpty()) return List.of();

        LambdaQueryWrapper<ClassicParagraph> paraQw = new LambdaQueryWrapper<ClassicParagraph>()
                .in(ClassicParagraph::getChapterId, chapterIds)
                .orderByAsc(ClassicParagraph::getSortOrder);
        List<ClassicParagraph> allParagraphs = classicParagraphMapper.selectList(paraQw);
        if (allParagraphs.isEmpty()) return chapters.stream().map(ch -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", ch.getId());
            cm.put("title", ch.getTitle());
            cm.put("paragraphs", List.of());
            return cm;
        }).collect(Collectors.toList());

        List<Long> paraIds = allParagraphs.stream().map(ClassicParagraph::getId).collect(Collectors.toList());
        Map<Long, List<Map<String, String>>> glossaryMap = paraIds.isEmpty() ? Map.of()
                : classicGlossaryMapper.selectList(
                    new LambdaQueryWrapper<ClassicGlossary>()
                        .in(ClassicGlossary::getParagraphId, paraIds)
                        .orderByAsc(ClassicGlossary::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(
                    ClassicGlossary::getParagraphId,
                    Collectors.mapping(g -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("word", g.getWord());
                        m.put("explanation", g.getExplanation());
                        return m;
                    }, Collectors.toList())
                ));

        Map<Long, List<ClassicParagraph>> paraMap = allParagraphs.stream()
                .collect(Collectors.groupingBy(ClassicParagraph::getChapterId));

        return chapters.stream().map(ch -> {
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
    }

    /**
     * 解析作者信息
     */
    private String resolveAuthor(Classic classic) {
        Map<Long, String> knownAuthors = Map.ofEntries(
            Map.entry(1L, "孔子及其弟子"),
            Map.entry(2L, "孟子及其弟子"),
            Map.entry(13L, "孙膑"),
            Map.entry(17L, "荀子"),
            Map.entry(18L, "老子"),
            Map.entry(19L, "庄子"),
            Map.entry(20L, "韩非"),
            Map.entry(21L, "墨子"),
            Map.entry(22L, "孙武"),
            Map.entry(33L, "刘义庆"),
            Map.entry(36L, "佚名")
        );
        return knownAuthors.getOrDefault(classic.getId(), "佚名");
    }
}
