package com.cakeshop.domain.review.dto.view;

import java.math.BigDecimal;

import lombok.Value;

@Value
public class ProductRatingAggregate {

    BigDecimal averageRating;
    long reviewCount;
}
