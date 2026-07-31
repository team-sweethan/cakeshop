package com.cakeshop.domain.product.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import com.cakeshop.domain.product.admin.dto.form.ProductOptionForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionGroupForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionMoveDirection;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionManagementView;
import com.cakeshop.domain.product.admin.service.ProductOptionAdminService;
import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductOptionAdminControllerTests {

    @Test
    void options_existingProduct_returnsManagementPage()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );

        ProductOptionManagementView management =
                new ProductOptionManagementView(
                        1L,
                        "레터링 케이크",
                        List.of()
                );

        when(service.getOptions(1L))
                .thenReturn(management);

        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/admin/products/1/options"))
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/options"
                ))
                .andExpect(model().attribute(
                        "management",
                        management
                ))
                .andExpect(model().attributeExists(
                        "selectionTypes",
                        "optionStatuses"
                ));
    }

    @Test
    void createOptionGroup_validInput_redirectsToOptions()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        when(service.createOptionGroup(
                org.mockito.ArgumentMatchers.eq(1L),
                any(ProductOptionGroupForm.class)
        )).thenReturn(10L);

        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post("/admin/products/1/option-groups")
                                .param("name", "크기")
                                .param("required", "true")
                                .param("selectionType", "SINGLE")
                                .param("status", "ACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "옵션 그룹을 등록했습니다."
                ))
                .andExpect(flash().attribute(
                        "openGroup",
                        10L
                ));

        ArgumentCaptor<ProductOptionGroupForm> captor =
                ArgumentCaptor.forClass(
                        ProductOptionGroupForm.class
                );

        verify(service).createOptionGroup(
                org.mockito.ArgumentMatchers.eq(1L),
                captor.capture()
        );

        assertThat(captor.getValue().getName())
                .isEqualTo("크기");
        assertThat(captor.getValue().isRequired()).isTrue();
    }

    @Test
    void createOptionGroup_invalidInput_doesNotCallService()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post("/admin/products/1/option-groups")
                                .param("name", "")
                                .param("selectionType", "SINGLE")
                                .param("status", "ACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ))
                .andExpect(flash().attributeExists(
                        "errorMessage"
                ))
                .andExpect(flash().attribute(
                        "openCreate",
                        true
                ));

        verify(service, never())
                .createOptionGroup(
                        anyLong(),
                        any(ProductOptionGroupForm.class)
                );
    }

    @Test
    void updateOptionGroup_requiredPolicyError_redirectsWithAlert()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        doThrow(new BusinessException(
                ProductErrorCode.REQUIRED_OPTION_GROUP_EMPTY
        )).when(service).updateOptionGroup(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                any(ProductOptionGroupForm.class)
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post(
                                "/admin/products/1"
                                        + "/option-groups/10"
                        )
                                .param("name", "크기")
                                .param("required", "true")
                                .param("selectionType", "SINGLE")
                                .param("status", "INACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "필수 옵션 그룹에는 하나 이상의 활성 옵션이 필요합니다."
                ))
                .andExpect(flash().attribute(
                        "openGroup",
                        10L
                ));
    }

    @Test
    void createOption_validInput_redirectsToOptions()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post(
                                "/admin/products/1"
                                        + "/option-groups/10/options"
                        )
                                .param("name", "2호")
                                .param("additionalPrice", "10000")
                                .param("status", "ACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 옵션을 등록했습니다."
                ))
                .andExpect(flash().attribute(
                        "openGroup",
                        10L
                ));

        verify(service).createOption(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                any(ProductOptionForm.class)
        );
    }

    @Test
    void updateOption_validInput_passesPathOwnershipIds()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post(
                                "/admin/products/1"
                                        + "/option-groups/10"
                                        + "/options/20"
                        )
                                .param("name", "2호")
                                .param("additionalPrice", "10000")
                                .param("status", "INACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ))
                .andExpect(flash().attribute(
                        "openGroup",
                        10L
                ));

        ArgumentCaptor<ProductOptionForm> captor =
                ArgumentCaptor.forClass(
                        ProductOptionForm.class
                );

        verify(service).updateOption(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                captor.capture()
        );

        assertThat(captor.getValue().getStatus())
                .isEqualTo(ProductOptionStatus.INACTIVE);
        assertThat(captor.getValue().getAdditionalPrice())
                .isEqualByComparingTo("10000");
    }

    @Test
    void updateOption_requiredPolicyError_redirectsWithAlert()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        doThrow(new BusinessException(
                ProductErrorCode.REQUIRED_OPTION_GROUP_EMPTY
        )).when(service).updateOption(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                any(ProductOptionForm.class)
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post(
                                "/admin/products/1"
                                        + "/option-groups/10"
                                        + "/options/20"
                        )
                                .param("name", "2호")
                                .param("additionalPrice", "10000")
                                .param("status", "INACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "필수 옵션 그룹에는 하나 이상의 활성 옵션이 필요합니다."
                ))
                .andExpect(flash().attribute(
                        "openGroup",
                        10L
                ));
    }

    @Test
    void moveOptionGroup_down_passesDirection()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post(
                                "/admin/products/1"
                                        + "/option-groups/10/order"
                        )
                                .param("direction", "DOWN")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ));

        verify(service).moveOptionGroup(
                1L,
                10L,
                ProductOptionMoveDirection.DOWN
        );
    }

    @Test
    void moveOption_up_passesOwnershipIdsAndDirection()
            throws Exception {
        ProductOptionAdminService service =
                org.mockito.Mockito.mock(
                        ProductOptionAdminService.class
                );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        post(
                                "/admin/products/1"
                                        + "/option-groups/10"
                                        + "/options/20/order"
                        )
                                .param("direction", "UP")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/options"
                ))
                .andExpect(flash().attribute(
                        "openGroup",
                        10L
                ));

        verify(service).moveOption(
                1L,
                10L,
                20L,
                ProductOptionMoveDirection.UP
        );
    }

    private MockMvc mockMvc(
            ProductOptionAdminService service
    ) {
        return MockMvcBuilders
                .standaloneSetup(
                        new ProductOptionAdminController(service)
                )
                .build();
    }
}
