package com.banquito.switchpagos.batch.service.impl;

import com.banquito.switchpagos.batch.dto.event.PaymentLineRequestedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRejectedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOffUsEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOnUsEvent;
import com.banquito.switchpagos.batch.service.PaymentLineEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RabbitPaymentLineEventPublisher implements PaymentLineEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final String requestedRoutingKey;
    private final String onUsRoutingKey;
    private final String offUsRoutingKey;
    private final String rejectedRoutingKey;

    public RabbitPaymentLineEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbit.exchange.payment-lines}") String exchangeName,
            @Value("${rabbit.routing-key.payment-line-requested}") String requestedRoutingKey,
            @Value("${rabbit.routing-key.payment-line-on-us}") String onUsRoutingKey,
            @Value("${rabbit.routing-key.payment-line-off-us}") String offUsRoutingKey,
            @Value("${rabbit.routing-key.payment-line-rejected}") String rejectedRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.requestedRoutingKey = requestedRoutingKey;
        this.onUsRoutingKey = onUsRoutingKey;
        this.offUsRoutingKey = offUsRoutingKey;
        this.rejectedRoutingKey = rejectedRoutingKey;
    }

    @Override
    public void publishRequested(PaymentLineRequestedEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, requestedRoutingKey, event);
    }

    @Override
    public void publishOnUs(PaymentLineRoutedOnUsEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, onUsRoutingKey, event);
    }

    @Override
    public void publishOffUs(PaymentLineRoutedOffUsEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, offUsRoutingKey, event);
    }

    @Override
    public void publishRejected(PaymentLineRejectedEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, rejectedRoutingKey, event);
    }
}
