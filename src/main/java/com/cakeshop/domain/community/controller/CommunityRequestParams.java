package com.cakeshop.domain.community.controller;

/*
 * 주소로 들어온 숫자 파라미터를 읽는다.
 *
 * <p>쪽 번호·분류 번호·댓글 상한은 모두 사용자가 주소창에 무엇이든 적을 수 있는 값이라, 잘못된
 * 값은 <b>거절이 아니라 "지정하지 않음"</b>으로 다룬다. 없는 쪽을 요청했다고 오류 화면을 띄우면
 * 링크를 잘못 복사한 사용자가 갈 곳이 없다.
 *
 * <p>Controller 마다 같은 파싱을 두면 한쪽만 고쳐지고, 그때 두 화면이 다른 값을 서로 다르게
 * 해석한다 — 화면은 둘 다 멀쩡해 보인다.
 */
final class CommunityRequestParams {

    private CommunityRequestParams() {
    }

    /** 양수가 아니거나 숫자가 아니면 {@code null}. {@code Integer} 범위를 넘어도 {@code null}이다. */
    static Integer positiveInteger(String value) {
        Long parsed = positiveLong(value);

        if (parsed == null || parsed > Integer.MAX_VALUE) {
            return null;
        }

        return parsed.intValue();
    }

    /** 양수가 아니거나 숫자가 아니면 {@code null}. */
    static Long positiveLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
