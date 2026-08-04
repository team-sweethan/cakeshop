package com.cakeshop.domain.community.service;

import java.util.List;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

    private final CommunityMapper communityMapper;

    public CommunityService(CommunityMapper communityMapper) {
        this.communityMapper = communityMapper;
    }

    /**
     * 노출 중인 게시글 목록. categoryId가 null이면 전체 카테고리다.
     * 페이지 크기는 호출자가 정하고, 정렬은 sort가 정한다(null이면 최신순).
     */
    @Transactional(readOnly = true)
    public PageResult<PostListView> getPosts(
            Long categoryId,
            PostSort sort,
            PageRequest pageRequest
    ) {
        List<PostListView> posts = communityMapper.findPublishedPosts(
                categoryId,
                sort,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityMapper.countPublishedPosts(categoryId);

        return new PageResult<>(posts, pageRequest, totalElements);
    }

    /**
     * 게시글 상세를 조회하고, 최근 10분 안에 보지 않은 조회면 조회수를 올린다.
     *
     * viewerId는 소유권 판단용이며 비로그인이면 null이다. viewerKey는 조회수 중복
     * 방지용으로, 회원이면 회원 번호가 비로그인이면 세션 id가 들어간다(DOMAIN.md 6.2).
     * 두 값 모두 요청 파라미터가 아니라 인증 정보·세션에서 와야 한다 — 클라이언트가
     * 정하면 매번 다른 키를 보내 중복 방지를 그대로 뚫는다.
     *
     * 조회 기록·조회수 증가·상세 조회를 한 트랜잭션에서 처리한다. 노출되지 않는
     * 게시글은 Mapper의 조건에서 걸러지므로 기록도 조회수도 남지 않는다.
     *
     * 노출 판단은 DOMAIN.md 4.3의 표를 그대로 따른다. 차단된 글은 작성자 본인에게만
     * 사유와 함께 보여 준다. 작성자는 차단된 글에 아무 조치도 할 수 없으므로(4.2),
     * 404까지 주면 글이 왜 사라졌는지 알 방법이 없다.
     */
    @Transactional
    public PostDetailView getPostDetail(long postId, Long viewerId, String viewerKey) {
        // 창 밖의 조회일 때만 숫자를 올리고 이력을 남긴다(DOMAIN.md 6.2).
        //
        // 순서를 뒤집지 말 것. 이력을 먼저 넣으면 FK 확인이 게시글 행에 공유 잠금을
        // 걸고, 그 뒤 조회수 UPDATE가 배타 잠금을 기다리면서 같은 글을 동시에 연 요청끼리
        // 교착에 빠진다. 조회수 UPDATE가 먼저 잠그고 중복까지 판단한다(CommunityMapper.xml).
        if (communityMapper.increaseViewCount(postId, viewerKey) > 0) {
            communityMapper.recordView(postId, viewerKey);
        }

        return requireVisiblePost(postId, viewerId);
    }

    /**
     * 조회수를 올리지 않고 상세를 읽는다. 노출 판단은 getPostDetail과 같다.
     *
     * 댓글 작성이 검증에 걸려 상세를 다시 그릴 때 쓴다. 폼이 되돌아오는 것은 조회가
     * 아니다 — 여기서 조회수를 올리면 빈 댓글을 여러 번 보내는 것만으로 조회수가 오른다.
     */
    @Transactional(readOnly = true)
    public PostDetailView getVisiblePost(long postId, Long viewerId) {
        return requireVisiblePost(postId, viewerId);
    }

    /** 화면의 카테고리 선택지로 쓸 활성 카테고리 목록. */
    @Transactional(readOnly = true)
    public List<PostCategoryView> getActiveCategories() {
        return communityMapper.findActiveCategories();
    }

    /**
     * 게시글을 저장하고 식별자를 돌려준다. 제목·본문은 폼에서 이미 다듬어져 있다.
     * authorId는 인증 정보에서 얻은 값이어야 한다. 활성 카테고리가 아니면 거절한다.
     */
    @Transactional
    public long createPost(PostForm form, long authorId) {
        requireActiveCategory(form.getCategoryId());

        Post post = Post.create(
                authorId,
                form.getCategoryId(),
                form.getTitle(),
                form.getContent()
        );

        communityMapper.insertPost(post);

        return post.getId();
    }

    /**
     * 수정 화면에 채울 게시글을 조회한다. 대상이 없거나, 남의 글이거나, 차단된 글이면 거절한다.
     *
     * 상세와 달리 조회수를 올리지 않는다. 자기 글을 고치러 들어온 것은 조회가 아니다.
     */
    @Transactional(readOnly = true)
    public PostDetailView getEditablePost(long postId, long editorId) {
        return requireEditablePost(postId, editorId);
    }

    /**
     * 게시글을 수정한다. editorId는 인증 정보에서 얻은 값이어야 한다.
     * 대상이 없거나, 남의 글이거나, 차단된 글이거나, 활성 카테고리가 아니면 거절한다.
     */
    @Transactional
    public void updatePost(long postId, PostForm form, long editorId) {
        requireEditablePost(postId, editorId);
        requireActiveCategory(form.getCategoryId());

        Post post = Post.edit(
                postId,
                editorId,
                form.getCategoryId(),
                form.getTitle(),
                form.getContent()
        );

        requireApplied(communityMapper.updatePost(post), postId, editorId);
    }

    /**
     * 게시글을 삭제 상태로 바꾼다. editorId는 인증 정보에서 얻은 값이어야 하고,
     * 대상이 없거나, 남의 글이거나, 차단된 글이면 거절한다.
     *
     * 행을 지우지 않는 soft delete다(DOMAIN.md 4.2). 댓글·좋아요·신고는 그대로 둔다(4.5).
     */
    @Transactional
    public void deletePost(long postId, long editorId) {
        requireEditablePost(postId, editorId);

        requireApplied(communityMapper.deletePost(postId, editorId), postId, editorId);
    }

    /**
     * 상세 화면의 댓글 구역을 만든다. requestedLimit은 "더 보기"가 실어 보낸 값이며
     * 처음 열린 상세에서는 null이다.
     *
     * 게시글 노출 판단은 하지 않는다. 이 메서드는 이미 노출이 확인된 게시글에 대해서만
     * 호출되고, 여기서 한 번 더 조회하면 상세 한 번에 게시글을 두 번 읽게 된다.
     */
    @Transactional(readOnly = true)
    public CommentSectionView getComments(long postId, Integer requestedLimit) {
        int limit = CommentSectionView.clampLimit(requestedLimit);

        List<CommentView> recent = communityMapper.findRecentComments(postId, limit);
        CommentCountView counts = communityMapper.countComments(postId);

        // SQL은 최신순으로 자르고 화면에는 오래된 순으로 낸다. 새 댓글이 맨 아래에 붙고
        // "더 보기"는 과거로 거슬러 올라간다.
        return new CommentSectionView(
                List.copyOf(recent.reversed()),
                counts.publishedCount(),
                counts.rowCount(),
                limit
        );
    }

    /**
     * 댓글을 달 수 있는 게시글인지 확인하고 돌려준다.
     *
     * 댓글 검증이 실패했을 때 상세를 다시 그리기 <b>전에</b> 권한부터 보려고 열어 둔다.
     * 순서가 뒤집히면 남의 삭제된 글 번호로 빈 댓글을 보냈을 때 상태도 확인하지 않은 채
     * 그 글의 상세가 200으로 열린다(조각 2의 수정 화면과 같은 실수다).
     */
    @Transactional(readOnly = true)
    public PostDetailView getCommentablePost(long postId, long memberId) {
        return requireCommentablePost(postId, memberId);
    }

    /**
     * 댓글을 저장한다. authorId는 인증 정보에서 얻은 값이어야 한다.
     * 대상 게시글이 노출 중이 아니면 거절한다(DOMAIN.md 4.5).
     */
    @Transactional
    public void addComment(long postId, CommentForm form, long authorId) {
        requireCommentablePost(postId, authorId);

        communityMapper.insertComment(
                Comment.create(postId, authorId, form.getContent()));
    }

    /**
     * 자기 댓글을 삭제 상태로 바꾼다. memberId는 인증 정보에서 얻은 값이어야 한다.
     *
     * 행을 지우지 않는 soft delete다(DOMAIN.md 4.4). 지운 자리에는 "삭제된 댓글입니다"가
     * 남고 개수 집계에서는 빠진다.
     *
     * 삭제에도 게시글이 노출 중일 것을 요구한다. 노출되지 않는 글의 댓글은 아무에게도
     * 보이지 않으므로 지울 이유가 없고, 작성에만 조건을 걸면 "댓글에 손대는 조건"이 두 벌이
     * 되어 한쪽을 고칠 때 다른 쪽을 빠뜨린다.
     */
    @Transactional
    public void deleteComment(long postId, long commentId, long memberId) {
        requireCommentablePost(postId, memberId);
        requireOwnComment(postId, commentId, memberId);

        requireCommentApplied(
                communityMapper.deleteComment(commentId, postId, memberId),
                postId,
                commentId,
                memberId
        );
    }

    /**
     * 좋아요를 남긴다. memberId는 인증 정보에서 얻은 값이어야 한다.
     *
     * 이미 눌러 둔 상태에서 다시 불러도 성공한다(DOMAIN.md 6.5). 토글이 아니라 추가와
     * 취소가 갈려 있으므로 같은 요청이 두 번 와도 뜻이 달라지지 않는다 — 토글이면 재전송·
     * 더블클릭이 원래 상태로 되돌려 놓고, 사용자는 눌렀는데 안 눌린 상태가 된다.
     *
     * 세 문장이 한 트랜잭션이고 <b>순서가 중요하다.</b> 잠금이 먼저다 — 잠그지 않고 INSERT
     * 부터 하면 FK 확인이 게시글 행에 공유 잠금을 걸고, 뒤따르는 재계산이 배타 잠금을
     * 기다리면서 같은 글에 동시에 좋아요를 누른 요청끼리 교착에 빠진다(CommunityMapper.xml).
     */
    @Transactional
    public void addLike(long postId, long memberId) {
        requireLikeablePost(postId, memberId);

        communityMapper.insertLike(postId, memberId);
        communityMapper.recalculateLikeCount(postId);
    }

    /**
     * 좋아요를 거둔다. memberId는 인증 정보에서 얻은 값이어야 한다.
     *
     * 누른 적이 없어도 성공한다. 사용자가 원한 상태(안 눌림)가 이미 이뤄져 있으므로,
     * 뒤로가기나 재전송에 에러 화면을 줄 이유가 없다.
     *
     * 잠금 순서는 addLike와 같다. 추가와 취소가 서로 다른 순서로 잠그면 둘이 섞였을 때
     * 교착이 되므로, 두 경로가 같은 문장으로 시작한다.
     */
    @Transactional
    public void removeLike(long postId, long memberId) {
        requireLikeablePost(postId, memberId);

        communityMapper.deleteLike(postId, memberId);
        communityMapper.recalculateLikeCount(postId);
    }

    /**
     * 이 회원이 이 글에 좋아요를 눌러 뒀는지. 상세에서 버튼 문구를 가르는 데 쓴다.
     *
     * 게시글 노출 판단은 하지 않는다. getComments와 같은 이유로, 이미 노출이 확인된
     * 게시글에 대해서만 호출된다.
     */
    @Transactional(readOnly = true)
    public boolean isLikedBy(long postId, long memberId) {
        return communityMapper.existsLike(postId, memberId);
    }

    /**
     * 게시글을 신고한다. reporterId는 인증 정보에서 얻은 값이어야 한다.
     *
     * 좋아요와 정반대로 <b>멱등하지 않다.</b> 이미 신고한 글이면 성공이 아니라 에러다
     * (DOMAIN.md 6.6) — 재신고는 "내 신고가 처리되지 않았다"는 인식의 표현이라, 조용히
     * 성공을 돌려주면 접수됐다고 오해하지만 실제로는 아무 일도 일어나지 않는다.
     *
     * 중복을 두 번 막는다. 확인이 먼저이고 UNIQUE 위반이 그다음이다. 확인만 두면 같은
     * 사람이 두 번 눌렀을 때 둘 다 "없다"를 읽고 하나가 500으로 죽고, 제약만 두면 정상
     * 흐름의 흔한 실수까지 예외 처리에 기대게 된다. 둘 다 같은 응답으로 모은다.
     *
     * 게시글 행을 잠그지 않는다. 좋아요와 달리 posts 를 쓰지 않으므로 잠금을 잡을 이유가
     * 없고, 확인과 INSERT 사이에 관리자가 차단하면 신고 한 건이 더 들어올 뿐이다 —
     * 이미 조치된 글에 붙는 신고라 손해가 없는 쪽으로 틀린다(R14와 같은 판단).
     */
    @Transactional
    public void reportPost(long postId, ReportForm form, long reporterId) {
        requireReportablePost(postId, reporterId);

        try {
            communityMapper.insertReport(postId, reporterId, form.getReason());
        } catch (DuplicateKeyException e) {
            // 확인과 INSERT 사이에 같은 사람의 신고가 먼저 들어온 경우다.
            throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
        }
    }

    /**
     * 신고할 수 있는 게시글인지 확인하고 돌려준다.
     *
     * 신고 사유 검증이 실패해 상세를 다시 그리기 <b>전에</b> 권한부터 보려고 열어 둔다.
     * 댓글의 getCommentablePost와 같은 이유다 — 순서가 뒤집히면 남의 삭제된 글 번호로
     * 빈 신고를 보냈을 때 그 글의 상세가 200으로 열린다.
     */
    @Transactional(readOnly = true)
    public PostDetailView getReportablePost(long postId, long memberId) {
        return requireReportablePost(postId, memberId);
    }

    /**
     * 이 회원이 이 글을 이미 신고했는지. 상세에서 신고 폼을 보일지 안내를 보일지 가른다.
     *
     * 게시글 노출 판단은 하지 않는다. isLikedBy와 같은 이유로, 이미 노출이 확인된
     * 게시글에 대해서만 호출된다.
     */
    @Transactional(readOnly = true)
    public boolean isReportedBy(long postId, long memberId) {
        return communityMapper.existsReport(postId, memberId);
    }

    /**
     * 신고할 수 있는 게시글인지 확인한다.
     *
     * 노출 판단을 통과한 뒤 자기 글을 먼저 거른다. 신고는 관리자에게 남의 글을 알리는
     * 경로이고, 자기 글이 문제라면 지우면 된다(DOMAIN.md 6.6). 여기서 걸러 두면 뒤에
     * 남는 것은 남의 PUBLISHED 글뿐이다 — 남의 차단·삭제된 글은 이미 404이고, 자기 차단
     * 글은 방금 걸러졌다.
     */
    private PostDetailView requireReportablePost(long postId, long memberId) {
        PostDetailView post = requireVisiblePost(postId, memberId);

        if (isAuthor(post, memberId)) {
            throw new BusinessException(CommunityErrorCode.OWN_POST_REPORT);
        }

        if (communityMapper.existsReport(postId, memberId)) {
            throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
        }

        return post;
    }

    /**
     * 좋아요를 누르거나 거둘 수 있는 게시글인지 확인하고, 그 행을 잠근다.
     *
     * 판단 기준은 requireCommentablePost와 같다(DOMAIN.md 4.5) — 노출 중인 글에만 허용하고,
     * 없는 글·삭제된 글·남의 차단된 글은 404, 자기 차단된 글은 403이다. 셋을 같은 404로
     * 묶는 이유는 403이 "그 자리에 글이 있다"는 사실을 흘리기 때문이다(4.3).
     *
     * 댓글과 갈리는 것은 <b>잠근 채로 읽는다</b>는 점뿐이다. 그래서 확인과 쓰기 사이에
     * 관리자가 차단할 수 있는 창이 이 경로에는 없다(R14와 다른 자리인 이유는
     * CommunityMapper.xml에 적었다).
     */
    private void requireLikeablePost(long postId, long memberId) {
        PostLockView post = communityMapper.lockPost(postId);

        if (post == null
                || post.status() == PostStatus.DELETED
                || (post.status() == PostStatus.BLOCKED
                        && !Long.valueOf(memberId).equals(post.memberId()))) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (post.status() != PostStatus.PUBLISHED) {
            // 여기 남는 비-PUBLISHED는 자기 차단 글을 보는 작성자뿐이다. 상세에서 이미
            // 본문과 사유를 본 상대라 숨길 것이 없고, 404를 주면 왜 막혔는지 알 수 없다.
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }
    }

    /**
     * 조건부 댓글 UPDATE가 실제로 한 행을 바꿨는지 확인한다. 게시글 쪽 requireApplied와
     * 같은 이유다 — 갱신 행 수를 버리면 조건이 걸러 낸 순간이 성공으로 보인다.
     */
    private void requireCommentApplied(
            int affectedRows, long postId, long commentId, long memberId) {
        if (affectedRows > 0) {
            return;
        }

        // 지금 상태 기준으로 알맞은 예외를 던진다.
        requireCommentablePost(postId, memberId);
        requireOwnComment(postId, commentId, memberId);

        // 조건은 맞는데 행이 안 바뀐 것이다. 원인을 모르는 채 성공으로 넘기지 않는다.
        throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
    }

    /**
     * 댓글을 달거나 지울 수 있는 게시글인지 확인하고 돌려준다.
     *
     * 노출 중인 글만 허용한다(DOMAIN.md 4.5). 게시글을 soft delete해도 댓글·좋아요·신고는
     * 그대로 남으므로, 이 검증이 없으면 삭제된 글에 요청만 따로 보내 댓글을 달 수 있다.
     *
     * 상태별 응답은 상세 접근 규칙(4.3)을 따르되 차단된 글만 다르다. 여기 도달하는
     * 차단된 글은 작성자 본인의 것뿐이고, 그 상대는 상세에서 이미 본문과 사유를 본 사람이라
     * 숨길 것이 없다. 404를 주면 왜 막혔는지 알 수 없다(조각 2와 같은 판단).
     */
    private PostDetailView requireCommentablePost(long postId, Long memberId) {
        PostDetailView post = requireVisiblePost(postId, memberId);

        if (post.status() != PostStatus.PUBLISHED) {
            // 노출 판단을 통과한 비-PUBLISHED는 자기 차단 글을 보는 작성자뿐이다.
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }

        return post;
    }

    /**
     * 그 댓글이 이 게시글에 달린, 요청자 본인의, 아직 남아 있는 댓글인지 확인한다.
     *
     * 넷을 구분하지 않고 전부 404다. 남의 댓글에 403을 주면 그 자리에 댓글이 있다는 사실이
     * 드러나고, 이미 지워진 댓글도 마찬가지다(DOMAIN.md 4.3의 이유가 그대로 적용된다).
     *
     * 지울 수 있는 사람은 작성자 본인뿐이다. 게시글 작성자에게도, 관리자에게도 댓글 삭제
     * 권한은 없다 — 관리자의 조치는 게시글 차단뿐이다(6.7).
     */
    private void requireOwnComment(long postId, long commentId, long memberId) {
        CommentView comment = communityMapper.findCommentById(commentId);

        if (comment == null
                || !comment.postId().equals(postId)
                || !comment.memberId().equals(memberId)
                || comment.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }

    /**
     * 고객 경로에서 이 게시글을 요청자에게 보여줄 수 있는지 확인하고 돌려준다.
     *
     * 삭제·차단·미존재를 구분하지 않는다. 구분하면 글의 존재가 드러난다(DOMAIN.md 4.3).
     */
    private PostDetailView requireVisiblePost(long postId, Long viewerId) {
        PostDetailView post = communityMapper.findPostById(postId);

        if (post == null || !isVisibleTo(post, viewerId)) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    /**
     * 조건부 UPDATE가 실제로 한 행을 바꿨는지 확인한다.
     *
     * SQL의 소유권·상태 조건은 검증과 UPDATE 사이의 변화를 막으라고 둔 것이다. 그런데
     * 갱신 행 수를 버리면 그 조건이 걸러 낸 순간이 성공으로 보인다. 관리자가 그 찰나에
     * 글을 차단하면 아무것도 바뀌지 않았는데 화면은 "삭제했습니다"라고 말한다.
     *
     * 0행이면 지금 상태를 다시 읽어 그에 맞는 응답을 낸다. 차단됐으면 403, 그 사이
     * 지워졌거나 소유자가 아니면 404다. 처음 진입할 때와 같은 규칙이다.
     */
    private void requireApplied(int affectedRows, long postId, long editorId) {
        if (affectedRows > 0) {
            return;
        }

        // 지금 상태 기준으로 알맞은 예외를 던진다.
        requireEditablePost(postId, editorId);

        // 여기까지 왔다면 조건은 맞는데 행이 안 바뀐 것이다. 원인을 모르는 채
        // 성공으로 넘기지 않는다.
        throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
    }

    /**
     * 요청자가 이 게시글을 고치거나 지울 수 있는지 확인하고 게시글을 돌려준다.
     * 상태별 응답은 상세 접근 규칙(DOMAIN.md 4.3)을 그대로 따르되 차단된 글만 다르다.
     *
     * 없는 글·남의 글·DELETED는 404다. 남에게 403을 주면 그 자리에 글이 있다는 사실이
     * 드러난다.
     *
     * 작성자의 BLOCKED 글은 403이다. 상세에서 이미 본문과 차단 사유를 보여 준 상대이므로
     * 존재를 숨길 것이 없고, 404를 주면 왜 막혔는지 알 수 없다. 차단된 글에 작성자가 할 수
     * 있는 일은 없다(4.2).
     */
    private PostDetailView requireEditablePost(long postId, long editorId) {
        PostDetailView post = communityMapper.findPostById(postId);

        if (post == null || !isAuthor(post, editorId) || post.status() == PostStatus.DELETED) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (post.status() == PostStatus.BLOCKED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }

        return post;
    }

    /**
     * 화면 선택지에 있는 카테고리인지 확인한다. 비활성 카테고리는 필터에도 글쓰기
     * 선택지에도 나오지 않는다(DOMAIN.md 6.8). 화면에 안 보이는 것과 저장할 수 없는 것은
     * 다르므로 여기서 막는다.
     */
    private void requireActiveCategory(Long categoryId) {
        if (!communityMapper.existsActiveCategory(categoryId)) {
            throw new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    /**
     * 고객 경로에서 이 게시글을 요청자에게 보여줄 수 있는지 판단한다. viewerId는 비로그인이면
     * null이다. 관리자도 고객 경로에서는 일반 회원과 똑같이 취급한다. 모든 상태를 보려면
     * /admin/community/{id}로 들어와야 한다(DOMAIN.md 4.3).
     */
    private boolean isVisibleTo(PostDetailView post, Long viewerId) {
        return switch (post.status()) {
            case PUBLISHED -> true;
            case BLOCKED -> isAuthor(post, viewerId);
            case DELETED -> false;
        };
    }

    /**
     * 요청자가 게시글 작성자인지 확인한다. 요청으로 전달된 회원 ID가 아니라 인증된 사용자
     * 기준으로 판단해야 하므로, 호출자는 인증 정보에서 얻은 값만 넘긴다(AGENTS.md).
     */
    private boolean isAuthor(PostDetailView post, Long viewerId) {
        return viewerId != null && viewerId.equals(post.memberId());
    }
}
