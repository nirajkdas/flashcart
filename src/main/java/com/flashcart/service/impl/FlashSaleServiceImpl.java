package com.flashcart.service.impl;

import com.flashcart.dto.request.CreateFlashSaleRequest;
import com.flashcart.dto.request.FlashSaleItemRequest;
import com.flashcart.dto.request.FlashSalePurchaseRequest;
import com.flashcart.dto.response.*;
import com.flashcart.entity.*;
import com.flashcart.exception.*;
import com.flashcart.repository.*;
import com.flashcart.service.FlashSaleService;
import com.flashcart.service.RateLimitService;
import com.flashcart.websocket.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ═══════════════════════════════════════════════════════════
 *  FLASH SALE PURCHASE — HOW OVERSELLING IS PREVENTED
 * ═══════════════════════════════════════════════════════════
 *
 *  Layer 1 — Redis atomic DECREMENT
 *    Stock is pre-loaded into Redis as a counter.
 *    DECR is atomic: only one thread wins, rest get < 0 and are rejected immediately.
 *    This handles the 99% case with near-zero DB load.
 *
 *  Layer 2 — Optimistic locking (@Version on FlashSaleItem)
 *    If two requests somehow slip through Redis simultaneously,
 *    only one DB UPDATE wins. The loser gets OptimisticLockException → 409.
 *
 *  Layer 3 — Per-user purchase limit (Redis set membership check)
 *    Prevents a single user from buying all stock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleServiceImpl implements FlashSaleService {

    private static final String STOCK_KEY   = "flash:stock:";   // flash:stock:{itemId}
    private static final String BUYERS_KEY  = "flash:buyers:";  // flash:buyers:{itemId}:{userId}

    private final FlashSaleRepository     saleRepo;
    private final FlashSaleItemRepository itemRepo;
    private final ProductRepository       productRepo;
    private final UserRepository          userRepo;
    private final OrderRepository         orderRepo;
    private final RedisTemplate<String, Object> redis;
    private final RateLimitService        rateLimitService;
    private final WebSocketNotificationService wsService;

    @Value("${app.sale.max-quantity-per-user:3}")
    private int globalMaxPerUser;

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    @CacheEvict(value = "flash-sales", allEntries = true)
    public FlashSaleResponse create(CreateFlashSaleRequest req, String adminUsername) {
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (req.getEndTime().isBefore(req.getStartTime()))
            throw new BadRequestException("End time must be after start time");
        if (req.getStartTime().isBefore(LocalDateTime.now()))
            throw new BadRequestException("Start time must be in the future");

        FlashSale sale = FlashSale.builder()
                .name(req.getName()).description(req.getDescription())
                .startTime(req.getStartTime()).endTime(req.getEndTime())
                .createdBy(admin).status(FlashSale.Status.SCHEDULED)
                .build();

        for (FlashSaleItemRequest itemReq : req.getItems()) {
            Product product = productRepo.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            if (itemReq.getSalePrice().compareTo(product.getBasePrice()) >= 0)
                throw new BadRequestException(
                        "Sale price must be less than base price for: " + product.getName());

            if (itemReq.getAllocatedQuantity() > product.getStockQuantity())
                throw new BadRequestException(
                        "Allocated quantity exceeds stock for: " + product.getName());

            FlashSaleItem item = FlashSaleItem.builder()
                    .flashSale(sale).product(product)
                    .salePrice(itemReq.getSalePrice())
                    .allocatedQuantity(itemReq.getAllocatedQuantity())
                    .maxPerUser(itemReq.getMaxPerUser())
                    .build();
            sale.getItems().add(item);
        }

        sale = saleRepo.save(sale);
        log.info("Flash sale created: '{}' starting at {}", sale.getName(), sale.getStartTime());
        return toResponse(sale);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PURCHASE — The critical path
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public OrderResponse purchase(FlashSalePurchaseRequest req, String username) {

        // ── Rate limit: max 5 purchase attempts per minute per user ───────────
        rateLimitService.checkRateLimit("flash-purchase", username, 5);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        FlashSaleItem item = itemRepo.findById(req.getFlashSaleItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale item", req.getFlashSaleItemId()));

        FlashSale sale = item.getFlashSale();

        // ── Validate sale is active ───────────────────────────────────────────
        if (!sale.isCurrentlyActive())
            throw new FlashSaleNotActiveException("Flash sale '" + sale.getName() + "' is not active");

        int qty = req.getQuantity();

        // ── Layer 3: per-user purchase limit ──────────────────────────────────
        String buyersKey = BUYERS_KEY + item.getId() + ":" + user.getId();
        Object existingPurchase = redis.opsForValue().get(buyersKey);
        int alreadyBought = existingPurchase != null ? Integer.parseInt(existingPurchase.toString()) : 0;

        int effectiveMax = Math.min(item.getMaxPerUser(), globalMaxPerUser);
        if (alreadyBought + qty > effectiveMax)
            throw new BadRequestException(
                    "Purchase limit exceeded. You can buy at most " + effectiveMax +
                    " of this item. Already purchased: " + alreadyBought);

        // ── Layer 1: Redis atomic stock decrement ─────────────────────────────
        String stockKey = STOCK_KEY + item.getId();
        Long remaining = redis.opsForValue().decrement(stockKey, qty);

        if (remaining == null || remaining < 0) {
            // Roll back the decrement
            if (remaining != null) redis.opsForValue().increment(stockKey, qty);
            throw new InsufficientStockException("Sorry, this item is sold out!");
        }

        // ── Layer 2: Optimistic locking — update DB ───────────────────────────
        try {
            item.setSoldQuantity(item.getSoldQuantity() + qty);
            item = itemRepo.save(item);   // throws OptimisticLockException if version mismatch
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Roll back Redis decrement
            redis.opsForValue().increment(stockKey, qty);
            throw ex; // GlobalExceptionHandler returns 409 → client retries
        }

        // ── Record per-user purchase in Redis (TTL = sale end time) ──────────
        long ttlSeconds = java.time.Duration.between(LocalDateTime.now(), sale.getEndTime()).getSeconds();
        redis.opsForValue().set(buyersKey, alreadyBought + qty, Math.max(ttlSeconds, 1), TimeUnit.SECONDS);

        // ── Create order ───────────────────────────────────────────────────────
        OrderItem orderItem = OrderItem.builder()
                .product(item.getProduct())
                .flashSaleItem(item)
                .quantity(qty)
                .unitPrice(item.getSalePrice())
                .isFlashSaleItem(true)
                .build();

        BigDecimal total = item.getSalePrice().multiply(BigDecimal.valueOf(qty));

        Order order = Order.builder()
                .user(user)
                .status(Order.Status.CONFIRMED)
                .totalAmount(total)
                .shippingAddress(req.getShippingAddress())
                .build();
        order.getItems().add(orderItem);
        orderItem.setOrder(order);
        order = orderRepo.save(order);

        // ── Broadcast real-time inventory update via WebSocket ────────────────
        wsService.broadcastInventoryUpdate(InventoryUpdateMessage.builder()
                .flashSaleItemId(item.getId())
                .flashSaleId(sale.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .remainingQuantity(item.remainingQuantity())
                .totalAllocated(item.getAllocatedQuantity())
                .salePrice(item.getSalePrice())
                .eventType("INVENTORY_UPDATE")
                .build());

        // ── Personal notification to buyer ────────────────────────────────────
        wsService.sendToUser(username, "ORDER_CONFIRMED",
                "Order Confirmed! 🎉",
                "You purchased " + qty + "x " + item.getProduct().getName() +
                " for ₹" + total + " — Order #" + order.getId());

        log.info("Flash sale purchase: user={} item={} qty={} remaining={}",
                username, item.getId(), qty, remaining);

        return toOrderResponse(order);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCHEDULER CALLBACKS
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void activateDueSales() {
        List<FlashSale> sales = saleRepo.findSalesReadyToStart(LocalDateTime.now());
        for (FlashSale sale : sales) {
            sale.setStatus(FlashSale.Status.ACTIVE);
            saleRepo.save(sale);

            // Pre-load stock counters into Redis for each item
            for (FlashSaleItem item : sale.getItems()) {
                String key = STOCK_KEY + item.getId();
                int available = item.getAllocatedQuantity() - item.getSoldQuantity();
                redis.opsForValue().set(key, available,
                        java.time.Duration.between(LocalDateTime.now(), sale.getEndTime()).getSeconds() + 60,
                        TimeUnit.SECONDS);
                log.info("Redis stock loaded: key={} qty={}", key, available);
            }

            wsService.broadcastSaleEvent(InventoryUpdateMessage.builder()
                    .flashSaleId(sale.getId())
                    .eventType("SALE_STARTED")
                    .build());

            wsService.broadcastGlobal("SALE_STARTED",
                    "⚡ Flash Sale Started!",
                    "'" + sale.getName() + "' is now live! Grab deals before they're gone.");

            log.info("Flash sale activated: id={} name='{}'", sale.getId(), sale.getName());
        }
    }

    @Override
    @Transactional
    public void expireEndedSales() {
        List<FlashSale> sales = saleRepo.findSalesReadyToEnd(LocalDateTime.now());
        for (FlashSale sale : sales) {
            sale.setStatus(FlashSale.Status.ENDED);
            saleRepo.save(sale);

            wsService.broadcastSaleEvent(InventoryUpdateMessage.builder()
                    .flashSaleId(sale.getId())
                    .eventType("SALE_ENDED")
                    .build());

            log.info("Flash sale ended: id={} name='{}'", sale.getId(), sale.getName());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Cacheable(value = "flash-sales", key = "#id")
    @Transactional(readOnly = true)
    public FlashSaleResponse getById(Long id) {
        return toResponse(saleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale", id)));
    }

    @Override
    @Cacheable(value = "flash-sales", key = "'active'")
    @Transactional(readOnly = true)
    public List<FlashSaleResponse> getActiveSales() {
        return saleRepo.findActiveSales().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FlashSaleResponse> getAll(Pageable pageable) {
        return PageResponse.of(saleRepo.findAll(pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    @CacheEvict(value = "flash-sales", allEntries = true)
    public void cancelSale(Long id, String adminUsername) {
        FlashSale sale = saleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale", id));
        if (sale.getStatus() == FlashSale.Status.ENDED)
            throw new BadRequestException("Cannot cancel an already ended sale");
        sale.setStatus(FlashSale.Status.CANCELLED);
        saleRepo.save(sale);

        wsService.broadcastSaleEvent(InventoryUpdateMessage.builder()
                .flashSaleId(sale.getId()).eventType("SALE_CANCELLED").build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPPERS
    // ─────────────────────────────────────────────────────────────────────────
    private FlashSaleResponse toResponse(FlashSale s) {
        return FlashSaleResponse.builder()
                .id(s.getId()).name(s.getName()).description(s.getDescription())
                .startTime(s.getStartTime()).endTime(s.getEndTime())
                .status(s.getStatus().name())
                .items(s.getItems().stream().map(this::toItemResponse).toList())
                .build();
    }

    private FlashSaleItemResponse toItemResponse(FlashSaleItem i) {
        // Try Redis first for real-time remaining count
        String key = STOCK_KEY + i.getId();
        Object redisStock = redis.opsForValue().get(key);
        int remaining = redisStock != null
                ? Integer.parseInt(redisStock.toString())
                : i.remainingQuantity();

        return FlashSaleItemResponse.builder()
                .id(i.getId())
                .productId(i.getProduct().getId())
                .productName(i.getProduct().getName())
                .productImageUrl(i.getProduct().getImageUrl())
                .originalPrice(i.getProduct().getBasePrice())
                .salePrice(i.getSalePrice())
                .discountPercent(Math.round(i.discountPercent() * 10.0) / 10.0)
                .allocatedQuantity(i.getAllocatedQuantity())
                .soldQuantity(i.getSoldQuantity())
                .remainingQuantity(Math.max(0, remaining))
                .maxPerUser(i.getMaxPerUser())
                .build();
    }

    private OrderResponse toOrderResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream().map(i ->
                OrderItemResponse.builder()
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .subtotal(i.subtotal())
                        .isFlashSaleItem(i.getIsFlashSaleItem())
                        .build()).toList();

        return OrderResponse.builder()
                .id(o.getId()).status(o.getStatus().name())
                .totalAmount(o.getTotalAmount())
                .shippingAddress(o.getShippingAddress())
                .items(items).createdAt(o.getCreatedAt())
                .build();
    }
}
