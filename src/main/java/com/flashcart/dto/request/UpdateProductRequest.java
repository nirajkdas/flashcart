package com.flashcart.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateProductRequest {
    @Size(max=255) private String name;
    private String description;
    @DecimalMin("0.01") private BigDecimal basePrice;
    @Min(0) private Integer stockQuantity;
    private Long categoryId;
    private String imageUrl;
    private Boolean isActive;
}
