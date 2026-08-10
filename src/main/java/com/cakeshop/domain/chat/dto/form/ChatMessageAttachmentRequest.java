package com.cakeshop.domain.chat.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class ChatMessageAttachmentRequest {
    @NotBlank(message = "S3 객체 키는 필수입니다.")
    @Size(max = 500, message = "S3 객체 키는 500자 이하여야 합니다.")
    private String objectKey;

    @NotBlank(message = "원본파일명은 필수입니다.")
    @Size(max = 255, message = "원본파일명은 255자 이하여야 합니다.")
    private String originalFilename;

    @NotBlank(message = "파일 타입은 필수입니다.")
    @Size(max = 100, message = "파일 타입은 100자 이하여야 합니다.")
    private String contentType;

    @NotNull(message = "파일 크기는 필수입니다.")
    @Positive(message = "파일 크기는 양수여야 합니다.")
    private Long fileSize;
}
