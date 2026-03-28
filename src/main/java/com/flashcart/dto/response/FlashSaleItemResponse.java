package com.flashcart.dto.response;
import lombok.*;
import java.math.BigDecimal;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FlashSaleItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private BigDecimal originalPrice;
    private BigDecimal salePrice;
    private double discountPercent;
    private Integer allocatedQuantity;
    private Integer soldQuantity;
    private Integer remainingQuantity;
    private Integer maxPerUser;
}
