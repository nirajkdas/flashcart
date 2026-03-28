package com.flashcart.controller;

import com.flashcart.dto.request.CreateFlashSaleRequest;
import com.flashcart.dto.request.FlashSalePurchaseRequest;
import com.flashcart.dto.response.*;
import com.flashcart.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Flash Sales", description = "Real-time flash sale engine")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    // ── Public ────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all flash sales")
    public ApiResponse<PageResponse<FlashSaleResponse>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(flashSaleService.getAll(PageRequest.of(page, size)));
    }

    @GetMapping("/active")
    @Operation(summary = "Get all currently active flash sales (cached 30s)")
    public ApiResponse<List<FlashSaleResponse>> getActive() {
        return ApiResponse.ok(flashSaleService.getActiveSales());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get flash sale details including live stock counts")
    public ApiResponse<FlashSaleResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(flashSaleService.getById(id));
    }

    // ── Customer purchase ──────────────────────────────────────────────────────

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(
        summary = "Purchase a flash sale item",
        description = """
            **Core feature**: atomic purchase using:
            1. Redis DECR (prevents overselling at scale)
            2. JPA @Version optimistic locking (DB-level safety net)
            3. Per-user rate limiting (5 attempts/min)
            4. Per-user quantity limit
            5. Real-time WebSocket inventory broadcast after purchase
            """,
        security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<OrderResponse> purchase(
            @Valid @RequestBody FlashSalePurchaseRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.ok("Purchase successful",
                flashSaleService.purchase(req, userDetails.getUsername()));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new flash sale", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<FlashSaleResponse> create(
            @Valid @RequestBody CreateFlashSaleRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.ok("Flash sale created",
                flashSaleService.create(req, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancel a flash sale", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        flashSaleService.cancelSale(id, userDetails.getUsername());
        return ApiResponse.ok("Sale cancelled", null);
    }
}
