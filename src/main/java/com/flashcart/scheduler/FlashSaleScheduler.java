package com.flashcart.scheduler;

import com.flashcart.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls every 30 seconds to:
 *   1. Activate sales whose startTime has passed
 *   2. Expire sales whose endTime has passed
 *
 * In a production setup this would be a distributed lock (e.g. Redis SETNX)
 * to prevent multiple instances running the same job simultaneously.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleScheduler {

    private final FlashSaleService flashSaleService;

    @Scheduled(fixedDelay = 30_000)   // every 30 seconds
    public void activateDueSales() {
        log.debug("Scheduler: checking for sales to activate...");
        flashSaleService.activateDueSales();
    }

    @Scheduled(fixedDelay = 30_000)
    public void expireEndedSales() {
        log.debug("Scheduler: checking for sales to expire...");
        flashSaleService.expireEndedSales();
    }
}
