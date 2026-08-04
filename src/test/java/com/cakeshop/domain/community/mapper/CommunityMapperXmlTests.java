package com.cakeshop.domain.community.mapper;

import com.cakeshop.domain.community.dto.view.AdminPostSort;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML을 파싱해 SQL의 <b>형태</b>를 고정한다. DB를 띄우지 않는다.
 *
 * <p>여기서 잡는 것은 "결과는 같지만 방식이 틀린" 변경이다. 결과가 같으니 화면으로도,
 * 실행 쿼리 수 측정으로도 드러나지 않는다(PLAN.md R2).
 */
class CommunityMapperXmlTests {

    /**
     * 고객·관리자 매퍼를 <b>한 Configuration에</b> 함께 파싱한다.
     *
     * <p>파일이 갈렸어도 검사는 갈라 두지 않는다. 여기서 고정하는 것 대부분이 두 파일에
     * 걸친 규칙이기 때문이다 — 조회수·좋아요·차단이 같은 {@code posts} 행의
     * {@code updated_at}을 보존해야 하고, 스칼라 서브쿼리 규칙도 고객 목록과 관리자 목록
     * 양쪽에 걸린다. 파일마다 클래스를 나누면 그 짝이 눈에서 사라진다.
     */
    private static final Map<String, Class<?>> MAPPERS = Map.of(
            "mapper/community/CommunityMapper.xml", CommunityMapper.class,
            "mapper/community/CommunityAdminMapper.xml", CommunityAdminMapper.class);

    private Configuration configuration;

    @BeforeEach
    void parseMapperXml() throws Exception {
        configuration = new Configuration();

        for (String resource : MAPPERS.keySet()) {
            try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(
                        inputStream,
                        configuration,
                        resource,
                        configuration.getSqlFragments()
                ).parse();
            }
        }
    }

    /**
     * Mapper 인터페이스와 XML의 연결 누락을 잡는 안전장치 (선례: OrderMapperXmlTests).
     *
     * <p>매퍼마다 확인한다. 한쪽만 보면 <b>문장을 옮기다 흘린 것</b>이 드러나지 않는다 —
     * 인터페이스에서 지운 메서드의 SQL이 옛 파일에 남아 있어도, 그 XML을 안 보면 통과한다.
     */
    @Test
    void mapperInterfacesAndXmlStatementsStaySynchronized() {
        for (Class<?> mapper : MAPPERS.values()) {
            Set<String> interfaceMethods = Arrays.stream(mapper.getDeclaredMethods())
                    .map(Method::getName)
                    .collect(Collectors.toSet());

            String namespace = mapper.getName();

            Set<String> xmlStatements = configuration.getMappedStatementNames().stream()
                    .filter(name -> name.startsWith(namespace + "."))
                    .map(name -> name.substring(namespace.length() + 1))
                    .collect(Collectors.toSet());

            assertThat(xmlStatements)
                    .as(mapper.getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(interfaceMethods);
        }
    }

    /**
     * H1a — 목록의 댓글 수 집계가 스칼라 서브쿼리 형태를 유지하는지 확인한다.
     *
     * <p>{@code LEFT JOIN comments ... GROUP BY p.id}로 바꿔도 화면 결과는 동일하다.
     * 하지만 LIMIT이 GROUP BY 이후에 적용되어 전체 게시글 × 전체 댓글을 집계한 뒤 20개를
     * 잘라내므로 게시글이 쌓일수록 느려진다(DOMAIN.md 6.1).
     */
    @Test
    void findPublishedPosts_aggregatesCommentCountWithScalarSubquery() {
        String sql = normalizedSql("findPublishedPosts");

        assertThat(sql).contains("SELECT COUNT(*) FROM COMMENTS");
        assertThat(sql).doesNotContain("GROUP BY");
        assertThat(sql).doesNotContain("JOIN COMMENTS");
    }

    /**
     * 목록 정렬에 id tiebreaker가 남아 있는지 확인한다.
     *
     * <p>{@code created_at}만으로 정렬하면 같은 시각에 작성된 글이 페이지 경계에서
     * 중복되거나 누락된다(DOMAIN.md 6.1).
     */
    @Test
    void findPublishedPosts_ordersByCreatedAtWithIdTiebreaker() {
        String sql = normalizedSql("findPublishedPosts");

        assertThat(sql).contains("ORDER BY P.CREATED_AT DESC, P.ID DESC");
    }

    /**
     * 노출 조건이 status 하나로만 판단되는지 확인한다.
     *
     * <p>{@code blocked_at IS NULL} 같은 조건이 늘어나면 새 쿼리에서 하나를 빠뜨려
     * 차단된 글이 노출된다(DOMAIN.md 4.1).
     */
    @Test
    void publishedPostQueries_judgeVisibilityByStatusOnly() {
        for (String statement : new String[]{"findPublishedPosts", "countPublishedPosts"}) {
            String sql = normalizedSql(statement);

            assertThat(sql).as(statement).contains("P.STATUS = 'PUBLISHED'");
            assertThat(sql).as(statement).doesNotContain("BLOCKED_AT");
        }
    }

    /**
     * 조회수 UPDATE가 {@code updated_at}을 명시적으로 보존하는지 확인한다.
     *
     * <p>{@code posts.updated_at}은 ON UPDATE CURRENT_TIMESTAMP다. 이 지정을 빼면
     * 조회만 해도 값이 바뀌어 "수정됨" 표시(DOMAIN.md 6.3)가 켜진다.
     * 실제 동작은 {@code CommunityMapperTests}가 DB로 확인한다.
     */
    @Test
    void increaseViewCount_keepsUpdatedAtUntouched() {
        String sql = normalizedSql("increaseViewCount");

        assertThat(sql).contains("UPDATED_AT = P.UPDATED_AT");
        assertThat(sql).contains("STATUS = 'PUBLISHED'");
    }

    /**
     * 조회수 증가가 <b>스스로</b> 중복을 걸러 내는지 확인한다.
     *
     * <p>이 조건을 떼고 "이력을 먼저 넣어 보고 새로 들어갔으면 올린다"로 되돌리면
     * 잠금 순서가 뒤집힌다 — 이력 INSERT가 FK 확인으로 게시글 행에 공유 잠금을 걸고,
     * 그 뒤 이 UPDATE가 배타 잠금을 기다리면서 같은 글을 동시에 연 요청끼리 교착에 빠진다
     * (DOMAIN.md 6.2). 실제로 그렇게 만들었다가 잡힌 문제다.
     *
     * <p>동시 요청이 없으면 결과가 똑같아서 단일 스레드 테스트로는 드러나지 않는다.
     * 실제 교착은 {@code CommunityViewCountConcurrencyTests}가 잡는다.
     */
    @Test
    void increaseViewCount_filtersDuplicatesItselfSoItLocksThePostFirst() {
        String sql = normalizedSql("increaseViewCount");

        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).contains("POST_VIEWS");
        assertThat(sql).contains("VIEWED_ON = CURRENT_DATE");
    }

    /**
     * 이력 INSERT가 중복을 삼키지 않는지 확인한다.
     *
     * <p>{@code INSERT IGNORE}로 바꾸면 조회수만 오르고 이력은 없는 상태가 조용히 남는다.
     * 그 어긋남은 화면에 숫자가 조금 큰 모습으로만 나타나서 눈으로는 찾을 수 없다.
     */
    @Test
    void recordView_doesNotSwallowDuplicates() {
        String sql = normalizedSql("recordView");

        assertThat(sql).contains("INSERT INTO POST_VIEWS");
        assertThat(sql).doesNotContain("IGNORE");
        assertThat(sql).doesNotContain("ON DUPLICATE KEY");
    }

    /**
     * 댓글 목록이 최신순으로 잘리고 id tiebreaker를 유지하는지 확인한다.
     *
     * <p>오래된 순으로 바꿔도 댓글이 20건 이하인 화면에서는 결과가 같아 보인다. 드러나는
     * 것은 댓글이 한도를 넘은 글에서 <b>방금 쓴 댓글이 화면 밖에 남을 때</b>뿐이고,
     * 사용자에게는 등록이 안 된 것과 구분되지 않는다(DOMAIN.md 6.4).
     *
     * <p>tiebreaker가 빠지면 limit 경계에서 순서가 흔들려 "더 보기"가 댓글을 빠뜨린다.
     */
    @Test
    void findRecentComments_cutsFromTheNewestWithIdTiebreaker() {
        String sql = normalizedSql("findRecentComments");

        assertThat(sql).contains("ORDER BY C.CREATED_AT DESC, C.ID DESC");
        assertThat(sql).contains("LIMIT ?");
    }

    /**
     * 댓글 목록이 상태로 거르지 않는지 확인한다.
     *
     * <p>{@code WHERE status = 'PUBLISHED'}를 덧붙이면 삭제된 댓글이 목록에서 사라진다.
     * 화면은 멀쩡해 보이지만 자리 표시 정책(DOMAIN.md 4.4)이 조용히 뒤집힌다 —
     * 2차 대댓글에서 부모가 사라져 자식이 고아가 되는 것을 막으려고 둔 규칙이다.
     */
    @Test
    void findRecentComments_keepsDeletedCommentsAsPlaceholders() {
        String sql = normalizedSql("findRecentComments");

        assertThat(sql).doesNotContain("C.STATUS = 'PUBLISHED' ORDER BY");
        assertThat(sql).doesNotContain("AND C.STATUS = 'PUBLISHED'");
        // 대신 본문만 지운다. 자리 표시에 쓰지 않는 값을 화면까지 내려보내지 않는다.
        assertThat(sql).contains("CASE WHEN C.STATUS = 'PUBLISHED' THEN C.CONTENT END");
    }

    /**
     * 삭제된 댓글이 화면의 "댓글 N"에서 빠지는지 확인한다.
     *
     * <p>자리 표시를 포함한 행 수와 노출 중인 수를 <b>둘 다</b> 세야 한다. 하나로 합치면
     * 개수가 부풀거나 "더 보기"가 사라진다(DOMAIN.md 4.4).
     */
    @Test
    void countComments_separatesPlaceholderRowsFromPublishedCount() {
        String sql = normalizedSql("countComments");

        assertThat(sql).contains("COUNT(*) AS ROW_COUNT");
        assertThat(sql).contains("C.STATUS = 'PUBLISHED'");
        // 댓글이 하나도 없으면 SUM이 NULL이라 long 컴포넌트 매핑에서 터진다.
        assertThat(sql).contains("COALESCE(SUM(");
    }

    /**
     * 댓글 삭제가 행을 지우지 않고 소유권·게시글·상태를 모두 조건에 두는지 확인한다.
     *
     * <p>{@code DELETE FROM}으로 바꾸면 자리 표시가 사라지고, post_id 조건이 빠지면
     * 다른 글의 주소로 남의 화면에 없는 댓글을 지울 수 있다.
     */
    @Test
    void deleteComment_isSoftDeleteGuardedByOwnerPostAndStatus() {
        String sql = normalizedSql("deleteComment");

        assertThat(sql).contains("UPDATE COMMENTS SET STATUS = 'DELETED'");
        assertThat(sql).contains("POST_ID = ?");
        assertThat(sql).contains("MEMBER_ID = ?");
        assertThat(sql).contains("STATUS = 'PUBLISHED'");
    }

    /**
     * 좋아요 경로가 게시글 행을 <b>잠그면서</b> 읽는지, 그리고 그때 다른 테이블을 끌고
     * 들어가지 않는지 확인한다.
     *
     * <p>{@code FOR UPDATE}가 빠지면 교착이다. post_likes INSERT가 FK 확인으로 게시글 행에
     * 공유 잠금을 걸고, 뒤따르는 재계산이 같은 행의 배타 잠금을 기다린다 — 같은 글에 동시에
     * 좋아요를 누른 요청 둘이 서로를 기다린다(DOMAIN.md 6.5). 조회수에서 겪은 것과 같은
     * 모양이고, 동시 요청이 없으면 결과가 똑같아 단일 스레드로는 드러나지 않는다.
     * 실제 교착은 {@code CommunityLikeConcurrencyTests}가 잡는다.
     *
     * <p>조인을 붙이면 반대 방향으로 틀어진다. {@code FOR UPDATE}는 조인한 테이블의 행까지
     * 잠그므로, 카테고리를 조인해 두면 좋아요 한 번에 카테고리 행이 잠겨 <b>같은 분류의 모든
     * 글</b>이 서로 줄을 선다. 이쪽은 교착이 아니라 조용한 직렬화라 더 찾기 어렵다.
     */
    @Test
    void lockPost_locksThePostRowWithoutDraggingJoinedTables() {
        String sql = normalizedSql("lockPost");

        assertThat(sql).contains("FOR UPDATE");
        assertThat(sql).contains("FROM POSTS");
        assertThat(sql).doesNotContain("JOIN");
    }

    /**
     * 좋아요 추가가 중복 키만 넘기고 나머지 실패는 그대로 터뜨리는지 확인한다.
     *
     * <p>{@code INSERT IGNORE}로 바꾸면 UNIQUE 위반만이 아니라 <b>FK 위반과 값 잘림까지</b>
     * 경고로 낮춰서, 없는 게시글이나 없는 회원으로 들어온 요청이 조용히 성공한다
     * (DOMAIN.md 6.5). 화면에는 좋아요가 눌린 것처럼 보이고 숫자만 안 오른다.
     */
    @Test
    void insertLike_swallowsDuplicateKeysOnly() {
        String sql = normalizedSql("insertLike");

        assertThat(sql).contains("INSERT INTO POST_LIKES");
        assertThat(sql).contains("ON DUPLICATE KEY");
        assertThat(sql).doesNotContain("IGNORE");
    }

    /**
     * 좋아요 수가 증분이 아니라 재계산이고, {@code updated_at}을 보존하는지 확인한다.
     *
     * <p>증분으로 바꾸면 한 번 틀어진 값이 스스로 복구되지 않는다. 화면에는 숫자가 조금 다른
     * 모습으로만 나타나서 눈으로는 찾을 수 없다(DOMAIN.md 6.5).
     *
     * <p>{@code updated_at} 지정이 빠지면 좋아요를 받은 글마다 "(수정됨)"이 붙는다 —
     * 시드에 실제로 있던 버그이고, 조회수 UPDATE와 같은 자리다(6.3).
     */
    @Test
    void recalculateLikeCount_recountsAndKeepsUpdatedAtUntouched() {
        String sql = normalizedSql("recalculateLikeCount");

        assertThat(sql).contains("SELECT COUNT(*) FROM POST_LIKES");
        assertThat(sql).contains("UPDATED_AT = P.UPDATED_AT");
        assertThat(sql).doesNotContain("LIKE_COUNT + 1");
        assertThat(sql).doesNotContain("LIKE_COUNT - 1");
    }

    /**
     * 신고 INSERT가 중복을 삼키지 않는지 확인한다.
     *
     * <p>좋아요와 정반대다. {@code INSERT IGNORE}나 {@code ON DUPLICATE KEY UPDATE}로
     * 바꾸면 중복 신고가 조용히 성공하는데, 신고자에게는 접수된 것으로 보이고 실제로는
     * 아무 일도 일어나지 않는다(DOMAIN.md 6.6). 정상 흐름에서는 결과가 같아 보인다.
     */
    @Test
    void insertReport_doesNotSwallowDuplicates() {
        String sql = normalizedSql("insertReport");

        assertThat(sql).doesNotContain("IGNORE");
        assertThat(sql).doesNotContain("ON DUPLICATE KEY");
    }

    /**
     * "이미 신고했는지"를 상태로 거르지 않는지 확인한다.
     *
     * <p>{@code status = 'PENDING'}을 덧붙이면 관리자가 신고를 처리한 뒤 같은 사람이 다시
     * 신고할 수 있게 된다. 신고에는 취소도 재신고도 없다(DOMAIN.md 6.6). UNIQUE 제약은
     * 그대로라 INSERT가 터지므로, 화면에는 "신고했습니다"가 아니라 500이 나온다.
     */
    @Test
    void existsReport_ignoresReportStatus() {
        String sql = normalizedSql("existsReport");

        assertThat(sql).doesNotContain("PENDING");
    }

    /**
     * 관리자 목록이 미처리 신고 수를 스칼라 서브쿼리로 세는지 확인한다.
     *
     * <p>H1a와 같은 이유다 — {@code LEFT JOIN post_reports ... GROUP BY}로 바꾸면 LIMIT이
     * 집계 이후에 적용되어 전체를 훑는다(DOMAIN.md 6.1). 결과는 같아서 화면으로는 모른다.
     */
    @Test
    void findPostsForAdmin_countsPendingReportsWithScalarSubquery() {
        String sql = normalizedSql("findPostsForAdmin");

        assertThat(sql).contains("SELECT COUNT(*) FROM POST_REPORTS");
        assertThat(sql).doesNotContain("GROUP BY");
        assertThat(sql).doesNotContain("JOIN POST_REPORTS");
    }

    /**
     * 정렬 분기가 둘 다 id tiebreaker를 유지하는지 확인한다.
     *
     * <p>기본 분기만 보면 새 분기가 tiebreaker 없이 들어와도 통과한다. 미처리 신고 수는
     * 0이 대부분이라 동점이 {@code created_at}보다 훨씬 잦고, tiebreaker가 없으면 페이지
     * 경계에서 글이 사라지거나 두 번 나온다(DOMAIN.md 6.7).
     */
    @Test
    void findPostsForAdmin_everySortBranchKeepsIdTiebreaker() {
        assertThat(normalizedSql("findPostsForAdmin", Map.of("sort", AdminPostSort.LATEST)))
                .contains("ORDER BY P.CREATED_AT DESC, P.ID DESC");

        assertThat(normalizedSql("findPostsForAdmin", Map.of("sort", AdminPostSort.REPORTS)))
                .contains("ORDER BY PENDING_REPORT_COUNT DESC, P.ID DESC");
    }

    /**
     * 관리자 목록이 상태로 거르지 <b>않는</b> 것을 기본으로 두는지 확인한다.
     *
     * <p>고객 목록과 정반대다. 관리자는 삭제·차단된 글까지 본다(DOMAIN.md 4.3).
     * 조건이 상수로 박히면 필터가 무엇을 고르든 같은 목록이 나온다.
     */
    @Test
    void findPostsForAdmin_withoutStatusFilter_hasNoStatusCondition() {
        String sql = normalizedSql("findPostsForAdmin");

        assertThat(sql).doesNotContain("STATUS = 'PUBLISHED'");
        assertThat(sql).doesNotContain("STATUS = 'BLOCKED'");
    }

    /**
     * 차단이 전이 규칙을 SQL에서도 지키고 {@code updated_at}을 보존하는지 확인한다.
     *
     * <p>{@code status = 'PUBLISHED'} 조건이 빠지면 이미 차단된 글의 조치 기록이 덮이고,
     * 작성자가 지운 글이 되살아난다(DOMAIN.md 4.2). 둘 다 화면에는 성공으로 보인다.
     *
     * <p>{@code updated_at} 지정이 빠지면 차단된 글마다 "(수정됨)"이 붙는다. 작성자가
     * 고치지도 않은 글에 붙는 표시라 원인을 찾을 수 없다(6.3, 조회수·좋아요와 같은 자리).
     */
    @Test
    void blockPost_requiresPublishedAndKeepsUpdatedAtUntouched() {
        String sql = normalizedSql("blockPost");

        assertThat(sql).contains("P.STATUS = 'PUBLISHED'");
        assertThat(sql).contains("BLOCKED_AT");
        assertThat(sql).contains("BLOCKED_BY");
        assertThat(sql).contains("UPDATED_AT = P.UPDATED_AT");
    }

    /**
     * 차단 해제가 blocked_* 를 지우지 않고, BLOCKED에서만 동작하는지 확인한다.
     *
     * <p>기록을 NULL로 되돌리면 같은 글이 두 번째로 신고됐을 때 앞선 조치를 알 수 없다
     * (DOMAIN.md 4.2). 조건이 빠지면 지워진 글이 이 UPDATE로 되살아난다.
     */
    @Test
    void unblockPost_keepsBlockRecordAndRequiresBlocked() {
        String sql = normalizedSql("unblockPost");

        assertThat(sql).contains("P.STATUS = 'BLOCKED'");
        assertThat(sql).contains("UPDATED_AT = P.UPDATED_AT");
        assertThat(sql).doesNotContain("BLOCKED_AT = NULL");
        assertThat(sql).doesNotContain("BLOCKED_REASON = NULL");
        assertThat(sql).doesNotContain("BLOCKED_BY = NULL");
    }

    /**
     * 신고를 닫는 UPDATE가 이미 처리된 신고를 건드리지 않는지 확인한다.
     *
     * <p>조건이 빠지면 차단 → 해제 → 재차단 흐름에서 REJECTED였던 신고가 RESOLVED로 바뀐다.
     * 상태는 "그때 무엇으로 조치했다"는 기록이므로 덮이면 거짓이 된다(DOMAIN.md 6.6).
     */
    @Test
    void closePendingReports_touchesOnlyPendingRows() {
        String sql = normalizedSql("closePendingReports");

        assertThat(sql).contains("PR.STATUS = 'PENDING'");
    }

    /** 공백을 하나로 줄이고 대문자로 바꿔 들여쓰기·줄바꿈 차이를 무시한다. */
    private String normalizedSql(String statementId) {
        return normalizedSql(statementId, Map.of());
    }

    /** 분기가 있는 문장은 파라미터를 갈아 끼워 <b>분기마다</b> 형태를 본다. */
    private String normalizedSql(String statementId, Map<String, Object> overrides) {
        MappedStatement statement = statementOf(statementId);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("categoryId", null);
        parameters.put("postId", 1L);
        parameters.put("commentId", 1L);
        parameters.put("memberId", 1L);
        parameters.put("size", 20);
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        parameters.put("viewerKey", "M:1");
        parameters.put("reporterId", 1L);
        parameters.put("reason", "사유");
        parameters.put("adminId", 1L);
        parameters.put("status", null);
        parameters.put("sort", null);
        parameters.putAll(overrides);

        return statement.getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    /**
     * 어느 매퍼의 문장인지 이름으로 찾는다. 호출부는 파일이 갈린 것을 몰라도 된다 —
     * 검사가 확인하는 것은 SQL의 형태이지 어느 파일에 적혀 있느냐가 아니다.
     */
    private MappedStatement statementOf(String statementId) {
        for (Class<?> mapper : MAPPERS.values()) {
            String name = mapper.getName() + "." + statementId;

            if (configuration.hasStatement(name)) {
                return configuration.getMappedStatement(name);
            }
        }

        throw new IllegalArgumentException("어느 커뮤니티 매퍼에도 없는 문장이다: " + statementId);
    }
}
