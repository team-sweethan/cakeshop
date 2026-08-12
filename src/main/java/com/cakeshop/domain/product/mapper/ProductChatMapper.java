package com.cakeshop.domain.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 시은
 * 작성일 : 2026-08-11
 * 기능 : 채팅 문의용 상품 상태 조회 SQL 계약
 * 설명 : products 테이블에서 채팅 문의 대상 상품이 존재하고 유효한지 검증한다.
 * ******************************
 */
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductChatMapper {
    int countActiveProductById(@Param("productId") Long productId);
    String findProductNameById(@Param("productId") Long productId);
    List<Map<String, Object>> findProductNamesByIds(@Param("productIds") List<Long> productIds);
}
