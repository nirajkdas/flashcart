package com.flashcart.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "flash_sale_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FlashSaleItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flash_sale_id", nullable = false)
    private FlashSale flashSale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "allocated_quantity", nullable = false)
    private Integer allocatedQuantity;

    @Column(name = "sold_quantity", nullable = false)
    @Builder.Default
    private Integer soldQuantity = 0;

    @Column(name = "max_per_user", nullable = false)
    @Builder.Default
    private Integer maxPerUser = 1;

    /**
     * Optimistic locking version field.
     * If two threads try to update this row simultaneously,
     * only one will succeed — the other gets OptimisticLockException.
     * This is the core mechanism preventing overselling in flash sales.
     */
    @Version
    private Long version;

    public int remainingQuantity() {
        return allocatedQuantity - soldQuantity;
    }

    public boolean hasStock(int requested) {
        return remainingQuantity() >= requested;
    }

    public double discountPercent() {
        if (product == null || product.getBasePrice().compareTo(BigDecimal.ZERO) == 0) return 0;
        return (1 - salePrice.doubleValue() / product.getBasePrice().doubleValue()) * 100;
    }
}
