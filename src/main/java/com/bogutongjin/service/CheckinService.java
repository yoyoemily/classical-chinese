package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.entity.UserCheckin;
import com.bogutongjin.mapper.UserCheckinMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final UserCheckinMapper checkinMapper;

    public List<String> getCheckinRecords(Long userId, Integer year, Integer month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        return checkinMapper.selectList(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .ge(UserCheckin::getCheckinDate, start)
                        .le(UserCheckin::getCheckinDate, end)
                        .orderByAsc(UserCheckin::getCheckinDate))
                .stream()
                .map(c -> c.getCheckinDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .collect(Collectors.toList());
    }
}
