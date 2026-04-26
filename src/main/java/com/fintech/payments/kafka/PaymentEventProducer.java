package com.fintech.payments.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.payment-completed:payment.completed}")
    private String paymentCompletedTopic;

    @Value("${kafka.topics.payment-failed:payment.failed}")
    private String paymentFailedTopic;

    public void publishPaymentCompleted(Transaction transaction) {
        publishEvent(paymentCompletedTopic, transaction);
    }

    public void publishPaymentFailed(Transaction transaction) {
        publishEvent(paymentFailedTopic, transaction);
    }

    private void publishEvent(String topic, Transaction transaction) {
        try {
            String payload = objectMapper.writeValueAsString(transaction);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, transaction.getId(), payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to {}: {}", topic, ex.getMessage());
                } else {
                    log.debug("Event published to {} | partition={} offset={}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Error serializing transaction event for {}: {}", transaction.getId(), e.getMessage());
        }
    }
}
