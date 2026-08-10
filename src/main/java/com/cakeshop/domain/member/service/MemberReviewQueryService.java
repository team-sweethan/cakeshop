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

    @Transactional(readOnly = true)
    public List<MemberReviewView> getMembersByIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return memberReviewMapper.findMembersByIds(memberIds);
    }
}
