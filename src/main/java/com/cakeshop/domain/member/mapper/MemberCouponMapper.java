package com.cakeshop.domain.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.member.dto.view.MemberCouponView;

/**
 * ******************************
 * 작성자 : 이정후
 * 담당자 : 수민
 * 작성일 : 2026-08-07
 * 기능 : 쿠폰 연동용 회원 조회 SQL 계약
 * 설명 : 쿠폰 도메인이 필요한 회원 조회 SQL을 회원 도메인에서 제공한다.
 * ******************************
 */
@Mapper
public interface MemberCouponMapper {

    List<MemberCouponView> findActiveMembers(
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countActiveMembers(@Param("keyword") String keyword);

    List<MemberCouponView> findMembersByIds(@Param("memberIds") List<Long> memberIds);

    List<Long> findMemberIdsByKeyword(@Param("keyword") String keyword);

    boolean existsActiveCouponIssuableMember(@Param("memberId") Long memberId);

    /** 쿠폰 발급 정책과 주문 생성이 경합할 때 회원 행을 잠근다. */
    Long findActiveCouponIssuableMemberIdForUpdate(@Param("memberId") Long memberId);

    List<Long> findActiveMemberIds();

    List<Long> findBirthdayMemberIds(@Param("month") int month);
}
