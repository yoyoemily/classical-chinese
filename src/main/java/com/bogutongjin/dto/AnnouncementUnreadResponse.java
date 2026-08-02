package com.bogutongjin.dto;

import lombok.Data;

/** 未读公告状态 */
@Data
public class AnnouncementUnreadResponse {
    /** 是否有未读 */
    private boolean hasUnread;
    /** 最新公告 ID */
    private long latestId;
    /** 最新公告标题（预留） */
    private String latestTitle;
}
