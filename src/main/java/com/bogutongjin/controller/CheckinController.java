package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.CheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @GetMapping
    public Result<List<String>> getCheckinRecords(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return Result.ok(checkinService.getCheckinRecords(userId, year, month));
    }
}
