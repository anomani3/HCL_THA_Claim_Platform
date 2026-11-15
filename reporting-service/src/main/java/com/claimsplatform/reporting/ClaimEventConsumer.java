package com.claimsplatform.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes claim-events. Exceptions propagate to Spring Kafka's default error handler
 * (retry with back-off, then log and skip). A dead-letter topic is listed as a next step.
 */
@Component
public class ClaimEventConsumer {

    private final ObjectMapper mapper;
    private final ClaimProjector projector;

    public ClaimEventConsumer(ObjectMapper mapper, ClaimProjector projector) {
        this.mapper = mapper;
        this.projector = projector;
    }

    @KafkaListener(topics = "${claims.kafka.topic}", groupId = "reporting-service")
    public void onMessage(String payload) throws JsonProcessingException {
        projector.apply(mapper.readValue(payload, ClaimEventDto.class));
    }
}
