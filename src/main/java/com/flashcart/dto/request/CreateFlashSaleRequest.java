package com.flashcart.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateFlashSaleRequest {
    @NotBlank @Size(max=255) private String name;
    private String description;
    @NotNull private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
    @NotEmpty private List<FlashSaleItemRequest> items;
}
