package com.cakeshop.domain.review.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cakeshop.domain.review.dto.view.ProductReviewView;

@Service
public class ReviewProductQueryService {

    private final ReviewService reviewService;

    public ReviewProductQueryService(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    public List<ProductReviewView> getPreview(long productId) {
        return reviewService.getProductReviewPreview(productId);
    }
}
