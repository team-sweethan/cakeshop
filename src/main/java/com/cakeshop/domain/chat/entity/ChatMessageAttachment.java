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
public class ChatMessageAttachment {
    private Long id; // 첨부파일 ID
    private Long chatMessageId; // 첨부된 메시지 ID
    private String objectKey; // S3 객체 키
    private String originalFilename; // 사용자가 업로드한 원본 파일명
    private String contentType; // MIME 타입
    private Long fileSize; // 파일 크기, byte
    private int displayOrder; // 메시지 내 표시 순서
    private LocalDateTime createdAt; // 첨부 생성 시각
}