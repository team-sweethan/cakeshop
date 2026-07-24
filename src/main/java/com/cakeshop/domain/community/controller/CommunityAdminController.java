package com.cakeshop.domain.community.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// /admin/community 관리·게시글 제재. 현재는 하드코딩 목업이며 제재·삭제는 담당자가 연결한다.
@Controller
public class CommunityAdminController {

    @GetMapping("/admin/community")
    public String list() {
        return "admin/community/list";
    }

    @GetMapping("/admin/community/{postId}")
    public String detail(@PathVariable long postId) {
        return "admin/community/detail";
    }
}
