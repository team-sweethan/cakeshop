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
// 게시글 작성·수정 화면에서 넘어온 입력을 담는 상자다.
// 아래 @NotNull·@NotBlank·@Size 는 여기서 스스로 돌지 않는다 — 컨트롤러가 매개변수에
// @Valid @ModelAttribute("form") PostForm form 이라고 적었을 때 스프링이 대신 검사한다.
// 검사 결과는 예외가 아니라 바로 뒤에 붙은 BindingResult 로 들어가고, 컨트롤러는
// bindingResult.hasErrors()가 true 면 입력값이 살아 있는 폼 화면을 다시 보여 준다.
// (message = "...")에 적은 문장이 그때 화면에 뜨는 문구다.
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

    // 새로 첨부할 이미지. MultipartFile 은 업로드된 파일 한 개를 감싼 타입이고,
    // 화면의 <input type="file" name="images" multiple> 이 여러 개면 List 로 채워진다.
    // 여기에는 검증 어노테이션이 없다 — 형식·용량·장수는 Service 가 본다.
    // 빈 List 로 초기화해 둬서 파일을 하나도 안 고르면 null 이 아니라 크기 0 이 된다.
    private List<MultipartFile> images = new ArrayList<>();

    // 수정 화면에서 체크한 "기존 첨부 지우기"의 id 들이다.
    private List<Long> deleteImageIds = new ArrayList<>();

    // @Setter 가 만들어 줄 setTitle 을 직접 써서 덮어쓴다. 같은 이름이면 내가 쓴 쪽이 남는다.
    // 스프링이 요청 값을 넣을 때도 이 setter 를 부르므로, @Size 검사는 앞뒤 공백을 턴 뒤의 길이로 센다.
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
