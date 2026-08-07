package com.cakeshop.domain.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.member.dto.view.MemberCouponView;
import com.cakeshop.domain.member.mapper.MemberCouponMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

/**
 * ******************************
 * 작성자 : 이정후
 * 담당자 : 수민
 * 작성일 : 2026-08-07
 * 기능 : 쿠폰 연동용 회원 조회 계약
 * 설명 : 쿠폰 도메인이 회원 테이블을 직접 조회하지 않고 회원 정보와 발급 대상 조건을 확인하도록 제공한다.
 * ******************************
 */
@Service
public class MemberCouponQueryService {

    private final MemberCouponMapper memberCouponMapper;

    public MemberCouponQueryService(MemberCouponMapper memberCouponMapper) {
        this.memberCouponMapper = memberCouponMapper;
    }

    /**
     * ******************************
     * 작성자 : 이정후
     * 담당자 : 수민
     * 작성일 : 2026-08-07
     * 기능 : 활성 쿠폰 발급 후보 회원 검색
     * 설명 : 특정 회원 쿠폰 발급 화면에서 활성 USER 회원을 이름 또는 이메일로 페이지 조회한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public PageResult<MemberCouponView> searchActiveMembers(String keyword, PageRequest pageRequest) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        return new PageResult<>(
                memberCouponMapper.findActiveMembers(
                        normalizedKeyword, pageRequest.getSize(), pageRequest.getOffset()),
                pageRequest,
                memberCouponMapper.countActiveMembers(normalizedKeyword)
        );
    }

    /**
     * ******************************
     * 작성자 : 이정후
     * 담당자 : 수민
     * 작성일 : 2026-08-07
     * 기능 : 쿠폰 발급 이력용 회원 프로필 조회
     * 설명 : 쿠폰 도메인이 발급 이력의 회원 ID로 이름·이메일·연락처·생일 정보를 조합하도록 제공한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<MemberCouponView> getMembersByIds(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return memberCouponMapper.findMembersByIds(memberIds);
    }

    /**
     * ******************************
     * 작성자 : 이정후
     * 담당자 : 수민
     * 작성일 : 2026-08-07
     * 기능 : 쿠폰 발급 회원 검색 대상 조회
     * 설명 : 쿠폰 관리자 발급 이력 목록의 이름·이메일 검색에 사용할 회원 ID를 제공한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<Long> getMemberIdsByKeyword(String keyword) {
        return memberCouponMapper.findMemberIdsByKeyword(keyword);
    }

    /**
     * ******************************
     * 작성자 : 이정후
     * 담당자 : 수민
     * 작성일 : 2026-08-07
     * 기능 : 쿠폰 발급 대상 회원 유효 여부 조회
     * 설명 : 쿠폰 도메인이 발급 이력을 생성하기 직전, 대상 회원이 활성 USER인지 다시 확인한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public boolean isActiveCouponIssuableMember(Long memberId) {
        return memberCouponMapper.existsActiveCouponIssuableMember(memberId);
    }

    /**
     * ******************************
     * 작성자 : 이정후
     * 담당자 : 수민
     * 작성일 : 2026-08-07
     * 기능 : 쿠폰 정책·주문 생성 회원 직렬화
     * 설명 : 쿠폰 발급과 주문 생성이 같은 회원의 첫 주문 여부를 판단할 때 회원 행을 먼저 잠가
     *       상태 변경과 주문 이력 생성을 같은 순서로 직렬화한다.
     * ******************************
     */
    @Transactional
    public boolean lockActiveCouponIssuableMember(Long memberId) {
        return memberCouponMapper.findActiveCouponIssuableMemberIdForUpdate(memberId) != null;
    }

    /**
     * ******************************
     * 작성자 : 이정후
     * 담당자 : 수민
     * 작성일 : 2026-08-07
     * 기능 : 전체 회원 쿠폰 발급 대상 조회
     * 설명 : 전체 회원 대상 쿠폰 등록 시 발급할 활성 USER 회원 ID를 제공한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<Long> getActiveMemberIds() {
        return memberCouponMapper.findActiveMemberIds();
    }

    /**
     * ******************************
     * 작성자 : 이정후
     * 담당자 : 수민
     * 작성일 : 2026-08-07
     * 기능 : 생일 쿠폰 발급 대상 조회
     * 설명 : 스케줄러가 해당 월 생일인 활성 USER 회원에게 쿠폰을 발급하도록 회원 ID를 제공한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<Long> getBirthdayMemberIds(int month) {
        return memberCouponMapper.findBirthdayMemberIds(month);
    }
}
