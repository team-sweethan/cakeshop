package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.Post;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 고객 경로(/community)의 조회와 쓰기.
 *
 * 관리자 조회·조치는 CommunityAdminMapper에 있다. 노출 규칙이 정반대라 갈라 뒀고,
 * 근거는 그쪽 주석에 적었다(docs/community/DOMAIN.md 4.3).
 *
 * <b>lockPost만은 양쪽이 함께 쓴다.</b> 좋아요·신고만이 아니라 관리자의 차단·해제·기각도
 * 이 문장으로 게시글 행을 잠근다(H15·H17). 잠금 문장이 둘이 되면 "어느 쪽이 먼저인가"를
 * 따질 자리가 생기고, 그 답이 갈리는 순간 교착이다.
 */
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

    /**
     * 오늘 이 조회자의 첫 조회일 때만 조회수를 1 증가시킨다.
     * 올렸으면 1, 이미 오늘 센 조회이거나 노출 중이 아니면 0이다.
     *
     * 이 문장이 게시글 행을 먼저 잠그고 중복까지 판단한다. 순서가 중요하다 —
     * 자세한 이유는 CommunityMapper.xml에 적어 두었다(교착 상태).
     *
     * 1을 돌려받았을 때만 recordView로 이력을 남긴다. 둘은 한 트랜잭션이어야 한다.
     */
    int increaseViewCount(
            @Param("postId") long postId,
            @Param("viewerKey") String viewerKey
    );

    /**
     * 조회 이력을 남긴다. increaseViewCount가 1을 돌려줬을 때만 부른다.
     *
     * 중복이면 UNIQUE 위반으로 실패한다. 삼키지 않는 것이 중요하다 — 실패하면 같은
     * 트랜잭션의 조회수 증가까지 함께 되돌아가므로, 숫자가 이력보다 앞서는 상태가
     * 생기지 않는다(DOMAIN.md 6.2).
     */
    int recordView(
            @Param("postId") long postId,
            @Param("viewerKey") String viewerKey
    );

    /** 한 게시글의 조회 이력 개수. view_count가 이력과 맞는지 확인하는 데 쓴다. */
    long countViews(
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

    /**
     * 좋아요를 바꾸기 전에 게시글 행을 잠그고 권한 판단에 필요한 값을 읽는다.
     * 없으면 null이다.
     *
     * 이 호출이 좋아요 경로의 첫 문장이어야 한다. 이유는 교착이며, 자세한 근거는
     * CommunityMapper.xml에 적어 두었다.
     */
    PostLockView lockPost(
            @Param("postId") long postId
    );

    /**
     * 좋아요를 남긴다. 이미 눌러 둔 상태면 아무 일도 하지 않는다(DOMAIN.md 6.5).
     *
     * <b>갱신 행 수로 아무것도 판단하지 않는다.</b> MariaDB JDBC가 CLIENT_FOUND_ROWS를 켜서
     * 이 문장은 새로 넣었을 때와 이미 있을 때를 구분해 주지 않는다(6.2에서 겪은 것과 같다).
     * 구분할 필요도 없다 — 뒤따르는 재계산이 실제 행 수를 다시 세기 때문이다.
     */
    int insertLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    /** 좋아요를 거둔다. 누른 적이 없으면 0행이고, 그것도 성공이다(멱등, DOMAIN.md 6.5). */
    int deleteLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    /**
     * posts.like_count를 post_likes에서 다시 센다. 증분하지 않는 이유는 DOMAIN.md 6.5에 있다.
     *
     * insertLike/deleteLike 다음에 같은 트랜잭션에서 부른다. 잠금은 lockPost가 이미 쥐고 있다.
     */
    int recalculateLikeCount(
            @Param("postId") long postId
    );

    /** 이 회원이 이 글에 좋아요를 눌러 뒀는지. 상세 화면의 버튼 문구를 가르는 데 쓴다. */
    boolean existsLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    /** 한 게시글의 좋아요 개수. like_count가 실제와 맞는지 확인하는 데 쓴다(countViews와 같다). */
    long countLikes(
            @Param("postId") long postId
    );

    /**
     * 신고를 접수한다. 이미 신고한 글이면 UNIQUE 위반으로 실패한다.
     *
     * 좋아요와 달리 중복을 삼키지 않는다(DOMAIN.md 6.6). 조용히 성공을 돌려주면 신고자는
     * 접수됐다고 오해하는데 실제로는 아무 일도 일어나지 않는다. Service가 이 실패를
     * ALREADY_REPORTED로 바꾼다.
     */
    int insertReport(
            @Param("postId") long postId,
            @Param("reporterId") long reporterId,
            @Param("reason") String reason
    );

    /** 이 회원이 이 글을 이미 신고했는지. 중복 신고를 INSERT 전에 걸러내는 데 쓴다. */
    boolean existsReport(
            @Param("postId") long postId,
            @Param("reporterId") long reporterId
    );

}
