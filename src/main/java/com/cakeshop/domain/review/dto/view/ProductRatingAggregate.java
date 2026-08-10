package com.cakeshop.domain.review.dto.view;

import java.math.BigDecimal;

public record ProductRatingAggregate(
        BigDecimal averageRating,
        long reviewCount
) {
}
