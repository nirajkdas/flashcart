package com.flashcart.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FlashSaleItemRequest {
    @NotNull private Long productId;
    @NotNull @DecimalMin("0.01") private BigDecimal salePrice;
    @NotNull @Min(1) private Integer allocatedQuantity;
    @Min(1) private Integer maxPerUser = 1;
}
