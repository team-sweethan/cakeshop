package com.cakeshop.domain.member.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.member.dto.view.MemberReviewView;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 수민
 * 작성일 : 2026-08-10
 * 기능 : 후기 작성자 표기용 회원 조회 SQL 계약
 * 설명 : 리뷰 Mapper 가 members 를 직접 참조하지 않도록 회원 도메인에 분리해 둔다.
 *        담당자의 기존 파일을 건드리지 않으려고 MemberMapper 에 얹지 않고 새로 뒀다.
 * ******************************
 */
@Mapper
public interface MemberReviewMapper {

    List<MemberReviewView> findMembersByIds(@Param("memberIds") Collection<Long> memberIds);

    /**
     * ******************************
     * 작성자 : HyunGyu-Cho
     * 담당자 : 수민
     * 작성일 : 2026-08-10
     * 기능 : 관리자 후기 검색의 작성자명 조건
     * 설명 : 닉네임 부분 일치 회원 ID 를 돌려준다. keyword 는 호출한 쪽이 LIKE 이스케이프를
     *        끝낸 값이어야 하고, SQL 은 ESCAPE '!' 로 받는다.
     * ******************************
     */
    List<Long> findMemberIdsByNickname(@Param("keyword") String keyword);

    /**
     * ******************************
     * 작성자 : HyunGyu-Cho
     * 담당자 : 수민
     * 작성일 : 2026-08-10
     * 기능 : 신규 후기 알림을 받을 관리자 조회
     * 설명 : role 이 ADMIN 이고 status 가 ACTIVE 인 회원 ID 를 돌려준다. 관리자 판별은 회원
     *        도메인의 사실이므로 리뷰가 members 를 직접 읽지 않는다.
     *        계약의 근거는 docs/review/specs/review-notification.md D2.
     * ******************************
     */
    List<Long> findActiveAdminIds();
}
