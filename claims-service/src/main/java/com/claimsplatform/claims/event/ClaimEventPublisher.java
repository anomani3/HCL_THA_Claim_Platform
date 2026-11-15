package com.claimsplatform.claims.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes events only AFTER the database transaction commits, so consumers never see
 * an event for a change that was rolled back.
 *
 * Known trade-off: if the process dies between commit and send, the event is lost (dual-write
 * problem). The production fix is a transactional outbox; see docs/ARCHITECTURE.md.
 */
@Component
public class ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public ClaimEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                               @Value("${claims.kafka.topic}") String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ClaimEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            // Keyed by claimId => all events for one claim land on one partition, in order.
            kafka.send(topic, event.claimId().toString(), json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish {} for claim {}", event.eventType(), event.claimNumber(), ex);
                } else {
                    log.info("Published {} for claim {} (v{}) to {}-{}@{}", event.eventType(), event.claimNumber(),
                            event.version(), topic, result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Could not serialise event for claim {}", event.claimNumber(), e);
        }
    }
}
