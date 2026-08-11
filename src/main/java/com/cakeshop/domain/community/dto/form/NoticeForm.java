package com.cakeshop.domain.community.dto.form;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 입력값 검증
 * 설명 : NoticeForm 요청 데이터와 검증 규칙을 정의한다.
 * ******************************
 */
public class NoticeForm {

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    @Size(max = 5000, message = "내용은 5000자 이하여야 합니다.")
    private String content;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endsAt;

    public void setTitle(String title) {
        this.title = strip(title);
    }

    public void setContent(String content) {
        this.content = strip(content);
    }

    /** 둘 다 입력했을 때만 순서를 따진다. 한쪽이 비면 기간이 열려 있다는 뜻이다. */
    @AssertTrue(message = "종료일은 시작일보다 뒤여야 합니다.")
    public boolean isValidPeriod() {
        return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
