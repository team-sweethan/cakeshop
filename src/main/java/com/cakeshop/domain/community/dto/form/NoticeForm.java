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
// 관리자 공지 등록·수정 화면에서 넘어온 입력을 담는 상자다.
// @NotBlank·@Size·@AssertTrue 는 컨트롤러가 @Valid @ModelAttribute 로 받을 때 스프링이 검사하고,
// 그 결과는 바로 뒤에 선언한 BindingResult 로 들어간다. hasErrors()면 저장 없이 폼으로 돌아간다.
public class NoticeForm {

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    @Size(max = 5000, message = "내용은 5000자 이하여야 합니다.")
    private String content;

    // 노출 기간. 브라우저는 "2026-08-20T09:00" 같은 문자열로 보내는데,
    // @DateTimeFormat 이 그 모양을 알려 줘야 스프링이 LocalDateTime 으로 바꿔 준다.
    // 검증 어노테이션이 없으니 둘 다 비워 둘 수 있고, 그러면 기간 제한이 없는 공지가 된다.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endsAt;

    // 아래 둘은 @Setter 가 만들어 줄 setter 를 직접 덮어쓴 것이다. 스프링이 값을 넣을 때 여길 지난다.
    public void setTitle(String title) {
        this.title = strip(title);
    }

    public void setContent(String content) {
        this.content = strip(content);
    }

    // 칸 하나로는 못 보는 검사(두 값의 관계)는 @AssertTrue 로 만든다.
    // 규칙: is로 시작하는 boolean 메서드를 만들면 검증기가 그걸 "validPeriod"라는 가짜 칸으로 보고 호출한다.
    // false 를 돌려주면 message 가 그 자리의 오류로 붙는다.
    // 둘 다 입력했을 때만 순서를 따진다. 한쪽이 비면 기간이 열려 있다는 뜻이다.
    @AssertTrue(message = "종료일은 시작일보다 뒤여야 합니다.")
    public boolean isValidPeriod() {
        return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
