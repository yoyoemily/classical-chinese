package com.bogutongjin.dto;

import lombok.Data;

/** 公告列表项（不含正文，减少网络传输） */
@Data
public class AnnouncementListItem {
    private Long id;
    private String title;
    /** 是否置顶 */
    private Boolean isPinned;
    /** 发布时间，格式 yyyy-MM-dd HH:mm */
    private String publishTime;
}
