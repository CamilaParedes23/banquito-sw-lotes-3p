package com.banquito.switchpagos.batch.listener;

import com.banquito.switchpagos.batch.dto.event.BillingCompletedEvent;
import com.banquito.switchpagos.batch.enums.BatchStatus;
import com.banquito.switchpagos.batch.model.PaymentBatch;
import com.banquito.switchpagos.batch.repository.PaymentBatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "messaging.provider", havingValue = "pubsub")
public class PubSubBillingCompletedSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubBillingCompletedSubscriber.class);

    private final ObjectMapper objectMapper;
    private final PaymentBatchRepository paymentBatchRepository;
    private final String projectId;
    private final String subscriptionName;
    private Subscriber subscriber;

    public PubSubBillingCompletedSubscriber(
            ObjectMapper objectMapper,
            PaymentBatchRepository paymentBatchRepository,
            @Value("${pubsub.project-id}") String projectId,
            @Value("${pubsub.subscription.batch-billing-completed}") String subscriptionName) {
        this.objectMapper = objectMapper;
        this.paymentBatchRepository = paymentBatchRepository;
        this.projectId = projectId;
        this.subscriptionName = subscriptionName;
    }

    @PostConstruct
    public void start() {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT es obligatorio cuando MESSAGING_PROVIDER=pubsub");
        }
        MessageReceiver receiver = this::receive;
        subscriber = Subscriber.newBuilder(ProjectSubscriptionName.of(projectId, subscriptionName), receiver).build();
        subscriber.startAsync().awaitRunning();
    }

    @PreDestroy
    public void stop() throws Exception {
        if (subscriber != null) {
            subscriber.stopAsync().awaitTerminated(30, TimeUnit.SECONDS);
        }
    }

    private void receive(PubsubMessage message, AckReplyConsumer consumer) {
        try {
            BillingCompletedEvent event = objectMapper.readValue(message.getData().toStringUtf8(), BillingCompletedEvent.class);
            onBillingCompleted(event);
            consumer.ack();
        } catch (Exception ex) {
            LOG.error("Error procesando BILLING_COMPLETED desde Pub/Sub. messageId={}", message.getMessageId(), ex);
            consumer.nack();
        }
    }

    @Transactional
    public void onBillingCompleted(BillingCompletedEvent event) {
        if (event == null) {
            LOG.warn("BillingCompletedEvent nulo ignorado por batch-service");
            return;
        }
        UUID batchId = event.getBatchId();
        PaymentBatch batch = paymentBatchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            LOG.warn("No se encontro el lote para actualizar estado a CERRADO. batchId={}", batchId);
            return;
        }
        if (!BatchStatus.PROCESANDO_LINEAS.name().equals(batch.getStatus())) {
            LOG.info("Lote no actualizable a CERRADO. batchId={}, currentStatus={}", batchId, batch.getStatus());
            return;
        }
        batch.setStatus(BatchStatus.CERRADO.name());
        batch.setUpdatedAt(OffsetDateTime.now());
        paymentBatchRepository.save(batch);
        LOG.info("Estado del lote actualizado a CERRADO por Pub/Sub. batchId={}", batchId);
    }
}
