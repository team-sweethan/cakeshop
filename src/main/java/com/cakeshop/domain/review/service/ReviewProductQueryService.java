package com.cakeshop.domain.review.service;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.cakeshop.domain.review.dto.view.ProductReviewView;

@Service
@RequiredArgsConstructor
public class ReviewProductQueryService {

    private final ReviewService reviewService;

    public List<ProductReviewView> getPreview(long productId) {
        return reviewService.getProductReviewPreview(productId);
    }
}
