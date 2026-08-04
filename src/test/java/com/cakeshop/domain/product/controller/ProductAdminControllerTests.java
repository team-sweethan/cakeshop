package com.cakeshop.domain.product.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;

import com.cakeshop.domain.product.dto.form.AdminStockFilter;
import com.cakeshop.domain.product.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductForm;
import com.cakeshop.domain.product.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.dto.view.ProductCategoryOptionView;
import com.cakeshop.domain.product.service.ProductAdminService;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductAdminControllerTests {

    @Test
    void productsBindsSearchConditionAndPageRequest()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        PageResult<ProductAdminListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(2, 10),
                        15
                );

        when(productAdminService.getProducts(
                any(),
                any()
        )).thenReturn(pageResult);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        get("/admin/products")
                                .param("keyword", "딸기")
                                .param("type", "GENERAL")
                                .param("status", "ACTIVE")
                                .param("stock", "AVAILABLE")
                                .param("page", "2")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/list"
                ))
                .andExpect(model().attribute(
                        "pageResult",
                        pageResult
                ))
                .andExpect(model().attributeExists(
                        "condition",
                        "productTypes",
                        "productStatuses",
                        "stockFilters"
                ));

        ArgumentCaptor<ProductAdminSearchCondition>
                conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductAdminSearchCondition.class
                );

        ArgumentCaptor<PageRequest> pageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);

        verify(productAdminService).getProducts(
                conditionCaptor.capture(),
                pageCaptor.capture()
        );

        ProductAdminSearchCondition condition =
                conditionCaptor.getValue();

        assertThat(condition.getKeyword())
                .isEqualTo("딸기");
        assertThat(condition.getType())
                .isEqualTo(ProductType.GENERAL);
        assertThat(condition.getStatus())
                .isEqualTo(ProductStatus.ACTIVE);
        assertThat(condition.getStock())
                .isEqualTo(AdminStockFilter.AVAILABLE);

        PageRequest pageRequest =
                pageCaptor.getValue();

        assertThat(pageRequest.getPage()).isEqualTo(2);
        assertThat(pageRequest.getSize()).isEqualTo(10);
        assertThat(pageRequest.getOffset()).isEqualTo(10);
    }

    @Test
    void changeStatusRedirectsToProductList()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        // 판매 중지 요청을 전송한다.
        mockMvc.perform(
                        post("/admin/products/{productId}/status", 1L)
                                .param("status", "INACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 판매를 중지했습니다."
                ));

        // 요청값이 Service로 정확하게 전달됐는지 확인한다.
        verify(productAdminService).changeProductStatus(
                1L,
                ProductStatus.INACTIVE
        );
    }

    @Test
    void startSaleUsesActiveStatus()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        // 판매 시작 요청을 전송한다.
        mockMvc.perform(
                        post("/admin/products/{productId}/status", 1L)
                                .param("status", "ACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 판매를 시작했습니다."
                ));

        verify(productAdminService).changeProductStatus(
                1L,
                ProductStatus.ACTIVE
        );
    }

    @Test
    void startSale_requiredPolicyError_redirectsWithAlert()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);
        doThrow(new BusinessException(
                ProductErrorCode.REQUIRED_OPTION_GROUP_EMPTY
        )).when(productAdminService).changeProductStatus(
                1L,
                ProductStatus.ACTIVE
        );

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        post("/admin/products/{productId}/status", 1L)
                                .param("status", "ACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products"
                ))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "필수 옵션 그룹에는 하나 이상의 활성 옵션이 필요합니다."
                ));
    }

    @Test
    void createFormReturnsProductFormPage()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        ProductCategoryOptionView category =
                new ProductCategoryOptionView(
                        1L,
                        "케이크"
                );

        when(productAdminService.getActiveCategories())
                .thenReturn(List.of(category));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(get("/admin/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/form"
                ))
                .andExpect(model().attributeExists(
                        "productForm",
                        "categories",
                        "productTypes"
                ))
                .andExpect(model().attribute(
                        "editMode",
                        false
                ))
                .andExpect(model().attribute(
                        "formAction",
                        "/admin/products"
                ));
    }

    @Test
    void createProductRedirectsToProductList()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        when(productAdminService.createProduct(any()))
                .thenReturn(10L);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        post("/admin/products")
                                .param("categoryId", "1")
                                .param("name", "신규 케이크")
                                .param("description", "상품 설명")
                                .param("basePrice", "35000")
                                .param("stockQuantity", "10")
                                .param("productType", "GENERAL")
                                .param("preparationDays", "0")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품을 등록했습니다."
                ));

        ArgumentCaptor<ProductForm> formCaptor =
                ArgumentCaptor.forClass(ProductForm.class);

        verify(productAdminService)
                .createProduct(formCaptor.capture());

        ProductForm form = formCaptor.getValue();

        assertThat(form.getCategoryId()).isEqualTo(1L);
        assertThat(form.getName()).isEqualTo("신규 케이크");
        assertThat(form.getBasePrice())
                .isEqualByComparingTo("35000");
        assertThat(form.getProductType())
                .isEqualTo(ProductType.GENERAL);
    }

    @Test
    void invalidProductFormReturnsCreatePage()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        when(productAdminService.getActiveCategories())
                .thenReturn(List.of(
                        new ProductCategoryOptionView(
                                1L,
                                "케이크"
                        )
                ));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        post("/admin/products")
                                .param("name", "")
                                .param("basePrice", "-1")
                                .param("preparationDays", "-1")
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/form"
                ))
                .andExpect(model().attributeHasErrors(
                        "productForm"
                ))
                .andExpect(model().attributeExists(
                        "categories",
                        "productTypes"
                ));

        verify(productAdminService, never())
                .createProduct(any());
    }

    @Test
    void createProduct_customPreparationZero_returnsCreatePage()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        when(productAdminService.getActiveCategories())
                .thenReturn(List.of(
                        new ProductCategoryOptionView(
                                1L,
                                "케이크"
                        )
                ));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        post("/admin/products")
                                .param("categoryId", "1")
                                .param("name", "주문 제작 케이크")
                                .param("basePrice", "55000")
                                .param("productType", "CUSTOM")
                                .param("preparationDays", "0")
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/form"
                ))
                .andExpect(model().attributeHasFieldErrors(
                        "productForm",
                        "preparationPolicyValid"
                ));

        verify(productAdminService, never())
                .createProduct(any());
    }

    @Test
    void editFormReturnsExistingProductInformation()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        ProductForm productForm = validProductForm();
        ProductCategoryOptionView category =
                new ProductCategoryOptionView(
                        1L,
                        "케이크"
                );

        when(productAdminService.getProductForm(1L))
                .thenReturn(productForm);
        when(productAdminService.getActiveCategories())
                .thenReturn(List.of(category));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        get("/admin/products/{productId}/edit", 1L)
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/form"
                ))
                .andExpect(model().attribute(
                        "productForm",
                        productForm
                ))
                .andExpect(model().attribute(
                        "productId",
                        1L
                ))
                .andExpect(model().attribute(
                        "editMode",
                        true
                ))
                .andExpect(model().attribute(
                        "formAction",
                        "/admin/products/1"
                ))
                .andExpect(model().attributeExists(
                        "categories",
                        "productTypes",
                        "productImages",
                        "imageUploadForm"
                ));

        verify(productAdminService)
                .getProductForm(1L);
        verify(productAdminService)
                .getProductImages(1L);
    }

    @Test
    void updateProductRedirectsToProductList()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        post("/admin/products/{productId}", 1L)
                                .param("categoryId", "1")
                                .param("name", "수정 케이크")
                                .param("description", "수정된 상품 설명")
                                .param("basePrice", "45000")
                                .param("stockQuantity", "5")
                                .param("originalStockQuantity", "10")
                                .param("productType", "CUSTOM")
                                .param("preparationDays", "3")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 정보를 수정했습니다."
                ));

        ArgumentCaptor<ProductForm> formCaptor =
                ArgumentCaptor.forClass(ProductForm.class);

        verify(productAdminService).updateProduct(
                eq(1L),
                formCaptor.capture()
        );

        ProductForm form = formCaptor.getValue();

        assertThat(form.getCategoryId()).isEqualTo(1L);
        assertThat(form.getName()).isEqualTo("수정 케이크");
        assertThat(form.getDescription())
                .isEqualTo("수정된 상품 설명");
        assertThat(form.getBasePrice())
                .isEqualByComparingTo("45000");
        assertThat(form.getStockQuantity()).isEqualTo(5);
        assertThat(form.getOriginalStockQuantity())
                .isEqualTo(10);
        assertThat(form.getProductType())
                .isEqualTo(ProductType.CUSTOM);
        assertThat(form.getPreparationDays()).isEqualTo(3);
    }

    @Test
    void invalidUpdateFormReturnsEditPage()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        when(productAdminService.getActiveCategories())
                .thenReturn(List.of(
                        new ProductCategoryOptionView(
                                1L,
                                "케이크"
                        )
                ));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        post("/admin/products/{productId}", 1L)
                                .param("name", "")
                                .param("basePrice", "-1")
                                .param("preparationDays", "-1")
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/form"
                ))
                .andExpect(model().attributeHasErrors(
                        "productForm"
                ))
                .andExpect(model().attribute(
                        "productId",
                        1L
                ))
                .andExpect(model().attribute(
                        "editMode",
                        true
                ))
                .andExpect(model().attribute(
                        "formAction",
                        "/admin/products/1"
                ))
                .andExpect(model().attributeExists(
                        "categories",
                        "productTypes",
                        "productImages",
                        "imageUploadForm"
                ));

        verify(productAdminService, never())
                .updateProduct(anyLong(), any());
    }

    private ProductForm validProductForm() {
        ProductForm form = new ProductForm();

        form.setCategoryId(1L);
        form.setName("기존 케이크");
        form.setDescription("기존 상품 설명");
        form.setBasePrice(
                BigDecimal.valueOf(35_000)
        );
        form.setStockQuantity(10);
        form.setOriginalStockQuantity(10);
        form.setProductType(ProductType.GENERAL);
        form.setPreparationDays(0);

        return form;
    }
}
