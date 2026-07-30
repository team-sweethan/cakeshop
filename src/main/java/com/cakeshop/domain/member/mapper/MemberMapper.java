package com.cakeshop.domain.member.mapper;

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

    Optional<Member> findByEmail(@Param("email") String email);

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
