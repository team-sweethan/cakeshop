package com.cakeshop.domain.community.dto.form;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 입력값 검증
 * 설명 : PostForm 요청 데이터와 검증 규칙을 정의한다.
 * ******************************
 */
public class PostForm {

    @NotNull(message = "분류를 선택해 주세요.")
    @Positive(message = "분류를 선택해 주세요.")
    private Long categoryId;

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    @Size(max = 5000, message = "내용은 5000자 이하여야 합니다.")
    private String content;

    /**
     * 새로 첨부할 이미지다. 장수·형식·용량은 여기서 보지 않는다 — 상한은 이미 붙어 있는 장수와
     * 함께 세야 하고(`specs/community-post.md` A4) 그 값은 폼이 모른다.
     */
    private List<MultipartFile> images = new ArrayList<>();

    /** 수정 화면에서 지우기로 표시한 기존 첨부의 식별자다. */
    private List<Long> deleteImageIds = new ArrayList<>();

    public void setTitle(String title) {
        this.title = strip(title);
    }

    public void setContent(String content) {
        this.content = strip(content);
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
