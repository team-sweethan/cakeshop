package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시글 작성·수정 화면의 입력값과 Bean Validation 규칙을 담는다.
 *
 * <p>작성과 수정이 같은 폼을 쓴다. 수정 가능 항목이 제목·본문·카테고리 전부이고
 * (docs/community/DOMAIN.md 6.3) 작성 항목과 같기 때문이다. 조회수·상태처럼 서버가
 * 결정하는 값은 담지 않는다.
 *
 * <p><b>제목·본문 setter만 직접 쓴다.</b> DOMAIN.md 7이 "trim 후 검증"을 요구하는데, 검증은
 * 필드 값을 보고 돌아가므로 들어올 때 다듬지 않으면 공백만 입력한 제목이 길이 검사를
 * 통과한 뒤 <b>공백 그대로 저장된다.</b> Lombok은 같은 이름의 setter가 있으면 생성하지
 * 않으므로 나머지는 {@code @Setter}가 만든다. 다듬은 값을 그대로 저장하니 Service가 다시
 * trim하지 않는다.
 */
@Getter
@Setter
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

    public void setTitle(String title) {
        this.title = strip(title);
    }

    /**
     * 본문의 앞뒤 공백만 제거한다.
     *
     * <p>중간 줄바꿈은 사용자가 쓴 그대로 보존해야 한다({@code String.strip()}이 앞뒤만
     * 건드린다).
     *
     * @param content 입력한 본문
     */
    public void setContent(String content) {
        this.content = strip(content);
    }

    /**
     * 앞뒤 공백을 제거한다.
     *
     * @param value 입력값
     * @return 앞뒤를 다듬은 값. {@code null}이면 {@code null}
     */
    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
