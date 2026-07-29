#!/usr/bin/env bash
#
# 원격 브랜치 현황을 마크다운 문서와 HTML 요약 페이지로 만들어 낸다.
#
# Git 메타데이터만 읽으므로 DB도 빌드도 필요 없다. 실행 전에 origin의 모든 브랜치를
# 받아 둬야 한다(워크플로에서는 fetch-depth: 0 으로 체크아웃한다).
#
#   ./.github/scripts/branch-status.sh [마크다운경로] [HTML경로]
#
# 두 출력은 같은 수집 결과에서 나온다. 각자 git 을 다시 돌면 그 사이 push 가 끼어들
# 때 두 파일이 서로 다른 시점을 가리키게 되므로, 수집은 한 번만 한다.
#
# EXCLUDE_BRANCH 환경 변수로 브랜치 하나를 보고서에서 뺄 수 있다. 워크플로가 결과를
# 담아 두는 작업 브랜치를 스스로 보고하지 않게 하는 용도다.
#
set -euo pipefail

OUT_MD="${1:-docs/branch_update.md}"
OUT_HTML="${2:-docs/branch_status.html}"
BASE="origin/dev"
PREFIX="refs/remotes/origin/"
HEAD_REF="refs/remotes/origin/HEAD"
EXCLUDE="${EXCLUDE_BRANCH:-}"

today="$(TZ=Asia/Seoul date +%Y-%m-%d)"
# 표의 모든 수치는 이 커밋을 기준으로 센 값이다. 문서를 게시하면 dev 가 앞으로
# 나아가므로, 어느 시점의 스냅샷인지 문서에 남긴다.
base_sha="$(git rev-parse --short "$BASE")"

RECORDS="$(mktemp)"
trap 'rm -f "$RECORDS"' EXIT

# --- 값 이스케이프 ------------------------------------------------------------

# 값에 들어올 수 있는 줄 구분 제어문자를 공백 하나로 바꾼다. git 은 작성자명과 커밋
# 제목에 단독 CR 을 허용하고 NUL 로 구분해 읽는 이 스크립트는 그것을 그대로 보존한다.
# 마크다운 표는 CR 을 줄바꿈으로 읽어 행이 중간에서 끊어지지만 HTML 카드는 그대로
# 버티므로, 손대지 않으면 두 출력이 서로 다른 내용을 보여 준다. 양쪽에 함께 적용해
# 같은 데이터에서 같은 결과가 나오게 한다.
oneline() {
    printf '%s' "$1" | tr '\r\n\f\v' '    '
}

# 마크다운 표의 셀에 들어가는 값. 브랜치명과 사람 이름에는 '|' 가 들어올 수 있고,
# 그대로 두면 열 구분자로 읽혀 행이 깨진다.
#
# 백슬래시를 먼저 겹쳐 두지 않으면 원래 '\|' 이던 자리가 '\\|' 이 되고, GFM 은 앞
# 백슬래시가 뒤 백슬래시를 이스케이프한 것으로 읽어 파이프가 다시 구분자가 된다.
#
# bash 의 ${v//.../...} 는 패턴과 치환문 양쪽에서 백슬래시를 삼켜 버려 이 두 단계를
# 제대로 표현하지 못한다. sed 로 처리한다.
cell() {
    oneline "$1" | sed -e 's/\\/\\\\/g' -e 's/|/\\|/g'
}

# HTML 텍스트 노드에 들어가는 값. 커밋 요약에 '<' 나 '&' 가 들어오면 태그나 엔티티로
# 읽히므로 먼저 '&' 를 바꾼 뒤 나머지를 바꾼다. 순서가 바뀌면 이미 만든 엔티티의
# '&' 가 다시 치환된다.
html() {
    oneline "$1" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' -e 's/"/\&quot;/g'
}

# --- 수집 --------------------------------------------------------------------

# 보고 대상이 아닌 ref 인가. 인자는 전체 refname.
skip_ref() {
    [ "$1" = "$HEAD_REF" ] && return 0
    [ -n "$EXCLUDE" ] && [ "${1#"$PREFIX"}" = "$EXCLUDE" ] && return 0
    return 1
}

# 브랜치마다 8개 필드를 NUL 로 이어 붙여 RECORDS 에 쌓는다. 레코드 구분자는 따로 두지
# 않고, 읽는 쪽에서 8개씩 끊는다. 커밋 요약에는 줄바꿈이 들어올 수 있어 줄 단위
# 구분자를 쓸 수 없다.
#
#   name, date, author, sha, subject, ahead, behind, status
#
# status 는 코드로만 담는다. 표시 문구는 마크다운과 HTML 이 각각 다르게 쓰므로
# 렌더러에서 정한다.
collect() {
    # refname:short 는 origin/HEAD 를 그냥 "origin" 으로 줄여 버려서 브랜치와 구별되지
    # 않는다. 전체 refname 으로 걸러낸 뒤 접두어를 직접 뗀다.
    #
    # 필드는 NUL 로 나눈다. 작성자명에는 탭도 US(0x1F)도 들어갈 수 있어서, 그런
    # 구분자를 쓰면 이름 안의 문자와 구별되지 않아 이후 열이 밀린다. NUL 만은
    # Git 이 값에 담을 수 없다.
    #
    # 레코드 끝의 %00 뒤에 for-each-ref 가 줄바꿈을 붙이므로, 다음 레코드의 첫 필드
    # 앞에 그 줄바꿈이 남는다. 읽은 뒤에 떼어 낸다.
    git for-each-ref --sort=-committerdate refs/remotes/origin \
        --format='%(refname)%00%(committerdate:short)%00%(authorname)%00%(objectname:short)%00%(contents:subject)%00' |
    while IFS= read -r -d '' ref &&
          IFS= read -r -d '' date &&
          IFS= read -r -d '' author &&
          IFS= read -r -d '' sha &&
          IFS= read -r -d '' subject; do
        ref="${ref#$'\n'}"
        skip_ref "$ref" && continue

        name="${ref#"$PREFIX"}"

        if [ "origin/$name" = "$BASE" ]; then
            # 기준 브랜치 자신. 앞/뒤를 셀 대상이 아니다.
            ahead=0
            behind=0
            status=base
        else
            # --left-right --count 는 "뒤처진수<TAB>앞선수" 순으로 낸다.
            read -r behind ahead < <(git rev-list --left-right --count "$BASE...origin/$name")
            if [ "$name" = "main" ]; then
                # main 은 릴리스 브랜치라 dev 를 앞설 일이 없다. 다른 브랜치와 같은
                # 기준으로 "반영 완료"라고 적으면 병합 대기 중인 작업처럼 읽힌다.
                status=release
            elif [ "$ahead" -gt 0 ]; then
                status=wip
            elif [ "$behind" -eq 0 ]; then
                status=same
            else
                status=merged
            fi
        fi

        printf '%s\0%s\0%s\0%s\0%s\0%s\0%s\0%s\0' \
            "$name" "$date" "$author" "$sha" "$subject" "$ahead" "$behind" "$status"
    done
}

# RECORDS 를 한 줄씩 읽어 콜백에 넘긴다. 인자는 호출할 함수 이름.
each_record() {
    local fn="$1"
    while IFS= read -r -d '' name &&
          IFS= read -r -d '' date &&
          IFS= read -r -d '' author &&
          IFS= read -r -d '' sha &&
          IFS= read -r -d '' subject &&
          IFS= read -r -d '' ahead &&
          IFS= read -r -d '' behind &&
          IFS= read -r -d '' status; do
        "$fn" "$name" "$date" "$author" "$sha" "$subject" "$ahead" "$behind" "$status"
    done < "$RECORDS"
}

# 상태 코드를 마크다운 문구로.
status_md() {
    case "$1" in
        wip)     printf '작업 중' ;;
        merged)  printf '`dev`에 반영 완료' ;;
        same)    printf '`dev`와 동일' ;;
        release) printf '릴리스 브랜치' ;;
        base)    printf '기준 브랜치' ;;
    esac
}

# 상태 코드를 HTML 문구로. 마크다운의 백틱은 여기서 쓰지 않는다.
status_html() {
    case "$1" in
        wip)     printf '작업 중' ;;
        merged)  printf 'dev에 반영 완료' ;;
        same)    printf 'dev와 동일' ;;
        release) printf '릴리스 브랜치' ;;
        base)    printf '기준 브랜치' ;;
    esac
}

# --- 마크다운 렌더러 ----------------------------------------------------------

md_latest_row() {
    printf '| `%s` | %s | %s | %s | %s |\n' \
        "$(cell "$1")" "$2" "$(cell "$3")" "$4" "$(cell "$5")"
}

# 앞선 커밋이 많은 브랜치부터, 같으면 더 많이 뒤처진 쪽부터. 기준 브랜치는 뺀다.
#
# 정렬 키로 탭을 쓴다. 이 표는 브랜치명만 담고, git 은 refname 에 제어문자를 허용하지
# 않으므로 탭이 값 안에 들어올 수 없다.
md_divergence_key() {
    [ "$8" = base ] && return 0
    printf '%s\t%s\t%s\t%s\n' "$6" "$7" "$1" "$8"
}

md_divergence_rows() {
    each_record md_divergence_key |
    sort -k1,1nr -k2,2nr |
    while IFS=$'\t' read -r ahead behind name status; do
        printf '| `%s` | %s | %s | %s |\n' \
            "$(cell "$name")" "$ahead" "$behind" "$(status_md "$status")"
    done
}

write_markdown() {
    mkdir -p "$(dirname "$OUT_MD")"
    {
        printf '# 브랜치 현황 (%s 기준)\n\n' "$today"
        printf '%s\n\n' '이 문서는 매일 00시(KST)에 자동으로 다시 만들어진다. 손으로 고쳐도 다음 실행 때 사라지므로, 내용을 바꾸려면 `.github/scripts/branch-status.sh`를 고친다.'
        # 두 파일은 같은 디렉터리에 놓이므로 파일명만으로 건다. 출력 경로를 그대로
        # 적으면 로컬에서 임시 경로로 만들어 봤을 때 그 경로가 문서에 박힌다.
        printf '%s[%s](%s)%s\n\n' \
            '같은 내용을 그림으로 본 것이 ' \
            "$(basename "$OUT_HTML")" "$(basename "$OUT_HTML")" \
            ' 이다. 브라우저로 열면 된다.'

        printf '## 브랜치별 최신 커밋\n\n'
        printf '| 브랜치 | 최신 커밋일 | 작성자 | 커밋 | 요약 |\n'
        printf '| --- | --- | --- | --- | --- |\n'
        each_record md_latest_row

        printf '\n## `dev` 기준 앞/뒤 커밋 수\n\n'
        printf '| 브랜치 | 앞선 커밋 | 뒤처진 커밋 | 상태 |\n'
        printf '| --- | ---: | ---: | --- |\n'
        md_divergence_rows

        printf '\n- "앞선 커밋"이 0이면 그 브랜치의 모든 커밋이 이미 `dev`에 들어가 있다는 뜻이다.\n'
        printf '%s\n' '- `main`의 "뒤처진 커밋"은 아직 릴리스되지 않은 `dev`의 작업량이다.'
        printf '%s`%s`%s\n' \
            '- 위 수치는 기준 커밋 ' "$base_sha" ' 시점의 스냅샷이다. 실제 값은 이 문서가 머지되는 순간 이미 더 커져 있다. 이 문서 자신의 커밋과 merge commit 이 `dev`에 얹히고(1~2), 그 사이 다른 PR 이 먼저 머지됐다면 그 커밋 수만큼 더 더해진다. 정확한 값은 `git rev-list --left-right --count origin/dev...origin/<브랜치>`로 확인한다.'
    } > "$OUT_MD"
}

# --- HTML 렌더러 --------------------------------------------------------------

# 기준 브랜치 카드. 다른 카드와 크기와 위치가 달라 따로 그린다.
html_base_card() {
    [ "$8" != base ] && return 0
    printf '  <div class="spine-head">\n'
    printf '    <div class="spine-name">%s</div>\n' "$(html "$1")"
    printf '    <div class="spine-meta">%s · %s · <code>%s</code></div>\n' \
        "$2" "$(html "$3")" "$4"
    printf '    <div class="spine-subject">%s</div>\n' "$(html "$5")"
    printf '  </div>\n'
}

# 기준 브랜치를 뺀 나머지 카드. 수집 순서가 최신 커밋 순이라 그대로 그린다.
html_card() {
    [ "$8" = base ] && return 0
    printf '  <article class="card %s">\n' "$8"
    printf '    <h3>%s</h3>\n' "$(html "$1")"
    printf '    <dl>\n'
    printf '      <div><dt>최신 커밋일</dt><dd>%s</dd></div>\n' "$2"
    printf '      <div><dt>작성자</dt><dd>%s</dd></div>\n' "$(html "$3")"
    printf '      <div><dt>커밋</dt><dd><code>%s</code></dd></div>\n' "$4"
    printf '    </dl>\n'
    printf '    <p class="subject">%s</p>\n' "$(html "$5")"
    printf '    <p class="counts"><span class="ahead">앞선 %s</span><span class="behind">뒤처진 %s</span></p>\n' \
        "$6" "$7"
    printf '    <span class="badge %s">%s</span>\n' "$8" "$(status_html "$8")"
    printf '  </article>\n'
}

html_divergence_rows() {
    each_record md_divergence_key |
    sort -k1,1nr -k2,2nr |
    while IFS=$'\t' read -r ahead behind name status; do
        local ahead_cls=zero
        [ "$ahead" -gt 0 ] && ahead_cls=nonzero
        printf '      <tr>\n'
        printf '        <th scope="row">%s</th>\n' "$(html "$name")"
        printf '        <td class="num %s">%s</td>\n' "$ahead_cls" "$ahead"
        printf '        <td class="num">%s</td>\n' "$behind"
        printf '        <td><span class="badge %s">%s</span></td>\n' \
            "$status" "$(status_html "$status")"
        printf '      </tr>\n'
    done
}

write_html() {
    mkdir -p "$(dirname "$OUT_HTML")"
    {
        cat <<'HEAD'
<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>브랜치 현황 한눈에 보기</title>
<style>
:root {
  color-scheme: light dark;
  --bg: #f1f5f9;
  --panel: #ffffff;
  --line: #d8e0ea;
  --text: #16202e;
  --muted: #5b6b80;
  --blue: #2563eb;
  --green: #16a34a;
  --orange: #ea580c;
  --gray: #64748b;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #0e141c;
    --panel: #161f2b;
    --line: #2a3849;
    --text: #e6edf5;
    --muted: #94a6bb;
    --blue: #60a5fa;
    --green: #4ade80;
    --orange: #fb923c;
    --gray: #94a3b8;
  }
}
* { box-sizing: border-box; }
body {
  margin: 0;
  padding: 2rem 1.25rem 3rem;
  background: var(--bg);
  color: var(--text);
  font: 15px/1.6 "Malgun Gothic", "Apple SD Gothic Neo", system-ui, sans-serif;
}
.wrap { max-width: 1180px; margin: 0 auto; }
header { text-align: center; margin-bottom: 2rem; }
h1 { margin: 0 0 .35rem; font-size: clamp(1.6rem, 4vw, 2.4rem); letter-spacing: -.02em; }
.sub { margin: 0; color: var(--muted); }
section {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 1.35rem 1.4rem 1.5rem;
  margin-bottom: 1.25rem;
}
h2 {
  margin: 0 0 1.1rem;
  font-size: 1.05rem;
  color: var(--blue);
  display: flex;
  align-items: center;
  gap: .55rem;
}
h2::before {
  content: counter(sec);
  counter-increment: sec;
  display: grid;
  place-items: center;
  width: 1.6em;
  height: 1.6em;
  border-radius: 50%;
  background: var(--blue);
  color: #fff;
  font-size: .85em;
}
.wrap { counter-reset: sec; }

/* 기준 브랜치 */
.spine-head {
  border: 2px solid var(--blue);
  border-radius: 12px;
  padding: .9rem 1.1rem;
  margin-bottom: 1.25rem;
  background: color-mix(in srgb, var(--blue) 8%, transparent);
}
.spine-name { font-weight: 700; font-size: 1.15rem; color: var(--blue); }
.spine-meta { color: var(--muted); font-size: .87rem; margin-top: .15rem; }
.spine-subject { margin-top: .4rem; }

/* 브랜치 카드 */
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(265px, 1fr));
  gap: .9rem;
}
.card {
  border: 1px solid var(--line);
  border-left: 4px solid var(--gray);
  border-radius: 10px;
  padding: .85rem 1rem 1rem;
  display: flex;
  flex-direction: column;
  gap: .5rem;
}
.card.wip { border-left-color: var(--orange); }
.card.merged, .card.same { border-left-color: var(--green); }
.card.release { border-left-color: var(--gray); }
.card h3 { margin: 0; font-size: .97rem; word-break: break-all; }
.card dl { margin: 0; display: grid; gap: .1rem; font-size: .85rem; }
.card dl div { display: flex; gap: .5rem; }
.card dt { color: var(--muted); min-width: 5.2em; }
.card dd { margin: 0; }
.subject { margin: 0; font-size: .88rem; color: var(--muted); }
.counts { margin: 0; display: flex; gap: .5rem; font-size: .82rem; }
.counts span { padding: .1rem .5rem; border-radius: 5px; background: color-mix(in srgb, var(--gray) 15%, transparent); }
code { font-family: ui-monospace, Consolas, monospace; font-size: .92em; }

/* 상태 배지 */
.badge {
  align-self: flex-start;
  padding: .2rem .6rem;
  border-radius: 999px;
  font-size: .8rem;
  font-weight: 600;
  white-space: nowrap;
}
.badge.wip { background: color-mix(in srgb, var(--orange) 18%, transparent); color: var(--orange); }
.badge.merged, .badge.same { background: color-mix(in srgb, var(--green) 18%, transparent); color: var(--green); }
.badge.release { background: color-mix(in srgb, var(--gray) 18%, transparent); color: var(--gray); }

/* 표 */
.scroll { overflow-x: auto; }
table { border-collapse: collapse; width: 100%; min-width: 520px; }
th, td { padding: .55rem .7rem; border-bottom: 1px solid var(--line); text-align: left; }
thead th { background: color-mix(in srgb, var(--blue) 10%, transparent); font-size: .87rem; }
tbody th { font-weight: 500; word-break: break-all; }
.num { text-align: right; font-variant-numeric: tabular-nums; font-weight: 600; }
.num.zero { color: var(--green); }
.num.nonzero { color: var(--orange); }

/* 해석 */
ol.notes { margin: 0; padding-left: 1.3rem; display: grid; gap: .5rem; }
footer { color: var(--muted); font-size: .84rem; text-align: center; margin-top: 1.5rem; }
</style>
</head>
<body>
<div class="wrap">
HEAD

        printf '<header>\n'
        printf '  <h1>브랜치 현황 한눈에 보기</h1>\n'
        printf '  <p class="sub">%s 기준 · 최신 커밋 및 병합 상태 요약</p>\n' "$today"
        printf '</header>\n'

        printf '<section>\n  <h2>브랜치별 최신 커밋</h2>\n'
        each_record html_base_card
        printf '  <div class="cards">\n'
        each_record html_card
        printf '  </div>\n</section>\n'

        printf '<section>\n  <h2>dev 기준 앞/뒤 커밋 수</h2>\n'
        printf '  <div class="scroll">\n    <table>\n'
        printf '      <thead><tr><th>브랜치</th><th class="num">앞선 커밋<br>(dev에 없는 커밋)</th><th class="num">뒤처진 커밋<br>(dev에 있는 커밋)</th><th>상태</th></tr></thead>\n'
        printf '      <tbody>\n'
        html_divergence_rows
        printf '      </tbody>\n    </table>\n  </div>\n</section>\n'

        cat <<'NOTES'
<section>
  <h2>해석 포인트</h2>
  <ol class="notes">
    <li>"앞선 커밋" = 해당 브랜치에는 있지만 dev에는 아직 없는 커밋 수.</li>
    <li>"뒤처진 커밋" = dev에는 있지만 해당 브랜치에는 없는 커밋 수.</li>
    <li>"앞선 커밋"이 0이면 그 브랜치의 모든 커밋이 이미 dev에 들어갔다는 뜻이다.</li>
    <li>main은 릴리스 브랜치라 앞선 커밋이 0인 것이 정상이다. main의 "뒤처진 커밋"은 아직 릴리스되지 않은 dev의 작업량이다.</li>
  </ol>
</section>
NOTES

        printf '<footer>\n'
        printf '  이 페이지는 <code>.github/scripts/branch-status.sh</code> 가 매일 00시(KST)에 다시 만든다. 손으로 고쳐도 다음 실행 때 사라진다.<br>\n'
        printf '  위 수치는 기준 커밋 <code>%s</code> 시점의 스냅샷이다. 정확한 값은 <code>git rev-list --left-right --count origin/dev...origin/&lt;브랜치&gt;</code> 로 확인한다.\n' "$base_sha"
        printf '</footer>\n</div>\n</body>\n</html>\n'
    } > "$OUT_HTML"
}

# --- 실행 --------------------------------------------------------------------

collect > "$RECORDS"
write_markdown
write_html

printf 'wrote: %s\n' "$OUT_MD"
printf 'wrote: %s\n' "$OUT_HTML"
