package com.cakeshop.domain.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.member.dto.view.MemberCouponView;

/** 쿠폰 도메인에 회원 조회 계약을 제공하는 읽기 전용 Mapper다. */
/**
 * 쿠폰 발급을 위한 회원 조회 SQL 계약이다.
 *
 * <p>작성자: 이정후, 회원 담당자 협의 - 쿠폰 도메인이 회원 Mapper를 직접 참조하지 않도록 분리한다.</p>
 */
@Mapper
public interface MemberCouponQueryMapper {

    List<MemberCouponView> findActiveMembers(
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countActiveMembers(@Param("keyword") String keyword);

    List<MemberCouponView> findMembersByIds(@Param("memberIds") List<Long> memberIds);

    List<Long> findActiveMemberIds();

    List<Long> findBirthdayMemberIds(@Param("month") int month);
}
