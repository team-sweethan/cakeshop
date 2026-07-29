package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    Optional<Member> findByEmail(@Param("email") String email);

    int join(Member member);

    int update(Member member);

    int withdrawById(
            @Param("id") Long id,
            @Param("status") MemberStatus status);
}
