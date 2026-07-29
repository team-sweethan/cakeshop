#!/usr/bin/env bash
#
# 원격 브랜치 현황을 마크다운 문서로 만들어 낸다.
#
# Git 메타데이터만 읽으므로 DB도 빌드도 필요 없다. 실행 전에 origin의 모든 브랜치를
# 받아 둬야 한다(워크플로에서는 fetch-depth: 0 으로 체크아웃한다).
#
#   ./.github/scripts/branch-status.sh [출력경로]
#
set -euo pipefail

OUT="${1:-docs/branch_update.md}"
BASE="origin/dev"
PREFIX="refs/remotes/origin/"
HEAD_REF="refs/remotes/origin/HEAD"

today="$(TZ=Asia/Seoul date +%Y-%m-%d)"

# 최신 커밋 표: 커밋이 최근인 브랜치부터.
latest_rows() {
    # refname:short 는 origin/HEAD 를 그냥 "origin" 으로 줄여 버려서 브랜치와 구별되지
    # 않는다. 전체 refname 으로 걸러낸 뒤 접두어를 직접 뗀다.
    git for-each-ref --sort=-committerdate refs/remotes/origin \
        --format='%(refname)%09%(committerdate:short)%09%(authorname)%09%(objectname:short)%09%(contents:subject)' |
    while IFS=$'\t' read -r ref date author sha subject; do
        [ "$ref" = "$HEAD_REF" ] && continue
        printf '| `%s` | %s | %s | %s | %s |\n' \
            "${ref#"$PREFIX"}" "$date" "$author" "$sha" "${subject//|/\\|}"
    done
}

# 앞선 커밋이 많은 브랜치부터, 같으면 더 많이 뒤처진 쪽부터.
divergence_rows() {
    git for-each-ref --format='%(refname)' refs/remotes/origin |
    while read -r full; do
        [ "$full" = "$HEAD_REF" ] && continue
        ref="origin/${full#"$PREFIX"}"
        [ "$ref" = "$BASE" ] && continue
        # --left-right --count 는 "뒤처진수<TAB>앞선수" 순으로 낸다.
        read -r behind ahead < <(git rev-list --left-right --count "$BASE...$ref")
        if [ "$ahead" -gt 0 ]; then
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
        printf '| `%s` | %s | %s | %s |\n' "$name" "$ahead" "$behind" "$status"
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
    printf '%s\n' '- `main`은 릴리스 브랜치라 앞선 커밋이 0인 것이 정상이다.'
} > "$OUT"

printf 'wrote: %s\n' "$OUT"
