package com.cakeshop.global.common.paging;

/**
 * 목록 화면의 페이지 번호 블록과 이전·다음 블록 이동 정보를 표현한다.
 * 데이터 조회 범위는 {@link PageRequest}, 화면에 표시할 번호 범위는 이 객체가 담당한다.
 */
public record PageNavigation(
        int startPage,
        int endPage,
        int previousPage,
        int nextPage,
        boolean hasPreviousBlock,
        boolean hasNextBlock
) {

    /** 별도 설정이 없는 화면에서 한 번에 표시할 페이지 번호 개수다. */
    public static final int DEFAULT_BLOCK_SIZE = 10;

    public static PageNavigation of(
            int currentPage,
            int totalPages
    ) {
        // 공통 기본값을 사용하는 간단한 생성 경로다.
        return of(
                currentPage,
                totalPages,
                DEFAULT_BLOCK_SIZE
        );
    }

    public static PageNavigation of(
            int currentPage,
            int totalPages,
            int blockSize
    ) {
        if (blockSize < 1) {
            throw new IllegalArgumentException(
                    "페이지 블록 크기는 1 이상이어야 합니다."
            );
        }

        if (totalPages < 1) {
            return new PageNavigation(
                    0, 0, 1, 1, false, false
            );
        }

        // 주소에 범위를 벗어난 page 값이 와도 실제 전체 페이지 범위 안으로 보정한다.
        int current =
                Math.min(Math.max(currentPage, 1), totalPages);

        int startPage =
                ((current - 1) / blockSize)
                        * blockSize + 1;

        int endPage =
                Math.min(
                        startPage + blockSize - 1,
                        totalPages
                );

        // 이전/다음은 한 페이지가 아니라 인접한 페이지 번호 블록의 시작점으로 이동한다.
        int previousPage =
                Math.max(startPage - 1, 1);

        int nextPage =
                Math.min(endPage + 1, totalPages);

        return new PageNavigation(
                startPage,
                endPage,
                previousPage,
                nextPage,
                startPage > 1,
                endPage < totalPages
        );
    }
}
