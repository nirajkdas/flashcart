package com.flashcart.service;

import com.flashcart.dto.request.CreateFlashSaleRequest;
import com.flashcart.dto.request.FlashSalePurchaseRequest;
import com.flashcart.dto.response.FlashSaleResponse;
import com.flashcart.dto.response.OrderResponse;
import com.flashcart.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FlashSaleService {
    FlashSaleResponse create(CreateFlashSaleRequest req, String adminUsername);
    FlashSaleResponse getById(Long id);
    List<FlashSaleResponse> getActiveSales();
    PageResponse<FlashSaleResponse> getAll(Pageable pageable);
    void cancelSale(Long id, String adminUsername);

    /** Core method — atomic purchase with Redis + optimistic locking */
    OrderResponse purchase(FlashSalePurchaseRequest req, String username);

    /** Called by scheduler */
    void activateDueSales();
    void expireEndedSales();
}
