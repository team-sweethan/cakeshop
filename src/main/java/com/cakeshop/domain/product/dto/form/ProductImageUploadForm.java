package com.cakeshop.domain.product.dto.form;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import org.springframework.web.multipart.MultipartFile;

/** 관리자 상품 이미지 업로드 입력값. */
@Getter
@Setter
public class ProductImageUploadForm {

    @NotNull(message = "상품 이미지를 선택해 주세요.")
    private MultipartFile imageFile;
}
