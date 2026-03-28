package com.flashcart.service;

import com.flashcart.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Sliding-window rate limiter backed by Redis.
 *
 * Key pattern:  rate_limit:{action}:{identifier}
 * Strategy:     Increment a counter per minute window.
 *               First request in window sets TTL = 60s.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.rate-limit.requests-per-minute:30}")
    private int defaultLimit;

    /**
     * Check rate limit. Throws if exceeded.
     *
     * @param action     e.g. "flash-purchase", "login"
     * @param identifier e.g. username or IP
     * @param limit      max requests per minute (0 = use default)
     */
    public void checkRateLimit(String action, String identifier, int limit) {
        if (limit <= 0) limit = defaultLimit;
        String key = "rate_limit:" + action + ":" + identifier;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) return; // Redis unavailable — fail open

        if (count == 1) {
            // First request in this window — set TTL
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        if (count > limit) {
            log.warn("Rate limit exceeded for {} on action {}: {}/{}", identifier, action, count, limit);
            throw new RateLimitExceededException(
                    "Too many requests. You are limited to " + limit + " per minute for: " + action);
        }
    }

    /** Overload using default limit. */
    public void checkRateLimit(String action, String identifier) {
        checkRateLimit(action, identifier, defaultLimit);
    }

    /** Get remaining requests in current window. */
    public long getRemainingRequests(String action, String identifier, int limit) {
        String key = "rate_limit:" + action + ":" + identifier;
        Object val = redisTemplate.opsForValue().get(key);
        long used = val != null ? Long.parseLong(val.toString()) : 0;
        return Math.max(0, limit - used);
    }
}
