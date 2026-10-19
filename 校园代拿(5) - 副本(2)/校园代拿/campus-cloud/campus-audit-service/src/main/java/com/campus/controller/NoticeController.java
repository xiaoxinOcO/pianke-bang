package com.campus.controller;

import com.campus.model.Notice;
import com.campus.service.NoticeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public List<Notice> listPublished() {
        return noticeService.publicNotices();
    }
}

