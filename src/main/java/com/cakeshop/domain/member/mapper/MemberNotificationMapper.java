package com.cakeshop.domain.member.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-18
 * 기능 : 알림용 수신 회원 및 관리자 조회 SQL 계약
 * 설명 : members 테이블에서 알림 전파 전 수신 회원의 계정 활성 상태(ACTIVE) 및 활성 관리자 목록을 조회한다.
 * ******************************
 */
@Mapper
public interface MemberNotificationMapper {

    // 수신 회원의 계정 활성 상태(ACTIVE) 여부 확인
    boolean isMemberActive(@Param("memberId") Long memberId);

    // 활성화된 관리자 회원 ID 목록 조회 (ADMIN 권한 및 ACTIVE 상태)
    List<Long> findActiveAdminIds();
}
