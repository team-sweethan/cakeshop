package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.Post;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityMapper {

    /** 노출 중인 게시글 목록을 최신순으로 조회한다. categoryId가 null이면 전체 카테고리다. */
    List<PostListView> findPublishedPosts(
            @Param("categoryId") Long categoryId,
            @Param("size") int size,
            @Param("offset") int offset
    );

    /** 노출 중인 게시글의 전체 개수. categoryId가 null이면 전체 카테고리다. */
    long countPublishedPosts(
            @Param("categoryId") Long categoryId
    );

    /**
     * 게시글 상세를 상태와 무관하게 조회한다. 없으면 null이다.
     *
     * 노출 여부는 조회 결과의 상태를 보고 Service가 판단한다(DOMAIN.md 4.3).
     * 여기서 상태로 걸러 버리면 작성자에게 차단 사유를 보여줄 수 없다.
     */
    PostDetailView findPostById(
            @Param("postId") long postId
    );

    /** 노출 중인 게시글의 조회수를 1 증가시킨다. 노출 중이 아니면 0행이다. */
    int increaseViewCount(
            @Param("postId") long postId
    );

    /** 화면의 선택지로 노출할 활성 카테고리를 정렬 순서대로 조회한다. */
    List<PostCategoryView> findActiveCategories();

    /**
     * 활성 카테고리인지 확인한다. 선택지에 없는 카테고리로 글을 넣지 못하게 막는다
     * (DOMAIN.md 6.8). 화면의 select만 믿으면 요청을 직접 만들어 비활성 카테고리로 보낼 수 있다.
     */
    boolean existsActiveCategory(
            @Param("categoryId") Long categoryId
    );

    /** 게시글을 저장하고 생성된 식별자를 post.id에 채운다. */
    int insertPost(Post post);

    /**
     * 노출 중인 자기 게시글의 제목·본문·카테고리를 수정한다.
     * 대상이 없거나 조건에 맞지 않으면 0행이다.
     *
     * 소유권과 상태 조건을 SQL에도 둔다. 판단과 에러 응답은 Service가 하지만
     * (404/403을 구분해야 한다), 검증과 UPDATE 사이에 상태가 바뀌면 조건 없는 UPDATE는
     * 차단된 글을 고쳐 버린다.
     */
    int updatePost(Post post);

    /**
     * 노출 중인 자기 게시글을 삭제 상태로 바꾼다.
     * 대상이 없거나 조건에 맞지 않으면 0행이다.
     *
     * status = 'PUBLISHED' 조건이 BLOCKED -> DELETED 금지(DOMAIN.md 4.2)를 SQL 쪽에서도 지킨다.
     */
    int deletePost(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    /**
     * 상세 화면에 실을 댓글을 최신순으로 limit건 조회한다.
     *
     * 최신순인 것은 실수가 아니다. 화면에는 오래된 순으로 나가지만, 잘라 내는 쪽이
     * 과거여야 방금 쓴 댓글이 언제나 화면에 남는다. 뒤집는 것은 Service가 한다
     * (CommentSectionView 참고).
     *
     * 삭제된 댓글도 함께 돌려준다. 지우지 않고 자리 표시로 남기기 때문이다(DOMAIN.md 4.4).
     */
    List<CommentView> findRecentComments(
            @Param("postId") long postId,
            @Param("limit") int limit
    );

    /** 한 게시글의 댓글 수. 자리 표시를 포함한 전체 행 수와 노출 중인 수를 함께 센다. */
    CommentCountView countComments(
            @Param("postId") long postId
    );

    /**
     * 댓글 하나를 상태와 무관하게 조회한다. 없으면 null이다.
     *
     * 상태로 걸러 버리면 이미 지워진 댓글과 없는 댓글을 Service가 구분할 수 없고,
     * 조건부 UPDATE가 0행일 때 어떤 응답을 낼지 정할 근거가 사라진다.
     */
    CommentView findCommentById(
            @Param("commentId") long commentId
    );

    /** 댓글을 저장하고 생성된 식별자를 comment.id에 채운다. */
    int insertComment(Comment comment);

    /**
     * 노출 중인 자기 댓글을 삭제 상태로 바꾼다.
     * 대상이 없거나 조건에 맞지 않으면 0행이다.
     *
     * 소유권·상태 조건은 Service 검증과 중복이지만, 검증과 UPDATE 사이의 변화를 막는다.
     * postId까지 조건에 두는 것은 주소 위조를 막기 위해서다 — 댓글 번호만 맞으면 다른 글의
     * 주소로 지울 수 있으면 안 된다.
     */
    int deleteComment(
            @Param("commentId") long commentId,
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );
}
