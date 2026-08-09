package com.cakeshop.domain.member.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.member.mapper.MemberReviewMapper;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 수민
 * 작성일 : 2026-08-10
 * 기능 : 후기 작성자 표기용 회원 조회 계약
 * 설명 : 후기 목록의 작성자 정보를 회원 ID 목록으로 한 번에 조회한다.
 *        계약의 근거는 docs/review/DOMAIN.md 2.6·2.7.
 * ******************************
 */
@Service
public class MemberReviewQueryService {

    private final MemberReviewMapper memberReviewMapper;

    public MemberReviewQueryService(MemberReviewMapper memberReviewMapper) {
        this.memberReviewMapper = memberReviewMapper;
    }

    /**
     * 회원 ID 목록으로 작성자 표기에 필요한 정보를 한 번에 조회한다.
     *
     * <p>후기 20건에 회원 조회를 20번 하면 N+1 이라 묶음으로만 연다(DOMAIN 2.7). 존재하지 않는
     * ID 는 결과에서 빠질 뿐 예외로 다루지 않는다 — 회원 한 명 때문에 후기 목록 전체가 보이지
     * 않게 되면 안 되기 때문이다. 빠진 ID 의 표기는 호출하는 쪽이 맡는다.</p>
     */
    @Transactional(readOnly = true)
    public List<MemberReviewView> getMembersByIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return memberReviewMapper.findMembersByIds(memberIds);
    }
}
