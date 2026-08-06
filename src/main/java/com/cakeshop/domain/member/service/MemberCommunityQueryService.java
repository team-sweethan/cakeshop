package com.cakeshop.domain.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.domain.member.mapper.MemberCommunityMapper;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 수민
 * 작성일 : 2026-08-07
 * 기능 : 커뮤니티 작성자 표기용 회원 조회 계약
 * 설명 : 게시글·댓글·신고 목록의 작성자 정보를 회원 ID 목록으로 한 번에 조회한다.
 * ******************************
 */
@Service
public class MemberCommunityQueryService {

    private final MemberCommunityMapper memberCommunityMapper;

    public MemberCommunityQueryService(MemberCommunityMapper memberCommunityMapper) {
        this.memberCommunityMapper = memberCommunityMapper;
    }

    /**
     * 회원 ID 목록으로 작성자 표기에 필요한 정보를 한 번에 조회한다.
     *
     * <p>존재하지 않는 ID 는 결과에서 빠질 뿐 예외로 다루지 않는다. 목록 조립이 회원 한 명 때문에
     * 실패하면 게시글 목록 전체가 보이지 않게 되기 때문이다. 빠진 ID 의 처리는 화면 정책이라
     * 호출하는 쪽이 맡는다.</p>
     */
    @Transactional(readOnly = true)
    public List<MemberCommunityView> getMembersByIds(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return memberCommunityMapper.findMembersByIds(memberIds);
    }
}
