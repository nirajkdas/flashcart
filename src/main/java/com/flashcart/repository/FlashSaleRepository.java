package com.flashcart.repository;

import com.flashcart.entity.FlashSale;
import com.flashcart.entity.FlashSale.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    List<FlashSale> findByStatus(Status status);

    @Query("SELECT fs FROM FlashSale fs WHERE fs.status = 'SCHEDULED' AND fs.startTime <= :now")
    List<FlashSale> findSalesReadyToStart(LocalDateTime now);

    @Query("SELECT fs FROM FlashSale fs WHERE fs.status = 'ACTIVE' AND fs.endTime <= :now")
    List<FlashSale> findSalesReadyToEnd(LocalDateTime now);

    @Query("SELECT fs FROM FlashSale fs WHERE fs.status = 'ACTIVE' ORDER BY fs.endTime ASC")
    List<FlashSale> findActiveSales();
}
