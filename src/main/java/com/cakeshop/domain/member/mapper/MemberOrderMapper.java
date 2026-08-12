package com.cakeshop.domain.member.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 수민
 * 작성일 : 2026-08-11
 * 기능 : 주문 관리자 권한 확인 SQL 계약
 * 설명 : 주문·결제 도메인이 members 테이블을 직접 조회하지 않고 현재 활성 ADMIN 여부를 확인하도록 제공한다.
 * ******************************
 */
@Mapper
public interface MemberOrderMapper {

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 수민
     * 작성일 : 2026-08-11
     * 기능 : 활성 관리자 존재 확인
     * 설명 : 회원 ID가 ADMIN 역할이며 ACTIVE 상태인지 DB 기준으로 확인한다.
     * ******************************
     */
    boolean existsActiveAdmin(@Param("memberId") long memberId);
}
