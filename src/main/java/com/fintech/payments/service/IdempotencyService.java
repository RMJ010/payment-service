package com.fintech.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payments.model.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    public boolean isDuplicate(String idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + idempotencyKey));
    }

    public PaymentResponse getResult(String idempotencyKey) {
        try {
            String json = redisTemplate.opsForValue().get(PREFIX + idempotencyKey);
            if (json != null) {
                return objectMapper.readValue(json, PaymentResponse.class);
            }
        } catch (Exception e) {
            log.error("Error reading idempotency result for key: {}", idempotencyKey, e);
        }
        return null;
    }

    public void saveResult(String idempotencyKey, PaymentResponse result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(PREFIX + idempotencyKey, json, TTL);
        } catch (Exception e) {
            log.error("Error saving idempotency result for key: {}", idempotencyKey, e);
        }
    }
}
