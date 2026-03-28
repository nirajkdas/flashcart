package com.flashcart.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor
public class PlaceOrderRequest {
    @NotEmpty private List<OrderItemRequest> items;
    private String shippingAddress;
    private String notes;
}
