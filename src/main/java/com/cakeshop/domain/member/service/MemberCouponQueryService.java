package com.cakeshop.domain.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.member.dto.view.MemberCouponView;
import com.cakeshop.domain.member.mapper.MemberCouponQueryMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

/**
 * 쿠폰 발급에 필요한 회원 데이터를 제공하는 읽기 전용 Service다.
 *
 * <p>작성자: 이정후, 회원 담당자 협의 - 쿠폰 도메인의 발급 대상 조회에 사용한다.</p>
 */
@Service
public class MemberCouponQueryService {

    private final MemberCouponQueryMapper memberCouponQueryMapper;

    public MemberCouponQueryService(MemberCouponQueryMapper memberCouponQueryMapper) {
        this.memberCouponQueryMapper = memberCouponQueryMapper;
    }

    /** 이름 또는 이메일로 활성 회원을 페이지 단위로 검색한다. */
    @Transactional(readOnly = true)
    public PageResult<MemberCouponView> searchActiveMembers(String keyword, PageRequest pageRequest) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        return new PageResult<>(
                memberCouponQueryMapper.findActiveMembers(
                        normalizedKeyword, pageRequest.getSize(), pageRequest.getOffset()),
                pageRequest,
                memberCouponQueryMapper.countActiveMembers(normalizedKeyword)
        );
    }

    /** 쿠폰 후보 목록에 표시할 회원 기본 정보를 조회한다. */
    @Transactional(readOnly = true)
    public List<MemberCouponView> getMembersByIds(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return memberCouponQueryMapper.findMembersByIds(memberIds);
    }

    /** 전체 회원 대상 쿠폰 발급에 사용할 활성 회원 식별자를 조회한다. */
    @Transactional(readOnly = true)
    public List<Long> getActiveMemberIds() {
        return memberCouponQueryMapper.findActiveMemberIds();
    }

    /** 생일 쿠폰 발급에 사용할 해당 월의 활성 회원 식별자를 조회한다. */
    @Transactional(readOnly = true)
    public List<Long> getBirthdayMemberIds(int month) {
        return memberCouponQueryMapper.findBirthdayMemberIds(month);
    }
}
