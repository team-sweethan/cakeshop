package com.cakeshop.global.common.paging;

public class PageRequest {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final int page;   // 1부터 시작
    private final int size;

    public PageRequest(Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        this.size = s;
        // getOffset()의 (page - 1) * size 가 int 를 넘으면 음수 OFFSET 이 되어 목록이 500 으로
        // 죽는다. page 는 공개 화면의 요청 파라미터라 누구나 큰 값을 넣을 수 있다.
        // 상한을 넘는 페이지는 어차피 데이터가 없으므로 빈 목록으로 응답한다.
        this.page = Math.min(p, Integer.MAX_VALUE / s);
    }

    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getOffset() { return (page - 1) * size; }
}
