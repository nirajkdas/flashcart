package com.flashcart.controller;

import com.flashcart.dto.request.CreateProductRequest;
import com.flashcart.dto.request.CreateReviewRequest;
import com.flashcart.dto.request.UpdateProductRequest;
import com.flashcart.dto.response.*;
import com.flashcart.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    // ── Public endpoints ──────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all products (paginated)")
    public ApiResponse<PageResponse<ProductResponse>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending()
                                                       : Sort.by(sortBy).descending();
        return ApiResponse.ok(productService.getAll(PageRequest.of(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get product by slug")
    public ApiResponse<ProductResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.ok(productService.getBySlug(slug));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by name or description")
    public ApiResponse<PageResponse<ProductResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productService.search(q, PageRequest.of(page, size)));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get products by category")
    public ApiResponse<PageResponse<ProductResponse>> byCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productService.getByCategory(categoryId, PageRequest.of(page, size)));
    }

    @GetMapping("/{productId}/reviews")
    @Operation(summary = "Get reviews for a product")
    public ApiResponse<PageResponse<ReviewResponse>> getReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(productService.getReviews(productId, PageRequest.of(page, size)));
    }

    // ── Seller / Admin endpoints ───────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Create a new product", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.ok("Product created", productService.create(req, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Update a product", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.ok(productService.update(id, req, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Soft-delete a product", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        productService.delete(id, userDetails.getUsername());
        return ApiResponse.ok("Product deactivated", null);
    }

    // ── Customer review ───────────────────────────────────────────────────────

    @PostMapping("/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Leave a review on a product", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<ReviewResponse> addReview(
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.ok("Review submitted",
                productService.addReview(productId, req, userDetails.getUsername()));
    }
}
