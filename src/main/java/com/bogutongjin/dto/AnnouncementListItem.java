package com.bogutongjin.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 公告列表项 */
@Data
public class AnnouncementListItem {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime publishTime;
}
