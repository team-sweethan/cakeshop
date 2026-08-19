package com.cakeshop.domain.store.controller;

import com.cakeshop.domain.store.service.StoreService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(StoreService.class)
public class StorePublicModelWebConfig implements WebMvcConfigurer {

    private final StoreService storeService;

    public StorePublicModelWebConfig(StoreService storeService) {
        this.storeService = storeService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new StorePublicModelInterceptor(storeService));
    }
}
