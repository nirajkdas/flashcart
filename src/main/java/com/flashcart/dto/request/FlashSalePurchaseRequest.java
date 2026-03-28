package com.flashcart.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class FlashSalePurchaseRequest {
    @NotNull private Long flashSaleItemId;
    @NotNull @Min(1) private Integer quantity;
    private String shippingAddress;
}
