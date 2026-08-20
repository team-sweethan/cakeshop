#!/usr/bin/env bash
# 측정용으로 앱을 띄운다. 개발용으로 쓰지 않는다.
#
# 로컬 개발 설정은 편의를 위해 성능을 일부러 깎아 둔 것이라, 그대로 재면
# 엉뚱한 곳이 범인으로 잡힌다. 여기서 그 셋을 되돌리고 힙을 고정한다.
#
#   ./monitoring/run-app-for-measurement.sh          # 측정용 DB(cakeshop_perf)
#   ./monitoring/run-app-for-measurement.sh dev      # 개발용 DB(원래 .env 값)
#
# 멈출 때는 Ctrl+C.

set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "루트에 .env 가 없다"; exit 1; }

# .env 를 `source` 하지 않는다. 값에 $ 가 들어 있으면 셸이 그것을 변수로 해석해서
# 조용히 다른 값이 된다 — 이 저장소의 RDS_PASSWORD 가 실제로 그렇다.
# 필요한 키만 글자 그대로 읽는다.
env_get() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r'; }

LOCAL_DB_HOST="$(env_get LOCAL_DB_HOST)"
LOCAL_DB_PORT="$(env_get LOCAL_DB_PORT)"
LOCAL_DB_DATABASE="$(env_get LOCAL_DB_DATABASE)"

DB="${1:-perf}"
if [ "$DB" = "perf" ]; then
    TARGET_DB="cakeshop_perf"
else
    TARGET_DB="$LOCAL_DB_DATABASE"
fi

JAR="build/libs/cakeshop-0.0.1-SNAPSHOT.jar"
[ -f "$JAR" ] || { echo "먼저 ./gradlew bootJar"; exit 1; }

echo "대상 DB : $TARGET_DB"
echo "주소    : http://localhost:8081"
echo "지표    : http://localhost:8081/actuator/prometheus"
echo

# ── 왜 bootRun 이 아니라 java -jar 인가 ────────────────────────────────
# bootRun 에는 devtools 가 붙는다. 파일이 바뀌었는지 지켜보는 스레드와 별도
# 클래스로더가 함께 돌아서 측정값에 섞인다. bootJar 로 만든 실행 파일에는
# devtools 가 빠져 있다(developmentOnly).
#
# ── 왜 힙을 고정하나 ──────────────────────────────────────────────────
# 안 정하면 JVM 이 실행할 때마다 형편에 맞게 잡는다. 그러면 메모리 정리가
# 도는 시점이 매번 달라져서 A/B 비교가 성립하지 않는다. 시작과 최대를 같게
# 두면 중간에 늘리느라 멈추는 일도 없다.
exec java \
    -Xms512m -Xmx512m \
    -XX:+UseG1GC \
    -Xlog:gc:file=build/gc.log:time,uptime:filecount=5,filesize=10M \
    -jar "$JAR" \
    --server.port=8081 \
    --spring.profiles.active=local,s3,oauth \
    "--spring.datasource.url=jdbc:mariadb://${LOCAL_DB_HOST:-localhost}:${LOCAL_DB_PORT}/${TARGET_DB}?connectionTimeZone=+09:00&forceConnectionTimeZoneToSession=true" \
    `# 로컬은 템플릿 캐시가 꺼져 있다. 켜지 않으면 요청마다 화면 틀을 다시 읽어서` \
    `# "화면 그리기가 제일 느리다"는, 배포에는 없는 결론이 나온다.` \
    --spring.thymeleaf.cache=true \
    `# 로컬만 로그인 없이 열어 두는 화면이 있다. 켜 두면 권한 검사 비용이 배포와 달라진다.` \
    --app.mockup.public-preview=false \
    `# 수집기가 지표를 가져갈 수 있게 연다. 배포 기본값은 닫혀 있다.` \
    --app.monitoring.metrics-public=true
