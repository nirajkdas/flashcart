package com.flashcart.dto.response;
import lombok.*;
import java.math.BigDecimal;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryUpdateMessage {
    private Long flashSaleItemId;
    private Long flashSaleId;
    private Long productId;
    private String productName;
    private Integer remainingQuantity;
    private Integer totalAllocated;
    private BigDecimal salePrice;
    private String eventType;
}
