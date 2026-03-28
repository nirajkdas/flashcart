package com.flashcart.dto.response;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private Integer stockQuantity;
    private String categoryName;
    private String sellerName;
    private String imageUrl;
    private Boolean isActive;
    private Double averageRating;
    private LocalDateTime createdAt;
}
