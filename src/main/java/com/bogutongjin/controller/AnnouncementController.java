package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.dto.AnnouncementDetail;
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

    /** 公告列表（不含正文，置顶优先 + 发布时间降序） */
    @GetMapping
    public Result<List<AnnouncementListItem>> listAnnouncements() {
        return Result.ok(announcementService.listAll());
    }

    /** 公告详情（含正文） */
    @GetMapping("/{id}")
    public Result<AnnouncementDetail> getAnnouncement(@PathVariable Long id) {
        AnnouncementDetail detail = announcementService.getDetail(id);
        if (detail == null) {
            return Result.fail(404, "公告不存在");
        }
        return Result.ok(detail);
    }

    /** 未读公告状态（传入客户端最后已读 ID，服务端判断是否有未读） */
    @GetMapping("/unread")
    public Result<AnnouncementUnreadResponse> getUnreadStatus(
            @RequestParam(defaultValue = "0") long lastReadId) {
        return Result.ok(announcementService.getUnreadStatus(lastReadId));
    }
}
