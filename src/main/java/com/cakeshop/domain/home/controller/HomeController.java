package com.cakeshop.domain.home.controller;

import com.cakeshop.domain.home.service.HomeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "logout", required = false) String logout,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 로그아웃 결과는 URL에 남기지 않고, 다음 홈 요청에서 한 번만 소비되는 Flash로 전달한다.
        if (logout != null) {
            redirectAttributes.addFlashAttribute("successMessage", "로그아웃되었습니다.");
            return "redirect:/";
        }

        model.addAttribute("store", homeService.getStore());
        model.addAttribute(
                "recommendedProducts",
                homeService.getRecommendedProducts()
        );
        model.addAttribute("noticeSection", homeService.getNoticeSection());
        model.addAttribute("popularSection", homeService.getPopularSection());
        return "home/main";
    }

    @GetMapping("/screens")
    public String screens() {
        return "home/screens";
    }
}
