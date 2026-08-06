package com.cakeshop.domain.product.mapper;

import com.cakeshop.domain.product.dto.view.ProductRatingSummary;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 시은
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 평점 집계
 * 설명 : products 의 평균 평점과 후기 수를 다시 계산해 반영한다. 조각 2(#33).
 * ******************************
 *
 * <p>이 매퍼가 {@code reviews} 를 직접 읽는 것은 {@code docs/conventions.md} 15절의 예외에
 * 해당한다 — <b>자기 도메인이 소유한 파생 컬럼을 유지하기 위한 집계 읽기</b>다. 표시·검색·업무
 * 규칙 판단은 여전히 금지다.
 *
 * <p>잠금 문장은 여기 두지 않는다. 기존 {@code ProductMapper.findSalesInfoByIdForUpdate} 를
 * 재사용한다. 복제하면 한쪽만 바뀌는 날 두 경로의 잠금이 갈라지고, 두 파일을 함께 열어 본
 * 사람이 없어 아무도 눈치채지 못한다.
 */
@Mapper
public interface ProductReviewMapper {

    /**
     * 공개 후기만으로 평균 평점과 후기 수를 센다.
     *
     * <p><b>잠금 읽기여야 한다.</b> MariaDB 기본 격리 수준이 {@code REPEATABLE READ} 라
     * 평범한 SELECT 로 두면 트랜잭션의 첫 SELECT 에서 스냅샷이 굳는다. 후기 등록은 잠금을
     * 잡기 전에 자격 검증을 평범한 SELECT 로 하므로, 동시 등록 두 건 중 뒤엣것이 먼저 커밋된
     * 후기를 보지 못하고 실제보다 작은 건수로 덮어쓴다.
     */
    ProductRatingSummary summarizeForUpdate(@Param("productId") long productId);

    /** 집계 결과를 {@code products} 에 반영한다. */
    int updateRating(
            @Param("productId") long productId,
            @Param("averageRating") java.math.BigDecimal averageRating,
            @Param("reviewCount") long reviewCount
    );
}
