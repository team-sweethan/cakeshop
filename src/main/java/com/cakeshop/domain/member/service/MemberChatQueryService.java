package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.mapper.MemberChatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-11
 * 기능 : 채팅방 관리자용 고객 이름 조회 계약
 * 설명 : 고객 ID로 채팅방 관리자 화면의 고객 이름을 연동 전용 Mapper로 한 번에 조회한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class MemberChatQueryService {

    private final MemberChatMapper memberChatMapper;

    public boolean existsCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            return false;
        }
        try {
            String name = memberChatMapper.findCustomerNameById(customerId);
            return name != null && !name.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    public String getCustomerName(Long customerId) {
        if (customerId == null || customerId <= 0) {
            return "고객";
        }
        try {
            String name = memberChatMapper.findCustomerNameById(customerId);
            return (name != null && !name.isBlank()) ? name : "고객";
        } catch (Exception e) {
            return "고객";
        }
    }
}
