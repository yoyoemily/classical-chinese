package com.bogutongjin.dto;

import lombok.Data;

/** 公告详情（含正文） */
@Data
public class AnnouncementDetail {
    private Long id;
    private String title;
    private String content;
    /** 发布时间，格式 yyyy-MM-dd HH:mm */
    private String publishTime;
}
