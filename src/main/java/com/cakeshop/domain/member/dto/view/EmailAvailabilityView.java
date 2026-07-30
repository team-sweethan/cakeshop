package com.cakeshop.domain.member.dto.view;

public record EmailAvailabilityView(
        boolean valid,
        boolean available,
        String message
) {
}
