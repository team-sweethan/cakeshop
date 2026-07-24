package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.entity.Member;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    Optional<Member> findByEmail(@Param("email") String email);
}
