package com.cakeshop.domain.community.controller;

// 주소창에 실려 온 문자열 파라미터를 프로그램이 쓸 타입으로 바꾸는 도구 모음
// 예시 요청: GET /community?page=3&categoryId=2&keyword=케이크
// 브라우저가 보낸 값은 전부 String 이라 Long·Integer 로 바꾸는 자리가 필요하다
// 값이 이상하면 예외를 던지지 않고 null 을 준다 = "지정하지 않음"
final class CommunityRequestParams {

    // private 생성자: new CommunityRequestParams() 를 막아 static 메서드로만 쓰게 한다
    private CommunityRequestParams() {
    }

    // "37" -> 37L / null·""·"   "·"abc"·"0"·"-1" -> null
    // Long.parseLong 은 숫자가 아니면 NumberFormatException 을 던지므로 try 로 감싼다
    // catch 의 ignored 는 "예외 객체를 안 쓴다"는 표시일 뿐 문법상 이름은 아무거나 가능하다
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

    // positiveLong 으로 먼저 거른 뒤 int 범위까지 확인한다
    // "3" -> 3 / "9999999999"(Integer.MAX_VALUE 초과) -> null
    // 반환이 Integer(래퍼 타입)인 이유: int 는 null 을 담을 수 없다
    static Integer positiveInteger(String value) {
        Long parsed = positiveLong(value);

        if (parsed == null || parsed > Integer.MAX_VALUE) {
            return null;
        }

        return parsed.intValue();
    }

    // "  케이크 " -> "케이크" / null·""·"   " -> null (검색 조건을 걸지 않는다는 뜻)
    static String keyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
