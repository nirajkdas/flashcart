package com.flashcart.controller;

import com.flashcart.dto.response.ApiResponse;
import com.flashcart.dto.response.PageResponse;
import com.flashcart.entity.Notification;
import com.flashcart.entity.User;
import com.flashcart.exception.ResourceNotFoundException;
import com.flashcart.repository.NotificationRepository;
import com.flashcart.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification inbox")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationRepository notificationRepo;
    private final UserRepository         userRepo;

    @GetMapping
    @Operation(summary = "Get my notifications (personal + broadcasts)")
    public ApiResponse<PageResponse<?>> getNotifications(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var notifications = notificationRepo
                .findByUserIdOrUserIsNullOrderByCreatedAtDesc(user.getId(), PageRequest.of(page, size))
                .map(n -> new NotificationView(
                        n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                        n.getIsRead(), n.getCreatedAt()));

        return ApiResponse.ok(PageResponse.of(notifications));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count unread notifications")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepo.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ApiResponse.ok(notificationRepo.countByUserIdAndIsReadFalse(user.getId()));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "Mark all notifications as read")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepo.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        notificationRepo.markAllReadForUser(user.getId());
        return ApiResponse.ok("Marked all as read", null);
    }

    record NotificationView(Long id, String type, String title,
                            String message, Boolean isRead, LocalDateTime createdAt) {}
}
