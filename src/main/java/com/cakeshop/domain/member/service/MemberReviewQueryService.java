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

    /**
     * ******************************
     * 작성자 : HyunGyu-Cho
     * 담당자 : 수민
     * 작성일 : 2026-08-10
     * 기능 : 관리자 후기 검색의 작성자명 조건
     * 설명 : 닉네임이 부분 일치하는 회원 ID 를 돌려준다. 빈 목록은 "조건에 맞는 회원이 없다"는
     *        뜻이므로, 조건을 걸지 않는 경우는 호출한 쪽이 이 메서드를 부르지 않는 것으로 가른다.
     *        계약의 근거는 docs/review/specs/review-admin.md C2.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<Long> findMemberIdsByNickname(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();

        if (normalized.isEmpty()) {
            return List.of();
        }

        return memberReviewMapper.findMemberIdsByNickname(escapeLikeKeyword(normalized));
    }

    /**
     * ******************************
     * 작성자 : HyunGyu-Cho
     * 담당자 : 수민
     * 작성일 : 2026-08-10
     * 기능 : 신규 후기 알림을 받을 관리자 조회
     * 설명 : 활성 관리자 전원의 회원 ID 를 돌려준다. 알림은 receiverId 가 필수라 받는 사람을
     *        정해야 보낼 수 있고, members 에 대표 관리자를 가리키는 컬럼이 없다.
     *        계약의 근거는 docs/review/specs/review-notification.md D2.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<Long> findActiveAdminIds() {
        return memberReviewMapper.findActiveAdminIds();
    }

    // '!' 를 먼저 바꾸지 않으면 뒤에서 만들어 낸 '!%' 를 다시 이스케이프해 패턴이 어긋난다.
    private static String escapeLikeKeyword(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
