package com.cakeshop.domain.coupon.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * coupons 테이블 한 행을 표현하는 MyBatis POJO다.
 * 화면 검증 규칙은 Form DTO에 두고, 이 객체는 Mapper의 조회·저장 파라미터로만 사용한다.
 */
@Getter
@Setter
public class Coupon {
    private Long id;                            // 쿠폰번호
    private String name;                        // 쿠폰 이름
    private DiscountType discountType;          // 할인 방식
    private BigDecimal discountValue;           // 할인 값 - 금액/비율
    private BigDecimal minimumOrderAmount;      // 최소 주문 금액
    private BigDecimal maximumDiscountAmount;   // 최대 할인 금액
    private Integer totalQuantity;              // 발급 가능한 전체 쿠폰 수량
    private Integer issuedQuantity;             // 현재까지 발급된 쿠폰 수량
    private LocalDateTime startsAt;             // 쿠폰 발급 시작 일시
    private LocalDateTime expiresAt;            // 쿠폰 만료 일시
    private CouponStatus status;                // 관리자가 설정하는 발급 허용 상태
    private Long createdBy;                     // 쿠폰 생성 관리자 회원
    private LocalDateTime createdAt;            // 쿠폰 생성 일시
    private LocalDateTime updatedAt;            // 쿠폰 최종 수정 일시
}
