package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.mapper.MemberOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 수민
 * 작성일 : 2026-08-11
 * 기능 : 주문 관리자 권한 조회 계약
 * 설명 : 주문·결제의 관리자 상태 변경 전에 현재 활성 ADMIN 여부를 회원 도메인 기준으로 확인한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class MemberOrderQueryService {

    private final MemberOrderMapper memberOrderMapper;

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 수민
     * 작성일 : 2026-08-11
     * 기능 : 현재 활성 관리자 확인
     * 설명 : 세션에 남아 있는 역할이 아니라 members의 ADMIN 역할과 ACTIVE 상태를 함께 확인한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public boolean isActiveAdmin(long memberId) {
        return memberId > 0 && memberOrderMapper.existsActiveAdmin(memberId);
    }
}
