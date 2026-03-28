package com.flashcart.repository;

import com.flashcart.entity.FlashSaleItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    List<FlashSaleItem> findByFlashSaleId(Long flashSaleId);

    Optional<FlashSaleItem> findByFlashSaleIdAndProductId(Long flashSaleId, Long productId);

    /**
     * Pessimistic write lock — used as a fallback safety net during purchase.
     * Primary prevention is handled via Redis atomic decrement + optimistic locking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fsi FROM FlashSaleItem fsi WHERE fsi.id = :id")
    Optional<FlashSaleItem> findByIdWithLock(@Param("id") Long id);
}
