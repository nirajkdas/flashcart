package com.flashcart.dto.response;
import lombok.*;
import java.time.LocalDateTime;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewResponse {
    private Long id;
    private String username;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
