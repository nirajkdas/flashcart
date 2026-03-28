package com.flashcart.service.impl;

import com.flashcart.dto.request.OrderItemRequest;
import com.flashcart.dto.request.PlaceOrderRequest;
import com.flashcart.dto.response.OrderItemResponse;
import com.flashcart.dto.response.OrderResponse;
import com.flashcart.dto.response.PageResponse;
import com.flashcart.entity.*;
import com.flashcart.exception.*;
import com.flashcart.repository.*;
import com.flashcart.service.OrderService;
import com.flashcart.websocket.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository               orderRepo;
    private final UserRepository                userRepo;
    private final ProductRepository             productRepo;
    private final WebSocketNotificationService  wsService;

    // ── Place a regular (non-flash-sale) order ────────────────────────────────
    @Override
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest req, String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : req.getItems()) {
            Product product = productRepo.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            if (!product.getIsActive())
                throw new BadRequestException("Product is not available: " + product.getName());

            if (product.getStockQuantity() < itemReq.getQuantity())
                throw new InsufficientStockException(
                        "Insufficient stock for: " + product.getName() +
                        " (available: " + product.getStockQuantity() + ")");

            // Deduct stock
            product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
            productRepo.save(product);

            BigDecimal unitPrice = product.getBasePrice();
            OrderItem oi = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .isFlashSaleItem(false)
                    .build();
            orderItems.add(oi);
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        Order order = Order.builder()
                .user(user)
                .status(Order.Status.CONFIRMED)
                .totalAmount(total)
                .shippingAddress(req.getShippingAddress())
                .notes(req.getNotes())
                .build();

        orderItems.forEach(oi -> oi.setOrder(order));
        order.getItems().addAll(orderItems);
        Order saved = orderRepo.save(order);

        wsService.sendToUser(username, "ORDER_PLACED",
                "Order Placed! 🛒",
                "Your order #" + saved.getId() + " worth ₹" + total + " has been confirmed.");

        log.info("Order placed: id={} user={} total={}", saved.getId(), username, total);
        return toResponse(saved);
    }

    // ── Fetch a single order (user sees only their own; admin sees all) ────────
    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(Long id, String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = user.getRole() == User.Role.ADMIN
                ? orderRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id))
                : orderRepo.findByIdAndUserId(id, user.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(String username, Pageable pageable) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return PageResponse.of(orderRepo.findByUserId(user.getId(), pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long id, String status, String adminUsername) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        try {
            Order.Status newStatus = Order.Status.valueOf(status.toUpperCase());
            order.setStatus(newStatus);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid order status: " + status);
        }
        Order saved = orderRepo.save(order);

        wsService.sendToUser(saved.getUser().getUsername(), "ORDER_STATUS_UPDATE",
                "Order Update 📦",
                "Your order #" + saved.getId() + " is now: " + saved.getStatus().name());

        return toResponse(saved);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────
    private OrderResponse toResponse(Order o) {
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
