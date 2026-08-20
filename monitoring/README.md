# 로컬 관측 스택

성능을 재려면 먼저 숫자가 보여야 한다. 이 폴더는 그 숫자를 모으고 그리는 도구만 담는다.
**무엇을 볼지**는 `docs/community/MONITORING.md`와 `docs/review/MONITORING.md`가 정한다.

> **로컬 전용이다.** Grafana가 로그인 없이 열려 있고 Prometheus도 인증이 없다.
> 이 스택을 외부에 노출하지 않는다.

## 켜는 순서

**1. 앱을 띄운다** — 지표를 내보내는 쪽이 먼저 있어야 수집기가 붙는다.

```bash
./gradlew bootJar && java -jar build/libs/cakeshop-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,s3,oauth
```

`bootRun`이 아니라 `bootJar` + `java -jar`인 이유는 devtools 때문이다. `bootRun`에는
재시작 감시 스레드와 별도 클래스로더가 붙어서 측정값에 섞인다.

**2. 지표가 실제로 나오는지 확인한다.**

```bash
curl -s localhost:8080/actuator/prometheus | head -20
```

`http_server_requests_seconds`나 `jvm_memory_used_bytes` 같은 줄이 보이면 된다.
비어 있거나 401/403이면 아래 `안 될 때`를 본다.

**3. 수집기와 그래프를 띄운다.**

```bash
docker compose -f monitoring/docker-compose.yml up -d
```

- Prometheus <http://localhost:9090> — `Status → Targets`에서 `cakeshop`이 **UP**이어야 한다
- Grafana <http://localhost:3000> — Prometheus 데이터소스가 이미 연결되어 있다

**4. 끝나면 내린다.**

```bash
docker compose -f monitoring/docker-compose.yml down
```

## 처음 볼 지표

Grafana의 `Explore`에 그대로 붙여 넣으면 된다.

| 무엇 | 쿼리 |
|---|---|
| 화면별 응답 시간 (p95) | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[1m])))` |
| 초당 요청 수 | `sum by (uri) (rate(http_server_requests_seconds_count[1m]))` |
| **커넥션을 기다리는 요청** | `hikaricp_connections_pending` |
| 쓰고 있는 커넥션 | `hikaricp_connections_active` |
| 바쁜 Tomcat 스레드 | `tomcat_threads_busy_threads` |
| 힙 사용량 | `jvm_memory_used_bytes{area="heap"}` |
| GC로 멈춘 시간 | `rate(jvm_gc_pause_seconds_sum[1m])` |
| 비동기 큐에 쌓인 작업 | `executor_queued_tasks` |

`hikaricp_connections_pending`이 0에서 떠오르는 순간이 **병목이 DB 앞으로 옮겨간 시점**이다.
로컬은 커넥션 풀이 기본값 10인데 Tomcat 스레드는 기본 200이라, 그 차이가 여기서 보인다.

## MariaDB는 여기에 안 나온다

잠금 대기·교착 같은 DB 안쪽 숫자는 Micrometer가 주지 않는다. 그건 DB에 직접 물어본다.

```sql
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';
SHOW GLOBAL STATUS LIKE 'Innodb_deadlocks';
SHOW ENGINE INNODB STATUS;   -- LATEST DETECTED DEADLOCK 절을 본다
```

느린 쿼리도 따로 켠다.

```sql
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;
SET GLOBAL log_queries_not_using_indexes = ON;
```

## 안 될 때

| 증상 | 원인 | 해결 |
|---|---|---|
| `/actuator/prometheus`가 **404** | 레지스트리가 없거나 노출 목록에 없다 | `local` 프로필로 떴는지 확인한다. 노출은 `application.yml`의 `local` 블록에만 있다 |
| `/actuator/prometheus`가 **403** | 로그인 요구에 걸렸다 | `app.monitoring.metrics-public`이 `true`인지 본다. 기본값은 `false`이고 `local`에서만 켠다 |
| Targets에서 `cakeshop`이 **DOWN** | 컨테이너에서 호스트가 안 보인다 | 앱이 8080에 떠 있는지, `host.docker.internal`이 풀리는지 확인한다 |
| 그래프가 **비어 있다** | 트래픽이 없다 | 지표는 요청이 있어야 생긴다. 화면을 몇 번 눌러 본다 |

## 왜 배포에서는 닫혀 있나

`/actuator/prometheus` 응답에는 **모든 엔드포인트의 URI 패턴**과 커넥션 풀·JVM 내부 상태가 들어 있다.
`AGENTS.md`가 "내부 경로를 응답이나 로그에 노출하지 않는다"고 정하고 있어서,
기본값을 닫아 두고 `local` 프로필에서만 연다(`app.monitoring.metrics-public`).

배포 환경에서 지표가 필요해지면 공개가 아니라 **접근 제한**으로 푼다 — 내부망 한정, 또는 인증 추가.
그건 `global` 담당과 함께 정할 일이다.
