package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.mapper.MemberNotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-18
 * 기능 : 알림 연동용 회원 조회 계약
 * 설명 : 알림 도메인이 회원 테이블을 직접 조회하지 않고 수신 회원의 활성 상태(ACTIVE)를 연동전용 Mapper로 조회한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class MemberNotificationQueryService {

    private final MemberNotificationMapper memberNotificationMapper;

    @Transactional(readOnly = true)
    public boolean isMemberActive(Long memberId) {
        if (memberId == null || memberId <= 0) {
            return false;
        }
        return memberNotificationMapper.isMemberActive(memberId);
    }
}
