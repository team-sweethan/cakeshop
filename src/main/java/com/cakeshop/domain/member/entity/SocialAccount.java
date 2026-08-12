package com.cakeshop.domain.member.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 외부 OAuth 제공자와 회원 계정의 연결 정보다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialAccount {

    private Long id;
    private Long memberId;
    private String provider;
    private String providerId;
    private LocalDateTime createdAt;
}
