package com.cakeshop.domain.member.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberStatusHistory {

    private Long id;
    private Long memberId;
    private MemberStatusAction action;
    private MemberStatus beforeStatus;
    private MemberStatus afterStatus;
    private String reason;
    private Long processedBy;
    private LocalDateTime processedAt;
}
