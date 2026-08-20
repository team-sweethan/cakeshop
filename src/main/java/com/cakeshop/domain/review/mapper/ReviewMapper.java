package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.command.ReviewUpdateCommand;
import com.cakeshop.domain.review.dto.query.ProductRatingAggregate;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.entity.Review;

// 구현 클래스를 우리가 쓰지 않는다. @Mapper 가 붙은 이 인터페이스를 MyBatis 가 대신 구현해 스프링 빈으로
// 올리고, 메서드 이름을 src/main/resources/mapper/review/ReviewMapper.xml 의 같은 id 와 짝지어 SQL 을 돌린다
//     findById(37L) -> <select id="findById"> 의 SQL 실행 -> 결과를 ReviewRow 로 채워 반환
// XML 의 namespace 가 이 인터페이스의 전체 이름이라 둘이 연결된다
@Mapper
public interface ReviewMapper {

    ReviewRow findById(@Param("id") long id);

    // 이름 뒤의 ForUpdate 는 SQL 끝에 FOR UPDATE 가 붙어 있다는 뜻이다
    //     행에 잠금을 걸고, 스냅샷이 아니라 지금 커밋된 값을 읽어 온다
    // 같은 트랜잭션 안에서 앞서 부른 일반 SELECT 는 시작 시점의 스냅샷을 보므로 값이 다를 수 있다
    ReviewRow findByIdForUpdate(@Param("id") long id);

    boolean existsByOrderItemId(@Param("orderItemId") long orderItemId);

    // 반환이 List<Long>: 후기가 달린 order_items 의 id 번호만 줄줄이 담아 온다 (후기 내용은 없다)
    // 작성 가능 목록 화면에서 "이미 쓴 것"을 빼는 데 쓴다
    // SQL 에 상태 조건이 없다 — DELETED 후기의 id 도 함께 온다
    //     지운 후기도 행은 남고 uk_reviews_order_item 유니크 키가 같은 주문 상품의 재작성을 막는다
    List<Long> findReviewedOrderItemIds(@Param("memberId") long memberId);

    // 목록과 개수는 짝으로 쓴다. 목록은 offset/size 로 한 페이지만 잘라 오고, 개수는 전체를 세어
    // 페이지 수 계산에 넣는다
    //     예: page=3, size=10 -> offset=20, size=10
    // @Param 이 셋이라 이름을 붙여야 XML 이 #{productId}, #{offset}, #{size} 를 각각 찾는다
    List<ReviewRow> findPublishedByProductId(
            @Param("productId") long productId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countPublishedByProductId(@Param("productId") long productId);

    List<ReviewRow> findByMemberId(
            @Param("memberId") long memberId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countByMemberId(@Param("memberId") long memberId);

    // 여러 행을 세고 평균 내어 한 행으로 받는다 -> ProductRatingAggregate 하나가 반환된다
    // 여기도 FOR UPDATE 라 상품의 후기 행들을 잠그고 지금 커밋된 값으로 센다
    //     MariaDB 기본 격리 수준인 REPEATABLE READ 에서는 일반 SELECT 가 트랜잭션 시작 시점의
    //     스냅샷을 보기 때문에, 잠그지 않으면 그사이 커밋된 남의 후기가 집계에서 빠진다
    ProductRatingAggregate aggregateForUpdate(@Param("productId") long productId);

    // 인자가 객체 하나뿐이면 @Param 을 생략해도 된다 — XML 의 #{orderItemId} 가 Review 의 getter 를 찾아간다
    // 반환 int 는 실제로 바뀐 행 수다. INSERT 는 1, 조건에 안 걸린 UPDATE·DELETE 는 0 이 온다
    int insert(Review review);

    int update(ReviewUpdateCommand command);

    // memberId 를 WHERE 에 함께 넣어 남의 후기는 지워지지 않게 한다
    // 0 이 오면 "없거나 내 것이 아니거나" 둘 중 하나다 — 어느 쪽인지는 위 findByIdForUpdate 로 가린다
    int deleteByAuthor(@Param("reviewId") long reviewId, @Param("memberId") long memberId);

}
