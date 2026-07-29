#!/usr/bin/env bash
#
# 원격 브랜치 현황을 마크다운 문서로 만들어 낸다.
#
# Git 메타데이터만 읽으므로 DB도 빌드도 필요 없다. 실행 전에 origin의 모든 브랜치를
# 받아 둬야 한다(워크플로에서는 fetch-depth: 0 으로 체크아웃한다).
#
#   ./.github/scripts/branch-status.sh [출력경로]
#
# EXCLUDE_BRANCH 환경 변수로 브랜치 하나를 보고서에서 뺄 수 있다. 워크플로가 결과를
# 담아 두는 작업 브랜치를 스스로 보고하지 않게 하는 용도다.
#
set -euo pipefail

OUT="${1:-docs/branch_update.md}"
BASE="origin/dev"
PREFIX="refs/remotes/origin/"
HEAD_REF="refs/remotes/origin/HEAD"
EXCLUDE="${EXCLUDE_BRANCH:-}"

today="$(TZ=Asia/Seoul date +%Y-%m-%d)"
# 표의 모든 수치는 이 커밋을 기준으로 센 값이다. 문서를 게시하면 dev 가 앞으로
# 나아가므로, 어느 시점의 스냅샷인지 문서에 남긴다.
base_sha="$(git rev-parse --short "$BASE")"

# 표의 셀에 들어가는 값. 브랜치명과 사람 이름에는 '|' 가 들어올 수 있고, 그대로 두면
# 열 구분자로 읽혀 행이 깨진다.
#
# 백슬래시를 먼저 겹쳐 두지 않으면 원래 '\|' 이던 자리가 '\\|' 이 되고, GFM 은 앞
# 백슬래시가 뒤 백슬래시를 이스케이프한 것으로 읽어 파이프가 다시 구분자가 된다.
#
# bash 의 ${v//.../...} 는 패턴과 치환문 양쪽에서 백슬래시를 삼켜 버려 이 두 단계를
# 제대로 표현하지 못한다. sed 로 처리한다.
cell() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/|/\\|/g'
}

# 보고 대상이 아닌 ref 인가. 인자는 전체 refname.
skip_ref() {
    [ "$1" = "$HEAD_REF" ] && return 0
    [ -n "$EXCLUDE" ] && [ "${1#"$PREFIX"}" = "$EXCLUDE" ] && return 0
    return 1
}

# 최신 커밋 표: 커밋이 최근인 브랜치부터.
latest_rows() {
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
        printf '| `%s` | %s | %s | %s | %s |\n' \
            "$(cell "${ref#"$PREFIX"}")" "$date" "$(cell "$author")" "$sha" "$(cell "$subject")"
    done
}

# 앞선 커밋이 많은 브랜치부터, 같으면 더 많이 뒤처진 쪽부터.
divergence_rows() {
    git for-each-ref --format='%(refname)' refs/remotes/origin |
    while read -r full; do
        skip_ref "$full" && continue
        ref="origin/${full#"$PREFIX"}"
        [ "$ref" = "$BASE" ] && continue
        # --left-right --count 는 "뒤처진수<TAB>앞선수" 순으로 낸다.
        read -r behind ahead < <(git rev-list --left-right --count "$BASE...$ref")
        if [ "$ref" = "origin/main" ]; then
            # main 은 릴리스 브랜치라 dev 를 앞설 일이 없다. 다른 브랜치와 같은
            # 기준으로 "반영 완료"라고 적으면 병합 대기 중인 작업처럼 읽힌다.
            status='릴리스 브랜치'
        elif [ "$ahead" -gt 0 ]; then
            status='작업 중'
        elif [ "$behind" -eq 0 ]; then
            status='`dev`와 동일'
        else
            status='`dev`에 반영 완료'
        fi
        printf '%s\t%s\t%s\t%s\n' "$ahead" "$behind" "${ref#origin/}" "$status"
    done |
    sort -k1,1nr -k2,2nr |
    while IFS=$'\t' read -r ahead behind name status; do
        printf '| `%s` | %s | %s | %s |\n' "$(cell "$name")" "$ahead" "$behind" "$status"
    done
}

mkdir -p "$(dirname "$OUT")"

{
    printf '# 브랜치 현황 (%s 기준)\n\n' "$today"
    printf '%s\n\n' '이 문서는 매일 00시(KST)에 자동으로 다시 만들어진다. 손으로 고쳐도 다음 실행 때 사라지므로, 내용을 바꾸려면 `.github/scripts/branch-status.sh`를 고친다.'

    printf '## 브랜치별 최신 커밋\n\n'
    printf '| 브랜치 | 최신 커밋일 | 작성자 | 커밋 | 요약 |\n'
    printf '| --- | --- | --- | --- | --- |\n'
    latest_rows

    printf '\n## `dev` 기준 앞/뒤 커밋 수\n\n'
    printf '| 브랜치 | 앞선 커밋 | 뒤처진 커밋 | 상태 |\n'
    printf '| --- | ---: | ---: | --- |\n'
    divergence_rows

    printf '\n- "앞선 커밋"이 0이면 그 브랜치의 모든 커밋이 이미 `dev`에 들어가 있다는 뜻이다.\n'
    printf '%s\n' '- `main`의 "뒤처진 커밋"은 아직 릴리스되지 않은 `dev`의 작업량이다.'
    printf '%s`%s`%s\n' \
        '- 위 수치는 기준 커밋 ' "$base_sha" ' 시점의 스냅샷이다. 실제 값은 이 문서가 머지되는 순간 이미 더 커져 있다. 이 문서 자신의 커밋과 merge commit 이 `dev`에 얹히고(1~2), 그 사이 다른 PR 이 먼저 머지됐다면 그 커밋 수만큼 더 더해진다. 정확한 값은 `git rev-list --left-right --count origin/dev...origin/<브랜치>`로 확인한다.'
} > "$OUT"

printf 'wrote: %s\n' "$OUT"
