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
import com.bogutongjin.util.PinyinUtils;
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
        result.put("author", classic.getAuthor());
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
        if (chapter != null && chapter.getAuthor() != null) result.put("author", chapter.getAuthor());
        if (chapter != null && chapter.getEra() != null) result.put("era", chapter.getEra());
        if (chapter != null && chapter.getBackground() != null) result.put("background", chapter.getBackground());

        List<Map<String, Object>> paraList = paragraphs.stream().map(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("text", p.getText());
            pm.put("translation", p.getTranslation());
            pm.put("glossary", glossaryMap.getOrDefault(p.getId(), List.of()));
            pm.put("rareCharPinyin", PinyinUtils.buildRareCharPinyin(p.getText()));
            return pm;
        }).collect(Collectors.toList());
        result.put("paragraphs", paraList);

        return result;
    }

    /**
     * 构建目录树
     * accordion 模式（如世说新语）构建二级树：parent chapters → children as leaf nodes
     * volume + accordion 模式（如山海经）在平铺章上构建虚拟分组
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
            // 卷帙型手风琴：在平铺章上构建虚拟二级分组
            if ("volume".equals(structureType) && "accordion".equals(navMode)) {
                return buildVolumeAccordionToc(parentNodes);
            }
            // 无二级结构：一级平铺（章节型）
            for (ClassicChapter ch : parentNodes) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", String.valueOf(ch.getId()));
                node.put("title", ch.getTitle());
                node.put("level", 0);
                node.put("isLeaf", true);
                if (ch.getAuthor() != null) node.put("author", ch.getAuthor());
                if (ch.getEra() != null) node.put("era", ch.getEra());
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
                    if (child.getAuthor() != null) childNode.put("author", child.getAuthor());
                    if (child.getEra() != null) childNode.put("era", child.getEra());
                    childNodes.add(childNode);
                }
                node.put("children", childNodes);
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 卷帙型手风琴目录：将平铺的卷级章节按标题模式分组，构建虚拟二级目录
     *
     * 分组规则按经典标题硬编码匹配，新加入的卷帙型经典需在此扩展分组。
     * 未匹配的章节作为顶级叶子节点兜底。
     */
    private List<Map<String, Object>> buildVolumeAccordionToc(List<ClassicChapter> chapters) {
        // 山海经分组规则：山经 / 海经 / 大荒经 / 海内经
        record VolumeGroup(String groupId, String groupTitle, List<String> titles) {}
        List<VolumeGroup> groups = List.of(
            new VolumeGroup("vol_shanjing", "山经",
                List.of("南山经", "西山经", "北山经", "东山经", "中山经")),
            new VolumeGroup("vol_haijing", "海经",
                List.of("海外南经", "海外西经", "海外北经", "海外东经",
                        "海内南经", "海内西经", "海内北经", "海内东经")),
            new VolumeGroup("vol_dahuangjing", "大荒经",
                List.of("大荒东经", "大荒南经", "大荒西经", "大荒北经")),
            new VolumeGroup("vol_haineijing", "海内经",
                List.of("海内经"))
        );

        // 标题 → 分组索引
        Map<String, Integer> titleToGroup = new HashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            for (String title : groups.get(i).titles()) {
                titleToGroup.put(title, i);
            }
        }

        // 分组
        List<List<ClassicChapter>> groupedChapters = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            groupedChapters.add(new ArrayList<>());
        }
        List<ClassicChapter> unmatched = new ArrayList<>();

        for (ClassicChapter ch : chapters) {
            Integer idx = titleToGroup.get(ch.getTitle());
            if (idx != null) {
                groupedChapters.get(idx).add(ch);
            } else {
                unmatched.add(ch);
            }
        }

        // 构建 TOC
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<ClassicChapter> children = groupedChapters.get(i);
            if (children.isEmpty()) continue;

            VolumeGroup vg = groups.get(i);
            Map<String, Object> groupNode = new LinkedHashMap<>();
            groupNode.put("id", vg.groupId());
            groupNode.put("title", vg.groupTitle());
            groupNode.put("level", 0);
            groupNode.put("isLeaf", false);

            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (ClassicChapter child : children) {
                Map<String, Object> childNode = new LinkedHashMap<>();
                childNode.put("id", String.valueOf(child.getId()));
                childNode.put("title", child.getTitle());
                childNode.put("level", 1);
                childNode.put("isLeaf", true);
                childNodes.add(childNode);
            }
            groupNode.put("children", childNodes);
            nodes.add(groupNode);
        }

        // 未匹配的章节兜底为顶级叶子
        for (ClassicChapter ch : unmatched) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", String.valueOf(ch.getId()));
            node.put("title", ch.getTitle());
            node.put("level", 0);
            node.put("isLeaf", true);
            nodes.add(node);
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
            if (ch.getAuthor() != null) cm.put("author", ch.getAuthor());
            if (ch.getEra() != null) cm.put("era", ch.getEra());
            if (ch.getBackground() != null) cm.put("background", ch.getBackground());

            List<ClassicParagraph> paras = paraMap.getOrDefault(ch.getId(), List.of());
            List<Map<String, Object>> paraList = paras.stream().map(p -> {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("text", p.getText());
                pm.put("translation", p.getTranslation());
                pm.put("glossary", glossaryMap.getOrDefault(p.getId(), List.of()));
                pm.put("rareCharPinyin", PinyinUtils.buildRareCharPinyin(p.getText()));
                return pm;
            }).collect(Collectors.toList());
            cm.put("paragraphs", paraList);
            return cm;
        }).collect(Collectors.toList());
    }
}
