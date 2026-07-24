package com.cakeshop.domain.community.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// /community 목록·상세·작성 (GET은 공개). 현재는 하드코딩 목업 화면이며 Model 데이터·저장은 담당자가 연결한다.
@Controller
public class CommunityController {

    @GetMapping("/community")
    public String list() {
        return "customer/community/list";
    }

    // 상세 경로는 SecurityConfig의 "/community/{id:\\d+}" 공개 규칙에 맞춰 숫자 식별자만 받는다.
    @GetMapping("/community/{postId}")
    public String detail(@PathVariable long postId) {
        return "customer/community/detail";
    }

    @GetMapping("/community/new")
    public String createForm() {
        return "customer/community/form";
    }
}
