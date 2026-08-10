package com.cakeshop.domain.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.member.dto.view.MemberCommunityView;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 수민
 * 작성일 : 2026-08-07
 * 기능 : 커뮤니티 작성자 표기용 회원 조회 SQL 계약
 * 설명 : 커뮤니티 Mapper 가 members 를 직접 참조하지 않도록 회원 도메인에 분리해 둔다.
 * ******************************
 */
@Mapper
public interface MemberCommunityMapper {

    List<MemberCommunityView> findMembersByIds(@Param("memberIds") List<Long> memberIds);
}
