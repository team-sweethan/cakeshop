package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 커뮤니티 시드의 참조 데이터와 삭제 순서를 검증한다. */
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

    /** 커뮤니티 시드는 카테고리를 보존한다. */
    @Test
    void communitySeed_doesNotDeletePostCategories() throws IOException {
        assertThat(normalize(readSeed(COMMUNITY_SEED)))
                .doesNotContain("DELETE FROM POST_CATEGORIES");
    }

    /** 샘플 게시글과 댓글 데이터를 초기화한다. */
    @Test
    void communitySeed_resetsSampleContent() throws IOException {
        String communitySeed = normalize(readSeed(COMMUNITY_SEED));

        assertThat(communitySeed).contains("DELETE FROM POSTS");
        assertThat(communitySeed).contains("DELETE FROM COMMENTS");
        assertThat(communitySeed).contains("DELETE FROM POST_LIKES");
    }

    /** 1차 시드는 대댓글 컬럼을 사용하지 않는다. */
    @Test
    void communitySeed_doesNotUseParentCommentId() throws IOException {
        assertThat(normalize(readSeed(COMMUNITY_SEED)))
                .doesNotContain("PARENT_COMMENT_ID");
    }

    /** 인기글 자식 행을 게시글보다 먼저 삭제한다. */
    @Test
    void seedScripts_deletePopularPostRowsBeforePosts() throws IOException {
        for (String location : new String[] {LOCAL_SEED, COMMUNITY_SEED}) {
            String seed = normalize(readSeed(location));

            int rankings = seed.indexOf("DELETE FROM DAILY_POPULAR_POSTS");
            int posts = seed.indexOf("DELETE FROM POSTS");

            assertThat(rankings)
                    .as("%s이 daily_popular_posts를 지워야 한다", location)
                    .isNotNegative();
            assertThat(rankings)
                    .as("%s에서 daily_popular_posts가 posts보다 먼저 지워져야 한다", location)
                    .isLessThan(posts);
        }
    }

    /** 인기글 배치 실행 기록도 초기화한다. */
    @Test
    void seedScripts_clearBatchRunHistory() throws IOException {
        for (String location : new String[] {LOCAL_SEED, COMMUNITY_SEED}) {
            assertThat(normalize(readSeed(location)))
                    .as("%s이 popular_post_batch_runs를 비워야 한다", location)
                    .contains("DELETE FROM POPULAR_POST_BATCH_RUNS");
        }
    }

    private String readSeed(String location) throws IOException {
        return new String(
                new ClassPathResource(location).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    /** 주석, 백틱, 공백 차이를 제거한다. */
    private String normalize(String sql) {
        String withoutComments = sql.replaceAll("(?m)--.*$", " ");

        return withoutComments.replace("`", "").replaceAll("\\s+", " ").toUpperCase();
    }
}
