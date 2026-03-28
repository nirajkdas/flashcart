package com.flashcart.controller;

import com.flashcart.dto.request.CreateCategoryRequest;
import com.flashcart.dto.response.ApiResponse;
import com.flashcart.dto.response.CategoryResponse;
import com.flashcart.entity.Category;
import com.flashcart.exception.ConflictException;
import com.flashcart.exception.ResourceNotFoundException;
import com.flashcart.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management")
public class CategoryController {

    private final CategoryRepository categoryRepo;

    @GetMapping
    @Cacheable("categories")
    @Operation(summary = "List all categories")
    public ApiResponse<List<CategoryResponse>> getAll() {
        List<CategoryResponse> cats = categoryRepo.findAll().stream()
                .map(c -> CategoryResponse.builder()
                        .id(c.getId()).name(c.getName())
                        .slug(c.getSlug()).description(c.getDescription()).build())
                .toList();
        return ApiResponse.ok(cats);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = "categories", allEntries = true)
    @Operation(summary = "Create a category (Admin only)")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest req) {
        if (categoryRepo.existsByName(req.getName()))
            throw new ConflictException("Category already exists: " + req.getName());

        Category cat = Category.builder()
                .name(req.getName()).slug(req.getSlug()).description(req.getDescription()).build();
        cat = categoryRepo.save(cat);

        return ApiResponse.ok("Category created",
                CategoryResponse.builder().id(cat.getId()).name(cat.getName())
                        .slug(cat.getSlug()).description(cat.getDescription()).build());
    }
}
