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
        assertOnlySeedRankingRemains();
        assertThat(activeCategoryCodes()).containsExactly("QNA", "REVIEW", "FREE");
        assertThat(count("SELECT COUNT(*) FROM comments WHERE parent_comment_id IS NOT NULL"))
                .isEqualTo(3);
    }

    @Test
    void communitySeed_leavesPopularSectionRenderable() {
        runSeeds(LOCAL_SEED, COMMUNITY_SEED);

        assertThat(count("SELECT COUNT(*) FROM popular_post_batch_runs")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM daily_popular_posts")).isPositive();
        // 시각을 다시 박아 두면 여기서 걸린다. 이틀까지 봐주는 것은 자정을 넘겨 도는
        // 실행 때문이고, 박힌 날짜는 그 폭으로는 절대 통과하지 못한다.
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM popular_post_batch_runs
                WHERE ranking_date < CURDATE() - INTERVAL 2 DAY
                """))
                .isZero();
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM popular_post_batch_runs r
                WHERE r.post_count <> (
                    SELECT COUNT(*)
                    FROM daily_popular_posts d
                    WHERE d.ranking_date = r.ranking_date
                )
                """))
                .isZero();
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM daily_popular_posts d
                JOIN posts p ON p.id = d.post_id
                WHERE p.status <> 'PUBLISHED'
                """))
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

    /*
     * 시드가 확정해 두는 날짜(어제)와 겹치지 않도록 충분히 과거를 쓴다. 날짜를 박아 두면
     * 시드와 같은 날에 걸리는 날이 오고, 그날 하루만 PK 충돌로 실패한다.
     */
    private static final String FOREIGN_RANKING_DATE = "CURDATE() - INTERVAL 30 DAY";

    private void insertPopularPostBatchResult() {
        Long postId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM posts WHERE status = 'PUBLISHED'",
                Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO daily_popular_posts (
                    ranking_date, ranking, post_id, popularity_score,
                    view_count, like_count, comment_count
                ) VALUES (%s, 1, ?, 1, 1, 0, 0)
                """.formatted(FOREIGN_RANKING_DATE),
                postId);
        jdbcTemplate.update(
                """
                INSERT INTO popular_post_batch_runs (ranking_date, post_count)
                VALUES (%s, 1)
                """.formatted(FOREIGN_RANKING_DATE));
    }

    private void assertPopularPostBatchStateIsCleared() {
        assertThat(count("SELECT COUNT(*) FROM daily_popular_posts")).isZero();
        assertThat(count("SELECT COUNT(*) FROM popular_post_batch_runs")).isZero();
    }

    /* 커뮤니티 시드는 남의 확정 기록을 걷어내고 자기 것 하나만 남긴다. */
    private void assertOnlySeedRankingRemains() {
        assertThat(count("SELECT COUNT(DISTINCT ranking_date) FROM daily_popular_posts"))
                .isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM popular_post_batch_runs WHERE ranking_date = "
                        + FOREIGN_RANKING_DATE))
                .isZero();
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
