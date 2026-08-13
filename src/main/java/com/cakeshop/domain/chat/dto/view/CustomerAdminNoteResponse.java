package com.cakeshop.domain.chat.dto.view;

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
public class CustomerAdminNoteResponse {

    // 관리자가 작성하거나 수정한 고객 특이사항 메모 내용 (선택)
    private String content;
}
