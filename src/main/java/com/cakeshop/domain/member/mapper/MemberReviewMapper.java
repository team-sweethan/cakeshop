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
}
