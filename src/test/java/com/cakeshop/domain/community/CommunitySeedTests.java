package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 로컬 시드를 실행한 뒤에도 커뮤니티 카테고리가 남는지 확인한다.
 *
 * <p>{@code post_categories}는 migration이 넣는 참조 데이터다. 없으면
 * {@code posts.category_id}가 NOT NULL FK이므로 글쓰기가 아예 불가능해지고, 목록의 카테고리
 * 필터도 빈 채로 뜬다(docs/community/DOMAIN.md 6.8).
 *
 * <p>공용 파일인 {@code seed-local.sql}은 이 테이블을 지운다. 그래서 커뮤니티 전용
 * {@code seed-community.sql}이 다시 채운다. 이 테스트는 "누가 채우는지"가 아니라
 * <b>둘을 순서대로 실행하면 카테고리가 남는다</b>는 결과를 고정한다. 나중에
 * {@code seed-local.sql} 쪽이 지우지 않도록 바뀌어도 이 테스트는 그대로 통과한다.
 *
 * <p>DB를 띄우지 않고 시드 파일 자체를 읽는다. 시드를 실제로 실행하는 검증은
 * {@code ALTER TABLE}의 암묵적 커밋 때문에 다른 테스트의 데이터까지 건드린다.
 */
class CommunitySeedTests {

    private static final String LOCAL_SEED = "db/seed/seed-local.sql";
    private static final String COMMUNITY_SEED = "db/seed/seed-community.sql";

    @Test
    void seedScripts_leaveMvpCategoriesInPlace() throws IOException {
        String localSeed = normalize(readSeed(LOCAL_SEED));
        String communitySeed = normalize(readSeed(COMMUNITY_SEED));

        if (localSeed.contains("DELETE FROM POST_CATEGORIES")) {
            assertThat(communitySeed)
                    .as("seed-local.sql이 카테고리를 지우므로 seed-community.sql이 다시 채워야 한다")
                    .contains("INSERT INTO POST_CATEGORIES")
                    .contains("'QNA'")
                    .contains("'REVIEW'")
                    .contains("'FREE'");
        }
    }

    /** 커뮤니티 시드가 카테고리를 지우면 seed-local.sql을 고쳐도 소용이 없다. */
    @Test
    void communitySeed_doesNotDeletePostCategories() throws IOException {
        assertThat(normalize(readSeed(COMMUNITY_SEED)))
                .doesNotContain("DELETE FROM POST_CATEGORIES");
    }

    /**
     * 게시글·댓글은 지워도 되는 샘플 데이터다. 커뮤니티 시드가 몇 번을 실행해도 같은 결과가
     * 되도록 초기화를 유지하는지 확인한다.
     */
    @Test
    void communitySeed_resetsSampleContent() throws IOException {
        String communitySeed = normalize(readSeed(COMMUNITY_SEED));

        assertThat(communitySeed).contains("DELETE FROM POSTS");
        assertThat(communitySeed).contains("DELETE FROM COMMENTS");
        assertThat(communitySeed).contains("DELETE FROM POST_LIKES");
    }

    /**
     * 1차에는 대댓글이 없다. 시드가 값을 넣으면 화면에 나올 방법이 없는 데이터가 생기고,
     * 2차 대댓글 작업 때 "언제 들어간 값인지" 모르는 행이 남는다(DOMAIN.md 6.4).
     */
    @Test
    void communitySeed_doesNotUseParentCommentId() throws IOException {
        assertThat(normalize(readSeed(COMMUNITY_SEED)))
                .doesNotContain("PARENT_COMMENT_ID");
    }

    private String readSeed(String location) throws IOException {
        return new String(
                new ClassPathResource(location).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    /**
     * 백틱과 공백 표기 차이를 무시한다.
     *
     * <p>주석을 먼저 걷어낸다. 시드 주석에는 "parent_comment_id를 넣지 않는다"처럼 규칙을
     * 설명하는 문장이 들어 있어서, 주석을 남겨 두면 설명 자체가 위반으로 잡힌다.
     */
    private String normalize(String sql) {
        String withoutComments = sql.replaceAll("(?m)--.*$", " ");

        return withoutComments.replace("`", "").replaceAll("\\s+", " ").toUpperCase();
    }
}
