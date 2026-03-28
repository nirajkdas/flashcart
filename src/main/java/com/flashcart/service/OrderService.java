package com.flashcart.service;

import com.flashcart.dto.request.PlaceOrderRequest;
import com.flashcart.dto.response.OrderResponse;
import com.flashcart.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse placeOrder(PlaceOrderRequest req, String username);
    OrderResponse getById(Long id, String username);
    PageResponse<OrderResponse> getMyOrders(String username, Pageable pageable);
    OrderResponse updateStatus(Long id, String status, String adminUsername);
}
