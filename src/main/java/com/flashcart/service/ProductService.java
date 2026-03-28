package com.flashcart.service;

import com.flashcart.dto.request.CreateProductRequest;
import com.flashcart.dto.request.CreateReviewRequest;
import com.flashcart.dto.request.UpdateProductRequest;
import com.flashcart.dto.response.PageResponse;
import com.flashcart.dto.response.ProductResponse;
import com.flashcart.dto.response.ReviewResponse;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponse create(CreateProductRequest req, String sellerUsername);
    ProductResponse update(Long id, UpdateProductRequest req, String sellerUsername);
    ProductResponse getById(Long id);
    ProductResponse getBySlug(String slug);
    PageResponse<ProductResponse> getAll(Pageable pageable);
    PageResponse<ProductResponse> search(String query, Pageable pageable);
    PageResponse<ProductResponse> getByCategory(Long categoryId, Pageable pageable);
    void delete(Long id, String username);

    ReviewResponse addReview(Long productId, CreateReviewRequest req, String username);
    PageResponse<ReviewResponse> getReviews(Long productId, Pageable pageable);
}
