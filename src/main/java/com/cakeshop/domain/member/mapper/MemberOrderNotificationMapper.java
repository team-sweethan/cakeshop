package com.cakeshop.domain.member.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-18
 * 기능 : 주문 알림 연동 전용 관리자 회원 조회 SQL 계약
 * 설명 : members 테이블에서 주문 알림 전파 전 활성화된 관리자 회원 ID 목록을 조회한다.
 * ******************************
 */
@Mapper
public interface MemberOrderNotificationMapper {

    /** 활성화된 관리자 회원 ID 목록 조회 */
    List<Long> findActiveAdminIds();
}
