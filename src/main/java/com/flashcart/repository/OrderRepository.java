package com.flashcart.repository;

import com.flashcart.entity.Order;
import com.flashcart.entity.Order.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(Status status, Pageable pageable);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId " +
           "AND o.status NOT IN ('CANCELLED','REFUNDED')")
    long countActiveOrdersByUser(@Param("userId") Long userId);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'DELIVERED' " +
           "AND o.createdAt BETWEEN :from AND :to")
    BigDecimal sumRevenueInPeriod(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("SELECT o FROM Order o JOIN o.items i WHERE i.flashSaleItem.flashSale.id = :saleId")
    List<Order> findOrdersByFlashSale(@Param("saleId") Long saleId);
}
