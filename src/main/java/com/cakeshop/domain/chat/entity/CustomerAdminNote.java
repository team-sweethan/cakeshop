package com.cakeshop.domain.chat.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAdminNote {
    private Long id; // 메모 ID
    private Long customerId; // 메모 대상 고객
    private String content; // 관리자 메모 내용
    private Long updatedBy; // 마지막 수정 관리자
    private LocalDateTime createdAt; // 생성 시각
    private LocalDateTime updatedAt; // 최종 수정 시각
}