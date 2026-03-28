package com.flashcart.service;

import com.flashcart.dto.request.FlashSalePurchaseRequest;
import com.flashcart.entity.*;
import com.flashcart.exception.*;
import com.flashcart.repository.*;
import com.flashcart.service.impl.FlashSaleServiceImpl;
import com.flashcart.websocket.WebSocketNotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashSaleServiceTest {

    @Mock FlashSaleRepository     saleRepo;
    @Mock FlashSaleItemRepository itemRepo;
    @Mock ProductRepository       productRepo;
    @Mock UserRepository          userRepo;
    @Mock OrderRepository         orderRepo;
    @Mock RedisTemplate<String, Object> redis;
    @Mock ValueOperations<String, Object> valueOps;
    @Mock RateLimitService        rateLimitService;
    @Mock WebSocketNotificationService wsService;

    @InjectMocks FlashSaleServiceImpl service;

    private User      customer;
    private Product   product;
    private FlashSale activeSale;
    private FlashSaleItem saleItem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "globalMaxPerUser", 3);

        customer = User.builder().id(1L).username("alice")
                .role(User.Role.CUSTOMER).build();

        product = Product.builder().id(10L).name("Headphones")
                .basePrice(new BigDecimal("199.99"))
                .stockQuantity(100).isActive(true).build();

        activeSale = FlashSale.builder().id(5L).name("Tech Week")
                .startTime(LocalDateTime.now().minusMinutes(10))
                .endTime(LocalDateTime.now().plusHours(2))
                .status(FlashSale.Status.ACTIVE)
                .items(new ArrayList<>()).build();

        saleItem = FlashSaleItem.builder().id(20L)
                .flashSale(activeSale).product(product)
                .salePrice(new BigDecimal("99.99"))
                .allocatedQuantity(50).soldQuantity(0)
                .maxPerUser(2).version(0L).build();

        activeSale.getItems().add(saleItem);

        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Successful flash sale purchase decrements Redis and creates order")
    void purchase_success() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(customer));
        when(itemRepo.findById(20L)).thenReturn(Optional.of(saleItem));
        when(valueOps.get("flash:buyers:20:1")).thenReturn(null);   // no prior purchase
        when(valueOps.decrement("flash:stock:20", 1)).thenReturn(49L);

        Order savedOrder = Order.builder().id(100L)
                .user(customer).status(Order.Status.CONFIRMED)
                .totalAmount(new BigDecimal("99.99"))
                .items(new ArrayList<>()).createdAt(LocalDateTime.now()).build();

        when(itemRepo.save(any())).thenReturn(saleItem);
        when(orderRepo.save(any())).thenReturn(savedOrder);

        FlashSalePurchaseRequest req = new FlashSalePurchaseRequest(20L, 1, "123 Main St");
        var response = service.purchase(req, "alice");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);

        verify(valueOps).decrement("flash:stock:20", 1);
        verify(itemRepo).save(argThat(i -> i.getSoldQuantity() == 1));
        verify(wsService).broadcastInventoryUpdate(any());
        verify(wsService).sendToUser(eq("alice"), any(), any(), any());
    }

    // ── Oversell prevention ────────────────────────────────────────────────

    @Test
    @DisplayName("Redis returns negative → throws InsufficientStockException (no DB hit)")
    void purchase_stockExhausted_redisDecrement() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(customer));
        when(itemRepo.findById(20L)).thenReturn(Optional.of(saleItem));
        when(valueOps.get("flash:buyers:20:1")).thenReturn(null);
        when(valueOps.decrement("flash:stock:20", 1)).thenReturn(-1L);  // stock exhausted

        FlashSalePurchaseRequest req = new FlashSalePurchaseRequest(20L, 1, null);

        assertThatThrownBy(() -> service.purchase(req, "alice"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("sold out");

        verify(valueOps).increment("flash:stock:20", 1);  // rollback confirmed
        verify(orderRepo, never()).save(any());
    }

    // ── Per-user limit ─────────────────────────────────────────────────────

    @Test
    @DisplayName("User exceeding per-item limit → BadRequestException")
    void purchase_userLimitExceeded() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(customer));
        when(itemRepo.findById(20L)).thenReturn(Optional.of(saleItem));
        when(valueOps.get("flash:buyers:20:1")).thenReturn("2");  // already bought max (2)

        FlashSalePurchaseRequest req = new FlashSalePurchaseRequest(20L, 1, null);

        assertThatThrownBy(() -> service.purchase(req, "alice"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit exceeded");

        verify(valueOps, never()).decrement(any(), anyLong());
    }

    // ── Sale not active ────────────────────────────────────────────────────

    @Test
    @DisplayName("Purchasing from a SCHEDULED sale → FlashSaleNotActiveException")
    void purchase_saleNotActive() {
        FlashSale scheduledSale = FlashSale.builder().id(6L)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(3))
                .status(FlashSale.Status.SCHEDULED)
                .items(new ArrayList<>()).build();

        FlashSaleItem futureItem = FlashSaleItem.builder().id(21L)
                .flashSale(scheduledSale).product(product)
                .salePrice(new BigDecimal("49.99"))
                .allocatedQuantity(10).soldQuantity(0).maxPerUser(1).version(0L).build();
        scheduledSale.getItems().add(futureItem);

        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(customer));
        when(itemRepo.findById(21L)).thenReturn(Optional.of(futureItem));

        FlashSalePurchaseRequest req = new FlashSalePurchaseRequest(21L, 1, null);

        assertThatThrownBy(() -> service.purchase(req, "alice"))
                .isInstanceOf(FlashSaleNotActiveException.class);
    }

    // ── Rate limiting ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Rate limit exceeded → RateLimitExceededException before any DB call")
    void purchase_rateLimitExceeded() {
        doThrow(new RateLimitExceededException("Too many requests"))
                .when(rateLimitService).checkRateLimit(eq("flash-purchase"), eq("alice"), anyInt());

        FlashSalePurchaseRequest req = new FlashSalePurchaseRequest(20L, 1, null);

        assertThatThrownBy(() -> service.purchase(req, "alice"))
                .isInstanceOf(RateLimitExceededException.class);

        verify(userRepo, never()).findByUsername(any());
    }
}
