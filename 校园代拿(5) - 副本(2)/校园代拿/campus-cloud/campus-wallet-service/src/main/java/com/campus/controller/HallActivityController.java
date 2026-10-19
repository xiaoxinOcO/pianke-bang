package com.campus.controller;

import com.campus.service.HallActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hall-activities")
public class HallActivityController {

    private final HallActivityService hallActivityService;

    public HallActivityController(HallActivityService hallActivityService) {
        this.hallActivityService = hallActivityService;
    }

    /** 小程序/网页大厅轮播：未登录可看；登录则领券类活动带 claimed */
    @GetMapping
    public List<Map<String, Object>> list() {
        return hallActivityService.listPublic();
    }
}
