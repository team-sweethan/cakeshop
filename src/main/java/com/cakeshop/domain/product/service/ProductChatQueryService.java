package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.mapper.ProductChatMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 시은
 * 작성일 : 2026-08-11
 * 기능 : 채팅방 문의 상품 검증 계약
 * 설명 : 채팅 문의 시 전달받은 상품이 존재하는지 연동 전용 Mapper로 검증한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class ProductChatQueryService {

    private final ProductChatMapper productChatMapper;

    public void validateProductForChat(Long productId) {
        if (productId == null || productId <= 0) {
            return;
        }
        int count = productChatMapper.countActiveProductById(productId);
        if (count <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    public String getProductName(Long productId) {
        if (productId == null || productId <= 0) {
            return null;
        }
        return productChatMapper.findProductNameById(productId);
    }

    public java.util.Map<Long, String> getProductNamesMap(java.util.List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.List<Long> validIds = productIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (validIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.List<java.util.Map<String, Object>> rows = productChatMapper.findProductNamesByIds(validIds);
        if (rows == null || rows.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<Long, String> result = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : rows) {
            Number idNum = (Number) row.get("id");
            String name = (String) row.get("name");
            if (idNum != null && name != null) {
                result.put(idNum.longValue(), name);
            }
        }
        return result;
    }
}
