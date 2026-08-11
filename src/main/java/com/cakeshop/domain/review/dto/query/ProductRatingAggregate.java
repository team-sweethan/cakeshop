package com.cakeshop.domain.review.dto.query;

import java.math.BigDecimal;

public record ProductRatingAggregate(BigDecimal averageRating, long reviewCount) {
}
