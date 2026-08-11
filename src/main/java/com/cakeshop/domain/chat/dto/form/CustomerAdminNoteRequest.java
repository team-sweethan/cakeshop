package com.cakeshop.domain.chat.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAdminNoteRequest {
    @NotNull(message = "메모 요청 바디는 필수입니다.")
    @Size(max = 2000, message = "메모 내용은 2,000자 이하여야 합니다.")
    private String content; // 관리자가 작성하거나 수정한 고객 특이사항 메모 내용
}
