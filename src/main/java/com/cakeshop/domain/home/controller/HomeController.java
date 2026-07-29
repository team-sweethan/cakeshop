package com.cakeshop.domain.home.controller;

import com.cakeshop.domain.home.service.HomeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "logout", required = false) String logout,
            Model model) {
        model.addAttribute("store", homeService.getStore());
        model.addAttribute(
                "recommendedProducts",
                homeService.getRecommendedProducts()
        );
        if (logout != null) {
            model.addAttribute("successMessage", "로그아웃되었습니다.");
        }
        return "home/main";
    }

    @GetMapping("/screens")
    public String screens() {
        return "home/screens";
    }
}
