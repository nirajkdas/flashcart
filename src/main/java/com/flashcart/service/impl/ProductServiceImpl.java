package com.flashcart.service.impl;

import com.flashcart.dto.request.CreateProductRequest;
import com.flashcart.dto.request.CreateReviewRequest;
import com.flashcart.dto.request.UpdateProductRequest;
import com.flashcart.dto.response.PageResponse;
import com.flashcart.dto.response.ProductResponse;
import com.flashcart.dto.response.ReviewResponse;
import com.flashcart.entity.*;
import com.flashcart.exception.*;
import com.flashcart.repository.*;
import com.flashcart.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository      productRepo;
    private final UserRepository         userRepo;
    private final CategoryRepository     categoryRepo;
    private final ProductReviewRepository reviewRepo;

    // ── Slug helper ────────────────────────────────────────────────────────────
    private static final Pattern NON_LATIN   = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE  = Pattern.compile("[\\s]+");

    private String toSlug(String input) {
        String slug = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        slug = WHITESPACE.matcher(slug).replaceAll("-");
        slug = NON_LATIN.matcher(slug).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH);
        // ensure uniqueness
        String base = slug;
        int i = 1;
        while (productRepo.existsBySlug(slug)) slug = base + "-" + i++;
        return slug;
    }

    // ── Create ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse create(CreateProductRequest req, String sellerUsername) {
        User seller = userRepo.findByUsername(sellerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = Product.builder()
                .seller(seller)
                .name(req.getName())
                .slug(toSlug(req.getName()))
                .description(req.getDescription())
                .basePrice(req.getBasePrice())
                .stockQuantity(req.getStockQuantity())
                .imageUrl(req.getImageUrl())
                .build();

        if (req.getCategoryId() != null) {
            Category cat = categoryRepo.findById(req.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));
            product.setCategory(cat);
        }

        return toResponse(productRepo.save(product));
    }

    // ── Update ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse update(Long id, UpdateProductRequest req, String sellerUsername) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        User caller = userRepo.findByUsername(sellerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isAdmin = caller.getRole() == User.Role.ADMIN;
        if (!isAdmin && !product.getSeller().getId().equals(caller.getId()))
            throw new BadRequestException("You do not own this product");

        if (req.getName()          != null) product.setName(req.getName());
        if (req.getDescription()   != null) product.setDescription(req.getDescription());
        if (req.getBasePrice()     != null) product.setBasePrice(req.getBasePrice());
        if (req.getStockQuantity() != null) product.setStockQuantity(req.getStockQuantity());
        if (req.getImageUrl()      != null) product.setImageUrl(req.getImageUrl());
        if (req.getIsActive()      != null) product.setIsActive(req.getIsActive());
        if (req.getCategoryId()    != null) {
            Category cat = categoryRepo.findById(req.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));
            product.setCategory(cat);
        }

        return toResponse(productRepo.save(product));
    }

    // ── Reads (cached) ─────────────────────────────────────────────────────────
    @Override
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return toResponse(productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id)));
    }

    @Override
    @Cacheable(value = "products", key = "'slug-' + #slug")
    @Transactional(readOnly = true)
    public ProductResponse getBySlug(String slug) {
        return toResponse(productRepo.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug)));
    }

    @Override
    @Cacheable(value = "products", key = "'all-' + #pageable.pageNumber")
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAll(Pageable pageable) {
        return PageResponse.of(productRepo.findAll(pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String query, Pageable pageable) {
        return PageResponse.of(productRepo.search(query, pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getByCategory(Long categoryId, Pageable pageable) {
        return PageResponse.of(
                productRepo.findByCategoryIdAndIsActiveTrue(categoryId, pageable).map(this::toResponse));
    }

    // ── Delete ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void delete(Long id, String username) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        User caller = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean isAdmin = caller.getRole() == User.Role.ADMIN;
        if (!isAdmin && !product.getSeller().getId().equals(caller.getId()))
            throw new BadRequestException("You do not own this product");
        product.setIsActive(false);   // soft delete
        productRepo.save(product);
    }

    // ── Reviews ────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public ReviewResponse addReview(Long productId, CreateReviewRequest req, String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (reviewRepo.existsByUserIdAndProductId(user.getId(), productId))
            throw new ConflictException("You have already reviewed this product");

        ProductReview review = ProductReview.builder()
                .user(user).product(product)
                .rating(req.getRating()).comment(req.getComment())
                .build();

        return toReviewResponse(reviewRepo.save(review));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviews(Long productId, Pageable pageable) {
        return PageResponse.of(reviewRepo.findByProductId(productId, pageable).map(this::toReviewResponse));
    }

    // ── Mappers ────────────────────────────────────────────────────────────────
    private ProductResponse toResponse(Product p) {
        Double avg = reviewRepo.findAverageRatingByProductId(p.getId());
        return ProductResponse.builder()
                .id(p.getId()).name(p.getName()).slug(p.getSlug())
                .description(p.getDescription()).basePrice(p.getBasePrice())
                .stockQuantity(p.getStockQuantity())
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .sellerName(p.getSeller().getFullName())
                .imageUrl(p.getImageUrl()).isActive(p.getIsActive())
                .averageRating(avg).createdAt(p.getCreatedAt())
                .build();
    }

    private ReviewResponse toReviewResponse(ProductReview r) {
        return ReviewResponse.builder()
                .id(r.getId()).username(r.getUser().getUsername())
                .rating(r.getRating()).comment(r.getComment())
                .createdAt(r.getCreatedAt()).build();
    }
}
