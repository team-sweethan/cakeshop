package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.query.CommentCountRow;
import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.dto.query.ReplyCountRow;
import com.cakeshop.domain.community.entity.Comment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-19
 * 기능 : 커뮤니티 댓글 데이터 접근
 * 설명 : CommunityCommentMapper 댓글 조회와 변경을 수행한다.
 * ******************************
 */
// 구현 클래스가 없는데도 동작하는 이유:
//   @Mapper 인터페이스를 MyBatis 가 스캔해서 실행 시점에 구현체를 대신 만들어 준다.
//   메서드 이름 = src/main/resources/mapper/community/CommunityCommentMapper.xml 안의
//   <select>·<insert> 태그 id. 그 짝이 맞아야 SQL 이 연결된다.
//
// 댓글은 2단 트리다: 원댓글(root) 아래에 답글(reply)만 달리고 그 아래로는 더 내려가지 않는다.
@Mapper
public interface CommunityCommentMapper {

    // 못 찾으면 null. 삭제·신고처럼 대상 하나를 확인할 때 쓴다.
    CommentRow findCommentById(
            @Param("commentId") long commentId
    );

    List<CommentRow> findRecentRootComments(
            @Param("postId") long postId,
            @Param("limit") int limit
    );

    // postId 를 함께 받는 이유: parentCommentId 만 믿지 않고 "그 글에 속한 답글"인지 같이 확인한다.
    List<CommentRow> findRepliesByParentId(
            @Param("parentCommentId") long parentCommentId,
            @Param("postId") long postId,
            @Param("limit") int limit
    );

    // 부모 여러 개의 답글 수를 한 번에 센다(부모마다 세면 N번 쿼리가 된다).
    // 반환 List<ReplyCountRow> = (parentId, count) 쌍의 묶음. 답글이 0인 부모는 아예 빠져 있을 수 있으니
    // Service 에서 Map 으로 바꾼 뒤 없는 키는 0으로 다룬다.
    List<ReplyCountRow> countRepliesByParentIds(
            @Param("parentIds") List<Long> parentIds
    );

    // 목록이 아니라 Row 한 개를 돌려준다 — 원댓글 수·전체 수처럼 여러 숫자를 한 번에 담기 때문이다.
    CommentCountRow countComments(
            @Param("postId") long postId
    );

    int insertComment(Comment comment);

    // 알림이 새 답글의 id 를 받아야 해서 Comment 를 넘긴다 — 다중 @Param 으로는 생성 키가 돌아오지 않는다.
    int insertReply(Comment reply);

    // postId·memberId 도 WHERE 에 함께 건다 = 남의 댓글이나 다른 글의 댓글이면 0을 돌려받는다.
    int deleteComment(
            @Param("commentId") long commentId,
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );
}
