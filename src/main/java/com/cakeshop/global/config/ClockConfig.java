package com.cakeshop.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각을 읽는 경로가 시계를 주입받게 한다.
 *
 * LocalDate.now()를 코드 안에서 직접 부르면 그 판단을 테스트가 고정할 수 없다.
 * 인기글 배치는 '어제'가 무엇인지에 따라 집계 대상이 통째로 달라지므로, 시계를
 * 밖에서 주는 것이 곧 그 판단을 검사 가능하게 만드는 일이다.
 *
 * 시간대를 시스템 기본값이 아니라 서울로 못 박는다. 배치가 넘기는 날짜와 DB의
 * created_at이 같은 기준이어야 하고, 그 반대편(JDBC 세션 시간대)은 application.yml이
 * 맡는다(docs/community/PLAN.md D10). 한쪽만 맞추면 두 벌이 되므로 함께 본다.
 */
@Configuration
public class ClockConfig {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SEOUL);
    }
}
