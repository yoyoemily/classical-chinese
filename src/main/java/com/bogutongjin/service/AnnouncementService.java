package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.dto.AnnouncementListItem;
import com.bogutongjin.dto.AnnouncementUnreadResponse;
import com.bogutongjin.entity.Announcement;
import com.bogutongjin.mapper.AnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    /** 获取公告列表（按发布时间降序） */
    public List<AnnouncementListItem> listAll() {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getPublishTime);
        List<Announcement> list = announcementMapper.selectList(wrapper);

        return list.stream().map(a -> {
            AnnouncementListItem item = new AnnouncementListItem();
            item.setId(a.getId());
            item.setTitle(a.getTitle());
            item.setContent(a.getContent());
            item.setPublishTime(a.getPublishTime());
            return item;
        }).collect(Collectors.toList());
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
