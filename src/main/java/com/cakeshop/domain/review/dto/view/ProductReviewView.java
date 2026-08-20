package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.review.dto.query.ReviewRow;

// 상품 상세 화면의 후기 카드 한 장을 담는 상자
// (templates/customer/review/product.html 과 fragments/customer/product-review.html 의 미리보기)
//
// record: 괄호 안에 적은 것이 곧 필드 목록이다. 자바가 그 자리에서 다음을 대신 만들어 준다
//     private final Long id ...  (전부 final, setter 없음)
//     id(), authorName() ...     (getId() 가 아니라 필드 이름 그대로인 읽기 메서드)
//     전체 인자 생성자 + equals/hashCode/toString
// 이 패키지의 *View 는 전부 같은 모양이라 record 설명은 여기 한 번만 둔다
//
// 후기 본문은 review 테이블에서, 작성자 이름은 회원 도메인에서, 이미지·답글은 각각 따로 조회해
// 아래 from(...) 이 한 덩어리로 합친다 (도메인끼리 JOIN 하지 않기 때문)
public record ProductReviewView(
        Long id,
        String authorName,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content,
        LocalDateTime createdAt,
        List<ReviewImageView> images,
        ReviewReplyView reply
) {

    // 관리자 화면의 두 View 도 이 상수를 가져다 쓴다 -> 표시 문구가 한 군데서만 정해진다
    public static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";

    // 정적 팩토리: 흩어져 들어온 조회 결과를 화면이 쓸 한 덩어리로 합친다
    // author 가 null 이거나 탈퇴 회원이면 닉네임 대신 "탈퇴한 회원" 을 넣는다
    // images 가 null 이면 빈 목록으로 바꿔 화면에서 null 검사를 하지 않게 한다
    //     List.copyOf: 원본을 복사해 못 바꾸는 목록으로 만든다 (밖에서 원본을 고쳐도 여기는 안 변한다)
    public static ProductReviewView from(
            ReviewRow row,
            MemberReviewView author,
            List<ReviewImageView> images,
            ReviewReplyView reply) {

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
                row.createdAt(),
                images == null ? List.of() : List.copyOf(images),
                reply);
    }

}
