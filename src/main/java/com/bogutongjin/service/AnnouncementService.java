package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.dto.AnnouncementDetail;
import com.bogutongjin.dto.AnnouncementListItem;
import com.bogutongjin.dto.AnnouncementUnreadResponse;
import com.bogutongjin.entity.Announcement;
import com.bogutongjin.mapper.AnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AnnouncementMapper announcementMapper;

    /** 获取公告列表（不含正文，置顶优先 + 发布时间降序） */
    public List<AnnouncementListItem> listAll() {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getIsPinned)
                .orderByDesc(Announcement::getPublishTime);
        List<Announcement> list = announcementMapper.selectList(wrapper);

        return list.stream().map(a -> {
            AnnouncementListItem item = new AnnouncementListItem();
            item.setId(a.getId());
            item.setTitle(a.getTitle());
            item.setIsPinned(a.getIsPinned());
            item.setPublishTime(a.getPublishTime() != null
                    ? a.getPublishTime().format(FMT) : null);
            return item;
        }).collect(Collectors.toList());
    }

    /** 获取公告详情（含正文） */
    public AnnouncementDetail getDetail(Long id) {
        Announcement a = announcementMapper.selectById(id);
        if (a == null) return null;

        AnnouncementDetail detail = new AnnouncementDetail();
        detail.setId(a.getId());
        detail.setTitle(a.getTitle());
        detail.setContent(a.getContent());
        detail.setPublishTime(a.getPublishTime() != null
                ? a.getPublishTime().format(FMT) : null);
        return detail;
    }

    /** 获取未读公告状态（对比客户端 lastReadId 与最新公告 ID） */
    public AnnouncementUnreadResponse getUnreadStatus(long lastReadId) {
        long latestId = announcementMapper.selectLatestId();
        AnnouncementUnreadResponse resp = new AnnouncementUnreadResponse();
        resp.setLatestId(latestId);
        resp.setHasUnread(latestId > lastReadId);

        if (latestId > 0) {
            Announcement latest = announcementMapper.selectById(latestId);
            if (latest != null) {
                resp.setLatestTitle(latest.getTitle());
            }
        }
        return resp;
    }
}
