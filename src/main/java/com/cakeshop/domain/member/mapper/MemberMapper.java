package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    Optional<Member> findByEmail(@Param("email") String email);

    List<String> findEmailsByMemberInfo(
            @Param("name") String name,
            @Param("birthDate") LocalDate birthDate,
            @Param("phone") String phone);

    int join(Member member);

    int update(Member member);

    int withdrawById(
            @Param("id") Long id,
            @Param("status") MemberStatus status);
}
