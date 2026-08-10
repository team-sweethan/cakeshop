package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.dto.view.MemberAdminDetailRow;
import com.cakeshop.domain.member.dto.view.MemberSummaryView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.dto.form.MemberAdminSearchCondition;
import com.cakeshop.domain.member.dto.view.MemberAdminListRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    List<MemberAdminListRow> findAdminMembers(
            @Param("condition") MemberAdminSearchCondition condition,
            @Param("size") int size,
            @Param("offset") int offset);

    long countAdminMembers(
            @Param("condition") MemberAdminSearchCondition condition);

    Optional<MemberAdminDetailRow> findAdminMemberDetail(
            @Param("memberId") Long memberId);

    int suspendActiveUser(
            @Param("memberId") Long memberId,
            @Param("suspendedReason") String suspendedReason);

    int activateSuspendedUser(
            @Param("memberId") Long memberId);

    Optional<Member> findByEmail(@Param("email") String email);

    Optional<MemberSummaryView> findSummaryByMemberId(@Param("memberId") Long memberId);

    List<MemberSummaryView> findAllSummaries(
            @Param("size") int size,
            @Param("offset") int offset);

    long countAllMembers();

    /** 주문 등 회원 전용 기능 실행 시 현재 ACTIVE 상태인지 DB 기준으로 확인한다. */
    boolean existsActiveMember(@Param("memberId") long memberId);

    List<String> findEmailsByMemberInfo(
            @Param("name") String name,
            @Param("birthDate") LocalDate birthDate,
            @Param("phone") String phone);

    Optional<Member> findPasswordRecoveryMember(
            @Param("email") String email,
            @Param("name") String name,
            @Param("birthDate") LocalDate birthDate,
            @Param("phone") String phone);

    int join(Member member);

    int update(Member member);

    Optional<String> findActivePasswordForUpdate(@Param("id") Long id);

    int updatePasswordForActiveMember(
            @Param("id") Long id,
            @Param("password") String password);

    int withdrawById(
            @Param("id") Long id,
            @Param("status") MemberStatus status);
}
