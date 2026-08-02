package com.cakeshop.domain.product.dto.form;

/** 관리자 옵션 그룹과 개별 옵션의 표시 순서 이동 방향. */
public enum ProductOptionMoveDirection {

    UP(-1),
    DOWN(1);

    private final int offset;

    ProductOptionMoveDirection(int offset) {
        this.offset = offset;
    }

    public int targetIndex(int currentIndex) {
        return currentIndex + offset;
    }
}
