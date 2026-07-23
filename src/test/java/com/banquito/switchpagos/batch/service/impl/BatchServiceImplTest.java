package com.banquito.switchpagos.batch.service.impl;

import com.banquito.switchpagos.batch.dto.request.ParsedBatchFile;
import com.banquito.switchpagos.batch.dto.response.*;
import com.banquito.switchpagos.batch.exception.BadRequestException;
import com.banquito.switchpagos.batch.exception.ResourceNotFoundException;
import com.banquito.switchpagos.batch.mapper.BatchFileParser;
import com.banquito.switchpagos.batch.mapper.BatchMapper;
import com.banquito.switchpagos.batch.mapper.PaymentLineMapper;
import com.banquito.switchpagos.batch.mapper.ValidationErrorMapper;
import com.banquito.switchpagos.batch.model.PaymentBatch;
import com.banquito.switchpagos.batch.repository.BatchPaymentLineRepository;
import com.banquito.switchpagos.batch.repository.BatchValidationErrorRepository;
import com.banquito.switchpagos.batch.repository.PaymentBatchRepository;
import com.banquito.switchpagos.batch.repository.UploadedFileRepository;
import com.banquito.switchpagos.batch.service.BatchProcessingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceImplTest {

    @Mock
    private BatchFileParser batchFileParser;

    @Mock
    private PaymentBatchRepository paymentBatchRepository;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private BatchValidationErrorRepository validationErrorRepository;

    @Mock
    private BatchPaymentLineRepository paymentLineRepository;

    @Mock
    private BatchProcessingService batchProcessingService;

    @Mock
    private BatchMapper batchMapper;

    @Mock
    private ValidationErrorMapper validationErrorMapper;

    @Mock
    private PaymentLineMapper paymentLineMapper;

    private BatchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BatchServiceImpl(
                batchFileParser,
                paymentBatchRepository,
                uploadedFileRepository,
                validationErrorRepository,
                paymentLineRepository,
                batchProcessingService,
                batchMapper,
                validationErrorMapper,
                paymentLineMapper
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== CASOS NEGATIVOS (70%) ====================

    @Test
    void shouldRejectNullFile() {
        // When & Then
        assertThatThrownBy(() -> service.uploadBatch(null, "1792103456001", null, "WEB", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archivo de lote es obligatorio");
    }

    @Test
    void shouldRejectEmptyFile() {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "batch.csv", "text/csv", new byte[0]);

        // When & Then
        assertThatThrownBy(() -> service.uploadBatch(emptyFile, "1792103456001", null, "WEB", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archivo de lote es obligatorio");
    }

    @Test
    void shouldRejectNonCsvFile() {
        // Given
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "batch.txt", "text/plain", "content".getBytes());

        // When & Then
        assertThatThrownBy(() -> service.uploadBatch(txtFile, "1792103456001", null, "WEB", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("extension .csv");
    }

    @Test
    void shouldRejectFileWithInvalidName() {
        // Given
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "..", "text/csv", "content".getBytes());

        // When & Then
        assertThatThrownBy(() -> service.uploadBatch(invalidFile, "1792103456001", null, "WEB", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nombre del archivo no es valido");
    }

    @Test
    void shouldRejectWhenCompanyRucMismatch() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file", "batch.csv", "text/csv", "content".getBytes());
        ParsedBatchFile parsedFile = createParsedBatchFile();
        parsedFile.setCompanyRuc("9999999999999");

        when(batchFileParser.parse(any())).thenReturn(parsedFile);

        // When & Then
        assertThatThrownBy(() -> service.uploadBatch(file, "1792103456001", null, "WEB", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("RUC del archivo no coincide");
    }

    @Test
    void shouldThrowNotFoundWhenBatchDoesNotExist() {
        UUID batchId = UUID.randomUUID();
        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBatch(batchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No existe el lote");
    }

    @Test
    void shouldThrowNotFoundWhenStateHistoryBatchDoesNotExist() {
        UUID batchId = UUID.randomUUID();
        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStateHistory(batchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No existe el lote");
    }

    @Test
    void shouldThrowNotFoundWhenValidationErrorsBatchDoesNotExist() {
        UUID batchId = UUID.randomUUID();
        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getValidationErrors(batchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No existe el lote");
    }

    @Test
    void shouldThrowNotFoundWhenLinesBatchDoesNotExist() {
        UUID batchId = UUID.randomUUID();
        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLines(batchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No existe el lote");
    }

    // ==================== CASOS LÍMITE (20%) ====================

    @Test
    void shouldNormalizePaginationParameters() {
        Page<PaymentBatch> emptyPage = new PageImpl<>(List.of());
        when(paymentBatchRepository.findAllByOrderByReceivedAtDesc(any(Pageable.class))).thenReturn(emptyPage);

        BatchHistoryPageResponse response = service.listBatches(null, -1, -10);

        assertThat(response).isNotNull();
        verify(paymentBatchRepository).findAllByOrderByReceivedAtDesc(
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 10));
    }

    @Test
    void shouldLimitMaxPageSize() {
        Page<PaymentBatch> emptyPage = new PageImpl<>(List.of());
        when(paymentBatchRepository.findAllByOrderByReceivedAtDesc(any(Pageable.class))).thenReturn(emptyPage);

        service.listBatches(null, 0, 1000);

        verify(paymentBatchRepository).findAllByOrderByReceivedAtDesc(
                argThat(p -> p.getPageSize() == 100));
    }

    @Test
    void shouldUseDefaultSizeWhenNull() {
        Page<PaymentBatch> emptyPage = new PageImpl<>(List.of());
        when(paymentBatchRepository.findAllByOrderByReceivedAtDesc(any(Pageable.class))).thenReturn(emptyPage);

        service.listBatches(null, 0, null);

        verify(paymentBatchRepository).findAllByOrderByReceivedAtDesc(
                argThat(p -> p.getPageSize() == 10));
    }

    @Test
    void shouldGetStateHistoryWithOnlyReceivedStatus() {
        UUID batchId = UUID.randomUUID();
        PaymentBatch batch = createPaymentBatch();
        batch.setBatchId(batchId);
        batch.setStatus("RECIBIDO");
        batch.setValidatedAt(null);
        batch.setFundedAt(null);

        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        BatchStateHistoryResponse response = service.getStateHistory(batchId);

        assertThat(response.getHistory()).hasSize(1);
        assertThat(response.getHistory().get(0).getNewStatus()).isEqualTo("RECIBIDO");
    }

    @Test
    void shouldGetStateHistoryWithValidatedAndFunded() {
        UUID batchId = UUID.randomUUID();
        PaymentBatch batch = createPaymentBatch();
        batch.setBatchId(batchId);
        batch.setStatus("FONDEADO");
        batch.setValidatedAt(OffsetDateTime.now().minusHours(1));
        batch.setFundedAt(OffsetDateTime.now());

        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        BatchStateHistoryResponse response = service.getStateHistory(batchId);

        assertThat(response.getHistory()).hasSize(3);
        assertThat(response.getHistory().get(0).getNewStatus()).isEqualTo("RECIBIDO");
        assertThat(response.getHistory().get(1).getNewStatus()).isEqualTo("VALIDADO");
        assertThat(response.getHistory().get(2).getNewStatus()).isEqualTo("FONDEADO");
    }

    @Test
    void shouldGetStateHistoryWithRejectionReason() {
        UUID batchId = UUID.randomUUID();
        PaymentBatch batch = createPaymentBatch();
        batch.setBatchId(batchId);
        batch.setStatus("RECHAZADO");
        batch.setRejectionReason("RUC invalido");
        batch.setValidatedAt(OffsetDateTime.now().minusHours(1));

        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        BatchStateHistoryResponse response = service.getStateHistory(batchId);

        assertThat(response.getHistory()).hasSize(3);
        assertThat(response.getHistory().get(2).getReason()).isEqualTo("RUC invalido");
    }

    @Test
    void shouldGetValidationErrorsWithEmptyList() {
        UUID batchId = UUID.randomUUID();
        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.of(createPaymentBatch()));
        when(validationErrorRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of());

        BatchValidationErrorsResponse response = service.getValidationErrors(batchId);

        assertThat(response.getBatchId()).isEqualTo(batchId);
        assertThat(response.getErrors()).isEmpty();
    }

    @Test
    void shouldGetLinesWithEmptyList() {
        UUID batchId = UUID.randomUUID();
        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.of(createPaymentBatch()));
        when(paymentLineRepository.findByBatchIdOrderBySequenceNumberAsc(batchId)).thenReturn(List.of());

        BatchLinesResponse response = service.getLines(batchId);

        assertThat(response.getBatchId()).isEqualTo(batchId);
        assertThat(response.getLines()).isEmpty();
    }

    // ==================== HAPPY PATH (10%) ====================

    @Test
    void shouldUploadBatchSuccessfully() {
        MockMultipartFile file = new MockMultipartFile("file", "batch.csv", "text/csv", "content".getBytes());
        ParsedBatchFile parsedFile = createParsedBatchFile();
        when(batchFileParser.parse(any())).thenReturn(parsedFile);
        when(batchMapper.toUploadResponse(any())).thenReturn(new UploadBatchResponse());

        UploadBatchResponse response = service.uploadBatch(file, null, "cust-123", "web", "user1");

        assertThat(response).isNotNull();
        verify(paymentBatchRepository).save(any());
        verify(uploadedFileRepository).save(any());
    }

    @Test
    void shouldListBatchesWithRucFilter() {
        PaymentBatch batch = createPaymentBatch();
        Page<PaymentBatch> page = new PageImpl<>(List.of(batch));
        when(paymentBatchRepository.findByCompanyRucOrderByReceivedAtDesc(eq("1792103456001"), any())).thenReturn(page);

        BatchHistoryPageResponse response = service.listBatches("1792103456001", 0, 10);

        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(paymentBatchRepository).findByCompanyRucOrderByReceivedAtDesc(eq("1792103456001"), any());
    }

    @Test
    void shouldListBatchesWithoutFilter() {
        PaymentBatch batch = createPaymentBatch();
        Page<PaymentBatch> page = new PageImpl<>(List.of(batch));
        when(paymentBatchRepository.findAllByOrderByReceivedAtDesc(any(Pageable.class))).thenReturn(page);

        BatchHistoryPageResponse response = service.listBatches(null, 0, 10);

        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(paymentBatchRepository).findAllByOrderByReceivedAtDesc(any());
    }

    @Test
    void shouldGetBatchById() {
        // Given
        UUID batchId = UUID.randomUUID();
        PaymentBatch batch = createPaymentBatch();
        batch.setBatchId(batchId);
        BatchStatusResponse expectedResponse = new BatchStatusResponse();
        expectedResponse.setBatchId(batchId);

        when(paymentBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchMapper.toStatusResponse(batch)).thenReturn(expectedResponse);

        // When
        BatchStatusResponse response = service.getBatch(batchId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getBatchId()).isEqualTo(batchId);
    }

    // ==================== HELPER METHODS ====================

    private ParsedBatchFile createParsedBatchFile() {
        ParsedBatchFile parsedFile = new ParsedBatchFile();
        parsedFile.setCompanyRuc("1792103456001");
        parsedFile.setSourceAccountNumber("0010000010599");
        parsedFile.setServiceType("PAGOS_MASIVOS");
        parsedFile.setHeaderTotalRecords(10);
        parsedFile.setHeaderControlAmount(new BigDecimal("1000.00"));
        parsedFile.setSecurityHash("abc123hash");
        return parsedFile;
    }

    private PaymentBatch createPaymentBatch() {
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchId(UUID.randomUUID());
        batch.setCorrelationId(UUID.randomUUID());
        batch.setCompanyRuc("1792103456001");
        batch.setSourceAccountNumber("0010000010599");
        batch.setFileName("batch.csv");
        batch.setStatus("RECIBIDO");
        batch.setTotalRecords(10);
        batch.setControlAmount(new BigDecimal("1000.00"));
        batch.setChannel("WEB");
        batch.setReceivedAt(OffsetDateTime.now());
        batch.setCreatedAt(OffsetDateTime.now());
        batch.setUpdatedAt(OffsetDateTime.now());
        return batch;
    }
}
