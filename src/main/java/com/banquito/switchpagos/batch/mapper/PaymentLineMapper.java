package com.banquito.switchpagos.batch.mapper;

import com.banquito.switchpagos.batch.dto.event.PaymentLineRequestedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRejectedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOffUsEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOnUsEvent;
import com.banquito.switchpagos.batch.dto.request.ParsedPaymentLine;
import com.banquito.switchpagos.batch.dto.response.BatchLineResponse;
import com.banquito.switchpagos.batch.enums.LineStatus;
import com.banquito.switchpagos.batch.enums.RoutingInstitution;
import com.banquito.switchpagos.batch.model.BatchPaymentLine;
import com.banquito.switchpagos.batch.model.PaymentBatch;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentLineMapper {

    private static final String SOURCE_SERVICE = "banquito-switch-batch-service";

    public BatchPaymentLine toEntity(UUID batchId, ParsedPaymentLine parsedLine) {
        OffsetDateTime now = OffsetDateTime.now();
        BatchPaymentLine line = new BatchPaymentLine();
        line.setLineId(UUID.randomUUID());
        line.setBatchId(batchId);
        line.setSequenceNumber(parsedLine.getSequenceNumber());
        line.setBeneficiaryIdentification(parsedLine.getBeneficiaryIdentification());
        line.setBeneficiaryName(parsedLine.getBeneficiaryName());
        line.setDestinationAccountNumber(parsedLine.getDestinationAccountNumber());
        line.setRoutingCode(parsedLine.getRoutingCode());
        line.setAmount(parsedLine.getAmount());
        line.setCurrency("USD");
        line.setReference(parsedLine.getReference());
        line.setNotificationEmail(parsedLine.getNotificationEmail());
        line.setStatus(LineStatus.PENDIENTE.name());
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        return line;
    }

    public PaymentLineRequestedEvent toEvent(PaymentBatch batch, BatchPaymentLine line, UUID eventId) {
        PaymentLineRequestedEvent event = new PaymentLineRequestedEvent();
        event.setEventId(eventId);
        event.setEventType("PAYMENT_LINE_REQUESTED");
        event.setOccurredAt(OffsetDateTime.now());
        event.setBatchId(batch.getBatchId());
        event.setLineId(line.getLineId());
        event.setCorrelationId(batch.getCorrelationId());
        event.setSourceService(SOURCE_SERVICE);
        event.setCompanyRuc(batch.getCompanyRuc());
        event.setSourceAccountNumber(batch.getSourceAccountNumber());
        event.setCoreFundingId(batch.getCoreFundingId());
        event.setBatchTotalLines(batch.getTotalRecords());
        event.setBatchControlAmount(batch.getControlAmount());
        event.setSequenceNumber(line.getSequenceNumber());
        event.setBeneficiaryIdentification(line.getBeneficiaryIdentification());
        event.setBeneficiaryName(line.getBeneficiaryName());
        event.setDestinationAccountNumber(line.getDestinationAccountNumber());
        event.setRoutingCode(line.getRoutingCode());
        event.setAmount(line.getAmount());
        event.setCurrency(batch.getCurrency());
        event.setReference(line.getReference());
        event.setNotificationEmail(line.getNotificationEmail());
        return event;
    }

    public PaymentLineRoutedOnUsEvent toOnUsEvent(
            PaymentLineRequestedEvent requestedEvent,
            RoutingInstitution institution,
            UUID eventId) {
        PaymentLineRoutedOnUsEvent event = new PaymentLineRoutedOnUsEvent();
        fillRoutedEvent(event, requestedEvent, institution, eventId, "PAYMENT_LINE_ROUTED_ON_US");
        return event;
    }

    public PaymentLineRoutedOffUsEvent toOffUsEvent(
            PaymentLineRequestedEvent requestedEvent,
            RoutingInstitution institution,
            UUID eventId) {
        PaymentLineRoutedOffUsEvent event = new PaymentLineRoutedOffUsEvent();
        fillRoutedEvent(event, requestedEvent, institution, eventId, "PAYMENT_LINE_ROUTED_OFF_US");
        return event;
    }

    public PaymentLineRejectedEvent toRejectedEvent(
            PaymentLineRequestedEvent requestedEvent,
            UUID eventId,
            String rejectionCode,
            String rejectionReason) {
        PaymentLineRejectedEvent event = new PaymentLineRejectedEvent();
        event.setEventId(eventId);
        event.setEventType("PAYMENT_LINE_REJECTED");
        event.setOccurredAt(OffsetDateTime.now());
        event.setBatchId(requestedEvent.getBatchId());
        event.setLineId(requestedEvent.getLineId());
        event.setCorrelationId(requestedEvent.getCorrelationId());
        event.setSourceService(SOURCE_SERVICE);
        event.setSequenceNumber(requestedEvent.getSequenceNumber());
        event.setBeneficiaryIdentification(requestedEvent.getBeneficiaryIdentification());
        event.setBeneficiaryName(requestedEvent.getBeneficiaryName());
        event.setDestinationAccountNumber(requestedEvent.getDestinationAccountNumber());
        event.setRoutingCode(requestedEvent.getRoutingCode());
        event.setAmount(requestedEvent.getAmount());
        event.setCurrency(requestedEvent.getCurrency());
        event.setReference(requestedEvent.getReference());
        event.setFinalStatus("RECHAZADA");
        event.setBillable(Boolean.FALSE);
        event.setRejectionCode(rejectionCode);
        event.setRejectionReason(rejectionReason);
        return event;
    }

    private void fillRoutedEvent(
            PaymentLineRoutedOnUsEvent event,
            PaymentLineRequestedEvent requestedEvent,
            RoutingInstitution institution,
            UUID eventId,
            String eventType) {
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setOccurredAt(OffsetDateTime.now());
        event.setBatchId(requestedEvent.getBatchId());
        event.setLineId(requestedEvent.getLineId());
        event.setCorrelationId(requestedEvent.getCorrelationId());
        event.setSourceService(SOURCE_SERVICE);
        event.setSequenceNumber(requestedEvent.getSequenceNumber());
        event.setCompanyRuc(requestedEvent.getCompanyRuc());
        event.setSourceAccountNumber(requestedEvent.getSourceAccountNumber());
        event.setCoreFundingId(requestedEvent.getCoreFundingId());
        event.setBeneficiaryIdentification(requestedEvent.getBeneficiaryIdentification());
        event.setBeneficiaryName(requestedEvent.getBeneficiaryName());
        event.setDestinationAccountNumber(requestedEvent.getDestinationAccountNumber());
        event.setRoutingCode(requestedEvent.getRoutingCode());
        event.setDestinationInstitutionName(institution.getInstitutionName());
        event.setAmount(requestedEvent.getAmount());
        event.setCurrency(requestedEvent.getCurrency());
        event.setReference(requestedEvent.getReference());
        event.setNotificationEmail(requestedEvent.getNotificationEmail());
    }

    public BatchLineResponse toResponse(BatchPaymentLine line) {
        BatchLineResponse response = new BatchLineResponse();
        response.setLineId(line.getLineId());
        response.setSequenceNumber(line.getSequenceNumber());
        response.setBeneficiaryIdentification(line.getBeneficiaryIdentification());
        response.setBeneficiaryName(line.getBeneficiaryName());
        response.setDestinationAccountNumber(line.getDestinationAccountNumber());
        response.setRoutingCode(line.getRoutingCode());
        response.setAmount(line.getAmount());
        response.setStatus(line.getStatus());
        response.setEventId(line.getEventId());
        return response;
    }
}
