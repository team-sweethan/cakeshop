package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.view.MemberChatView;
import com.cakeshop.domain.member.mapper.MemberChatMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-11
 * 기능 : 채팅방 관리자용 고객 이름 조회 계약
 * 설명 : 고객 ID로 채팅방 관리자 화면의 고객 이름을 연동 전용 Mapper로 단건/배치 조회한다.
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
        String name = memberChatMapper.findCustomerNameById(customerId);
        return name != null && !name.isBlank();
    }

    public String getCustomerName(Long customerId) {
        if (customerId == null || customerId <= 0) {
            return "고객";
        }
        String name = memberChatMapper.findCustomerNameById(customerId);
        return (name != null && !name.isBlank()) ? name : "고객";
    }

    public Map<Long, String> getCustomerNamesMap(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MemberChatView> views = memberChatMapper.findCustomerNamesByIds(customerIds);
        if (views == null || views.isEmpty()) {
            return Collections.emptyMap();
        }
        return views.stream().collect(Collectors.toMap(
                MemberChatView::id,
                v -> (v.name() != null && !v.name().isBlank()) ? v.name() : "고객",
                (a, b) -> a
        ));
    }
}
