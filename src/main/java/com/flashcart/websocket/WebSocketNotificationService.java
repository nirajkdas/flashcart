package com.flashcart.websocket;

import com.flashcart.dto.response.InventoryUpdateMessage;
import com.flashcart.entity.FlashSale;
import com.flashcart.entity.FlashSaleItem;
import com.flashcart.entity.Notification;
import com.flashcart.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Broadcasts real-time events to WebSocket subscribers.
 *
 * Clients subscribe to:
 *   /topic/flash-sale/{saleId}          — inventory updates for a specific sale
 *   /topic/flash-sales                  — global sale lifecycle events (STARTED / ENDED / CANCELLED)
 *   /topic/announcements                — global admin broadcasts
 *   /user/queue/notifications           — personal purchase confirmations and alerts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate  messagingTemplate;
    private final NotificationRepository notificationRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // Called by FlashSaleServiceImpl — overload accepting a pre-built DTO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Broadcast a fully-built {@link InventoryUpdateMessage} after every purchase.
     * Called directly from FlashSaleServiceImpl so the caller controls the payload.
     *
     * Topic: /topic/flash-sales/{saleId}/inventory
     */
    public void broadcastInventoryUpdate(InventoryUpdateMessage msg) {
        String destination = "/topic/flash-sales/" + msg.getFlashSaleId() + "/inventory";
        messagingTemplate.convertAndSend(destination, msg);
        log.debug("Inventory update → {}: {} remaining for item {}",
                destination, msg.getRemainingQuantity(), msg.getFlashSaleItemId());
    }

    /**
     * Broadcast a sale lifecycle event (SALE_STARTED / SALE_ENDED / SALE_CANCELLED)
     * using a pre-built {@link InventoryUpdateMessage}.
     *
     * Topic: /topic/flash-sales/events
     */
    public void broadcastSaleEvent(InventoryUpdateMessage msg) {
        messagingTemplate.convertAndSend("/topic/flash-sales/events", msg);
        log.info("Sale event broadcast: {} for saleId={}", msg.getEventType(), msg.getFlashSaleId());
    }

    /**
     * Send a personal notification to a specific user over WebSocket and persist it to the DB
     * so the user can retrieve missed notifications via the REST inbox.
     *
     * Destination: /user/{username}/queue/notifications
     *
     * @param username  the recipient's Spring Security username
     * @param type      notification type label, e.g. "ORDER_CONFIRMED"
     * @param title     short heading shown in the UI
     * @param message   full notification body
     */
    public void sendToUser(String username, String type, String title, String message) {
        // Persist so the user can fetch it later via GET /api/notifications
        Notification notification = Notification.builder()
                .type(type)
                .title(title)
                .message(message)
                .build();
        notificationRepo.save(notification);

        // Push to the connected WebSocket session (no-op if user is offline)
        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/notifications",
                new NotificationPayload(type, title, message));
        log.debug("Personal notification → {}: {}", username, title);
    }

    /**
     * Broadcast a global announcement to every connected client and persist it to the DB
     * so offline users can retrieve it later.
     *
     * Topic: /topic/announcements
     *
     * @param type    notification type label, e.g. "SALE_STARTED"
     * @param title   short heading
     * @param message full announcement body
     */
    public void broadcastGlobal(String type, String title, String message) {
        // Persist without a user — null user_id = broadcast notification in the DB
        Notification notification = Notification.builder()
                .type(type)
                .title(title)
                .message(message)
                .build();
        notificationRepo.save(notification);

        messagingTemplate.convertAndSend(
                "/topic/announcements",
                new NotificationPayload(type, title, message));
        log.info("Global broadcast: [{}] {}", type, title);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Original entity-based overloads — kept for backwards compatibility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convenience overload: build the InventoryUpdateMessage from a {@link FlashSaleItem}
     * and broadcast it. Used when the caller has the entity rather than the DTO.
     */
    public void broadcastInventoryUpdate(FlashSaleItem item) {
        broadcastInventoryUpdate(InventoryUpdateMessage.builder()
                .flashSaleItemId(item.getId())
                .flashSaleId(item.getFlashSale().getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .remainingQuantity(item.remainingQuantity())
                .totalAllocated(item.getAllocatedQuantity())
                .salePrice(item.getSalePrice())
                .eventType("INVENTORY_UPDATE")
                .build());
    }

    /**
     * Notify all clients that a flash sale has started (entity-based overload).
     */
    public void broadcastSaleStarted(FlashSale sale) {
        broadcastSaleEvent(InventoryUpdateMessage.builder()
                .flashSaleId(sale.getId())
                .eventType("SALE_STARTED")
                .build());
    }

    /**
     * Notify all clients that a flash sale has ended (entity-based overload).
     */
    public void broadcastSaleEnded(FlashSale sale) {
        broadcastSaleEvent(InventoryUpdateMessage.builder()
                .flashSaleId(sale.getId())
                .eventType("SALE_ENDED")
                .build());
    }

    /**
     * Send a personal purchase confirmation to a specific user (legacy overload).
     */
    public void sendPurchaseConfirmation(String username, Long orderId, String productName) {
        sendToUser(
                username,
                "PURCHASE_CONFIRMED",
                "Order Confirmed! 🎉",
                "Your order #" + orderId + " for \"" + productName + "\" has been placed!");
    }

    /**
     * Broadcast a sold-out alert for a specific flash sale item (entity-based overload).
     */
    public void broadcastSoldOut(FlashSaleItem item) {
        broadcastInventoryUpdate(InventoryUpdateMessage.builder()
                .flashSaleItemId(item.getId())
                .flashSaleId(item.getFlashSale().getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .remainingQuantity(0)
                .totalAllocated(item.getAllocatedQuantity())
                .salePrice(item.getSalePrice())
                .eventType("SOLD_OUT")
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal payload record
    // ─────────────────────────────────────────────────────────────────────────

    public record NotificationPayload(String type, String title, String message) {}
}
