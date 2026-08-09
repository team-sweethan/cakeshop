package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.member.dto.view.MemberReviewView;

/**
 * 상품 후기 목록(B1)의 한 건.
 *
 * <p>작성자 식별자를 담지 않는다. 화면에 필요한 것은 표시명뿐이고, 후기 목록은 비로그인에게도
 * 열려 있어 회원 식별자가 나갈 자리가 아니다(DOMAIN 2.3).</p>
 */
public record ProductReviewView(
        Long id,
        String authorName,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content,
        LocalDateTime createdAt
) {

    /** 탈퇴 회원의 표기. 후기와 평점은 남기고 이름만 가린다(DOMAIN 2.6). */
    public static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";

    /**
     * 매퍼가 읽은 행에 작성자를 붙인다.
     *
     * <p>{@code author} 가 {@code null} 이면 탈퇴와 같게 다룬다. 회원 계약은 없는 ID 를 결과에서
     * 빼고 돌려주므로, 여기서 예외를 던지면 회원 한 명 때문에 목록 전체가 사라진다.</p>
     */
    public static ProductReviewView of(ReviewRow row, MemberReviewView author) {
        return new ProductReviewView(
                row.id(),
                author == null || author.withdrawn()
                        ? WITHDRAWN_AUTHOR_NAME
                        : author.nickname(),
                row.overallRating(),
                row.tasteRating(),
                row.designRating(),
                row.serviceRating(),
                row.content(),
                row.createdAt());
    }
}
