package com.banquito.switchpagos.batch.service;

import com.banquito.switchpagos.batch.dto.event.PaymentLineRequestedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRejectedEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOffUsEvent;
import com.banquito.switchpagos.batch.dto.event.PaymentLineRoutedOnUsEvent;

public interface PaymentLineEventPublisher {

    void publishRequested(PaymentLineRequestedEvent event);

    void publishOnUs(PaymentLineRoutedOnUsEvent event);

    void publishOffUs(PaymentLineRoutedOffUsEvent event);

    void publishRejected(PaymentLineRejectedEvent event);
}
