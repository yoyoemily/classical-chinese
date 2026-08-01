package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.dto.AnnouncementListItem;
import com.bogutongjin.dto.AnnouncementUnreadResponse;
import com.bogutongjin.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 公告列表（按发布时间降序） */
    @GetMapping
    public Result<List<AnnouncementListItem>> listAnnouncements() {
        return Result.ok(announcementService.listAll());
    }

    /** 未读公告状态（传入客户端最后已读 ID，服务端判断是否有未读） */
    @GetMapping("/unread")
    public Result<AnnouncementUnreadResponse> getUnreadStatus(
            @RequestParam(defaultValue = "0") long lastReadId) {
        return Result.ok(announcementService.getUnreadStatus(lastReadId));
    }
}
