package com.cakeshop.domain.chat.dto.form;

import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "메모 요청 바디는 필수입니다.") // @NotNull 추가! (빈 문자열 ""은 허용함)
    private String content; // 관리자가 작성하거나 수정한 고객 특이사항 메모 내용
}
