package com.cakeshop.domain.member.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-11
 * 기능 : 채팅용 고객 이름 조회 SQL 계약
 * 설명 : members 테이블에서 채팅방 관리자 화면 표기용 고객 이름을 조회한다.
 * ******************************
 */
@Mapper
public interface MemberChatMapper {
    String findCustomerNameById(@Param("customerId") Long customerId);
}
