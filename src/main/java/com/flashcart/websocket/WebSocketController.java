package com.flashcart.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * Handles messages sent FROM clients TO the server over WebSocket.
 *
 * Connect via SockJS:  ws://localhost:8080/ws
 * Subscribe to:
 *   /topic/flash-sales/{saleId}/inventory   → real-time stock updates
 *   /topic/flash-sales/events               → SALE_STARTED / SALE_ENDED events
 *   /topic/announcements                    → global broadcasts
 *   /user/queue/notifications               → personal notifications (requires auth)
 */
@Controller
@Slf4j
public class WebSocketController {

    /**
     * Client can ping the server to get an acknowledgement on subscription.
     * Destination: /app/ping
     */
    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public String ping(String username) {
        log.debug("WebSocket ping from: {}", username);
        return "pong";
    }
}
