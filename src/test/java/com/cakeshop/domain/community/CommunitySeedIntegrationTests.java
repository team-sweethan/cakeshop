package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;

/** 로컬 시드와 커뮤니티 시드의 실제 MariaDB 재실행 계약을 검증한다. */
@SpringBootTest
@MariaDbIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunitySeedIntegrationTests {

    private static final String LOCAL_SEED = "db/seed/seed-local.sql";
    private static final String COMMUNITY_SEED = "db/seed/seed-community.sql";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedScripts_canBeReappliedAfterPopularPostBatchHasRun() {
        runSeeds(LOCAL_SEED, COMMUNITY_SEED);
        insertPopularPostBatchResult();

        runSeeds(LOCAL_SEED);

        assertPopularPostBatchStateIsCleared();

        runSeeds(COMMUNITY_SEED);
        SeedSnapshot expected = snapshot();
        insertPopularPostBatchResult();

        runSeeds(COMMUNITY_SEED);

        assertThat(snapshot()).isEqualTo(expected);
        assertPopularPostBatchStateIsCleared();
        assertThat(activeCategoryCodes()).containsExactly("QNA", "REVIEW", "FREE");
        assertThat(count("SELECT COUNT(*) FROM comments WHERE parent_comment_id IS NOT NULL"))
                .isZero();
    }

    private void runSeeds(String... locations) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        for (String location : locations) {
            populator.addScript(new ClassPathResource(location));
        }
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private void insertPopularPostBatchResult() {
        Long postId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM posts WHERE status = 'PUBLISHED'",
                Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO daily_popular_posts (
                    ranking_date, ranking, post_id, popularity_score,
                    view_count, like_count, comment_count
                ) VALUES ('2026-08-08', 1, ?, 1, 1, 0, 0)
                """,
                postId);
        jdbcTemplate.update(
                """
                INSERT INTO popular_post_batch_runs (ranking_date, post_count)
                VALUES ('2026-08-08', 1)
                """);
    }

    private void assertPopularPostBatchStateIsCleared() {
        assertThat(count("SELECT COUNT(*) FROM daily_popular_posts")).isZero();
        assertThat(count("SELECT COUNT(*) FROM popular_post_batch_runs")).isZero();
    }

    private SeedSnapshot snapshot() {
        return new SeedSnapshot(
                count("SELECT COUNT(*) FROM post_categories"),
                count("SELECT COUNT(*) FROM posts"),
                count("SELECT COUNT(*) FROM comments"),
                count("SELECT COUNT(*) FROM post_likes"),
                count("SELECT COUNT(*) FROM post_views"),
                count("SELECT COUNT(*) FROM post_reports"));
    }

    private List<String> activeCategoryCodes() {
        return jdbcTemplate.queryForList(
                """
                SELECT code
                FROM post_categories
                WHERE is_active = 1
                ORDER BY sort_order
                """,
                String.class);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private record SeedSnapshot(
            int categoryCount,
            int postCount,
            int commentCount,
            int likeCount,
            int viewCount,
            int reportCount) {
    }
}
