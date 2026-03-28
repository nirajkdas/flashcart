package com.flashcart.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateCategoryRequest {
    @NotBlank @Size(max=100) private String name;
    @NotBlank @Size(max=100) private String slug;
    private String description;
}
