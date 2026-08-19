#!/usr/bin/env bash
#
# 테스트를 돌릴 필요가 없는 변경인지 판정해 GITHUB_OUTPUT 에 skip=true|false 를 쓴다.
#
# 판정은 '생략해도 되는 경로'를 열거하는 방향이다. 반대로 짜면 앞으로 생기는 새로운 종류의
# 파일이 조용히 생략 대상이 된다. 목록을 확정하지 못하는 상황도 전부 '실행'으로 떨어뜨린다.
#
# 변경 목록을 git 이 아니라 compare API 로 받는 이유는 이름 변경 때문이다. git diff --name-only
# 는 rename 을 감지하면 바뀐 뒤 경로만 보여줘서, 코드 파일을 docs/ 로 옮기면 원본 경로가
# 목록에서 사라진다. API 는 previous_filename 을 따로 주므로 그 구멍이 없다.
set -euo pipefail

case "${GITHUB_EVENT_NAME:-}" in
    pull_request) range="${BASE_SHA}...${HEAD_SHA}" ;;
    push) range="${BEFORE_SHA}...${GITHUB_SHA}" ;;
    *) range="" ;;
esac

run_tests() {
    echo "$1"
    echo "skip=false" >> "$GITHUB_OUTPUT"
    exit 0
}

if [ -z "$range" ]; then
    run_tests "비교 대상이 없는 이벤트(${GITHUB_EVENT_NAME:-?})라 테스트를 실행한다."
fi

if ! comparison=$(gh api "repos/${GITHUB_REPOSITORY}/compare/${range}" 2>/dev/null); then
    run_tests "변경 목록을 받지 못해 테스트를 실행한다. (${range})"
fi

count=$(printf '%s' "$comparison" | jq '.files | length')

# compare API 는 300개까지만 준다. 잘린 목록으로는 나머지가 문서인지 알 수 없다.
if [ "$count" -eq 0 ] || [ "$count" -ge 300 ]; then
    run_tests "변경 파일이 ${count}개라 목록을 신뢰할 수 없어 테스트를 실행한다."
fi

files=$(printf '%s' "$comparison" | jq -r '.files[] | .filename, (.previous_filename // empty)')

echo "변경된 파일:"
sed 's/^/  /' <<< "$files"

# 확장자만 보지 않고 위치까지 고정한다. src/main/resources 아래의 .md 는 jar 에 실려
# 정적 자원으로 응답되므로 문서가 아니다.
#
# grep 에 파이프를 쓰지 않는 이유는 -q 가 첫 매치에서 즉시 끝나기 때문이다. 목록이 파이프
# 버퍼(64KB)보다 크면 앞을 쓰던 쪽이 SIGPIPE 로 죽고, pipefail 이 그 파이프라인을 실패로
# 보는 바람에 '비문서 파일을 찾았다' 가 '못 찾았다' 로 뒤집힌다. 그 방향이 하필 생략이다.
skippable='^docs/|^[^/]+\.md$|^\.github/[^/]+\.md$|^src/main/java/.+/CLAUDE\.md$|^\.gitignore$'

if grep -qvE "$skippable" <<< "$files"; then
    run_tests "테스트에 영향을 주는 변경이 있어 실행한다."
fi

echo "문서 전용 변경이라 테스트를 생략한다."
echo "skip=true" >> "$GITHUB_OUTPUT"
