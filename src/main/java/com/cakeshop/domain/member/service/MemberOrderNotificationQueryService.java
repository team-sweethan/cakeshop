package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.mapper.MemberOrderNotificationMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-18
 * 기능 : 주문 알림 연동 전용 회원 조회 계약
 * 설명 : 주문 알림 전송 시 회원 테이블을 직접 조회하지 않고 활성 관리자 목록을 연동전용 Mapper로 조회한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class MemberOrderNotificationQueryService {

    private final MemberOrderNotificationMapper memberOrderNotificationMapper;

    @Transactional(readOnly = true)
    public List<Long> findActiveAdminIds() {
        return memberOrderNotificationMapper.findActiveAdminIds();
    }
}
