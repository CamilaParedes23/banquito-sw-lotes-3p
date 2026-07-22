package com.banquito.switchpagos.batch.service.impl;

import com.banquito.switchpagos.batch.dto.event.PaymentLineRejectedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRequestedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOffUsEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOnUsEvent;
import com.banquito.switchpagos.batch.service.PaymentLineEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "pubsub")
public class PubSubPaymentLineEventPublisher implements PaymentLineEventPublisher {

    private static final String SOURCE_SERVICE = "batch-service";

    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String topicName;
    private final String schemaVersion;
    private Publisher publisher;

    public PubSubPaymentLineEventPublisher(
            ObjectMapper objectMapper,
            @Value("${pubsub.project-id}") String projectId,
            @Value("${pubsub.topic.payment-lines}") String topicName,
            @Value("${pubsub.schema-version}") String schemaVersion) {
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.topicName = topicName;
        this.schemaVersion = schemaVersion;
    }

    @PostConstruct
    public void start() throws Exception {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT es obligatorio cuando MESSAGING_PROVIDER=pubsub");
        }
        this.publisher = Publisher.newBuilder(ProjectTopicName.of(projectId, topicName)).build();
    }

    @PreDestroy
    public void stop() throws Exception {
        if (publisher != null) {
            publisher.shutdown();
            publisher.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Override
    public void publishRequested(PaymentLineRequestedEvent event) {
        publish("PAYMENT_LINE_REQUESTED", event, event.getBatchId(), event.getLineId(), event.getCorrelationId());
    }

    @Override
    public void publishOnUs(PaymentLineRoutedOnUsEvent event) {
        publish("PAYMENT_LINE_ON_US", event, event.getBatchId(), event.getLineId(), event.getCorrelationId());
    }

    @Override
    public void publishOffUs(PaymentLineRoutedOffUsEvent event) {
        publish("PAYMENT_LINE_OFF_US", event, event.getBatchId(), event.getLineId(), event.getCorrelationId());
    }

    @Override
    public void publishRejected(PaymentLineRejectedEvent event) {
        publish("PAYMENT_LINE_REJECTED", event, event.getBatchId(), event.getLineId(), event.getCorrelationId());
    }

    private void publish(String eventType, Object event, UUID batchId, UUID lineId, UUID correlationId) {
        try {
            Map<String, String> attributes = baseAttributes(eventType, correlationId);
            attributes.put("batchId", batchId.toString());
            attributes.put("lineId", lineId.toString());
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(objectMapper.writeValueAsString(event)))
                    .putAllAttributes(attributes)
                    .build();
            publisher.publish(message).get(30, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar evento Pub/Sub " + eventType, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo publicar evento Pub/Sub " + eventType, ex);
        }
    }

    private Map<String, String> baseAttributes(String eventType, UUID correlationId) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("eventType", eventType);
        attributes.put("sourceService", SOURCE_SERVICE);
        attributes.put("schemaVersion", schemaVersion);
        attributes.put("correlationId", correlationId.toString());
        return attributes;
    }
}
