package com.cakeshop.domain.product.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.cakeshop.domain.product.dto.view.ProductOptionManagementView;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductOptionAdminService;
import com.cakeshop.global.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductOptionAdminController.class)
@Import(SecurityConfig.class)
class ProductOptionAdminScreenRenderingTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductOptionAdminService productOptionAdminService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void options_generalProduct_disablesRequiredSelection()
            throws Exception {
        givenManagement(ProductType.GENERAL);

        mockMvc.perform(get("/admin/products/1/options"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "일반 상품은 선택 옵션만 설정할 수 있습니다."
                )))
                .andExpect(content().string(containsString(
                        "disabled=\"disabled\""
                )));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void options_customProduct_enablesRequiredSelection()
            throws Exception {
        givenManagement(ProductType.CUSTOM);

        mockMvc.perform(get("/admin/products/1/options"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(
                        "일반 상품은 선택 옵션만 설정할 수 있습니다."
                ))))
                .andExpect(content().string(not(containsString(
                        "disabled=\"disabled\""
                ))));
    }

    private void givenManagement(ProductType productType) {
        when(productOptionAdminService.getOptions(1L))
                .thenReturn(new ProductOptionManagementView(
                        1L,
                        "테스트 케이크",
                        productType,
                        List.of()
                ));
    }
}
