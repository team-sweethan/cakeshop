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

# 표의 셀에 들어가는 값. 브랜치명과 사람 이름에는 '|' 가 들어올 수 있고, 그대로 두면
# 열 구분자로 읽혀 행이 깨진다.
#
# 치환문의 '\\|' 는 백슬래시를 남기지 않으므로(bash 가 replacement 의 백슬래시를 먼저
# 소비한다) 백슬래시를 변수에 담아 넣는다.
BACKSLASH='\'
cell() {
    local value="$1"
    printf '%s' "${value//|/${BACKSLASH}|}"
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
    git for-each-ref --sort=-committerdate refs/remotes/origin \
        --format='%(refname)%09%(committerdate:short)%09%(authorname)%09%(objectname:short)%09%(contents:subject)' |
    while IFS=$'\t' read -r ref date author sha subject; do
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
} > "$OUT"

printf 'wrote: %s\n' "$OUT"
