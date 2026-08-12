package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.entity.SocialAccount;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SocialAccountMapper {

    Optional<String> findMemberEmail(
            @Param("provider") String provider,
            @Param("providerId") String providerId);

    int insert(SocialAccount socialAccount);
}
