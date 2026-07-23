package com.banquito.switchpagos.batch.controller;

import com.banquito.switchpagos.batch.dto.response.BatchHistoryPageResponse;
import com.banquito.switchpagos.batch.dto.response.BatchStatusResponse;
import com.banquito.switchpagos.batch.dto.response.UploadBatchResponse;
import com.banquito.switchpagos.batch.exception.BadRequestException;
import com.banquito.switchpagos.batch.exception.GlobalExceptionHandler;
import com.banquito.switchpagos.batch.exception.ResourceNotFoundException;
import com.banquito.switchpagos.batch.service.BatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BatchControllerTest {

    @Mock
    private BatchService batchService;

    private BatchController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new BatchController(batchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== CASOS NEGATIVOS (70%) ====================

    @Test
    void shouldRequireCompanyRucForPublicHttpUpload() {
        // Given
        MultipartFile file = mock(MultipartFile.class);

        // When & Then
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> controller.uploadBatch(file, null, null, "PORTAL_WEB", "frontend-user"));

        assertEquals("COMPANY_RUC_REQUIRED", exception.getCode());
        assertEquals("El RUC de la empresa autenticada es obligatorio para cargar el lote.",
                exception.getMessage());
        verify(batchService, never()).uploadBatch(any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturn400WhenCompanyRucIsMissing() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "batch.csv",
                "text/csv",
                "content".getBytes());

        // When & Then
        mockMvc.perform(multipart("/api/v1/batches/upload")
                        .file(file)
                        .param("channel", "PORTAL_WEB"))
                .andExpect(status().isBadRequest());

        verify(batchService, never()).uploadBatch(any(), any(), any(), any(), any());
    }

    // Nota: Pruebas de validación de parámetros @RequestParam required=true
    // requieren contexto completo de Spring y se prueban mejor en tests de integración

    @Test
    void shouldReturn404WhenBatchNotFound() throws Exception {
        // Given
        UUID batchId = UUID.randomUUID();
        when(batchService.getBatch(batchId))
                .thenThrow(new ResourceNotFoundException("Batch no encontrado"));

        // When & Then
        mockMvc.perform(get("/api/v1/batches/{batchId}", batchId))
                .andExpect(status().isNotFound());
    }

    // Nota: Validación de UUID inválido requiere contexto completo de Spring

    @Test
    void shouldReturn400WhenFileIsNotCSV() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "batch.txt",
                "text/plain",
                "content".getBytes());

        when(batchService.uploadBatch(any(), eq("1792103456001"), any(), any(), any()))
                .thenThrow(new BadRequestException("INVALID_FILE_TYPE", "Solo se aceptan archivos CSV"));

        // When & Then
        mockMvc.perform(multipart("/api/v1/batches/upload")
                        .file(file)
                        .param("companyRuc", "1792103456001")
                        .param("channel", "PORTAL_WEB"))
                .andExpect(status().isBadRequest());
    }

    // ==================== CASOS LÍMITE (20%) ====================

    @Test
    void shouldHandleEmptyCompanyRucParameter() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "batch.csv",
                "text/csv",
                "content".getBytes());

        // When & Then
        mockMvc.perform(multipart("/api/v1/batches/upload")
                        .file(file)
                        .param("companyRuc", "")
                        .param("channel", "PORTAL_WEB"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandlePaginationWithDefaultValues() throws Exception {
        // Given
        BatchHistoryPageResponse response = new BatchHistoryPageResponse();
        response.setContent(List.of());
        response.setTotalElements(0L);
        response.setTotalPages(0);

        when(batchService.listBatches(null, null, null)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ==================== HAPPY PATH (10%) ====================

    @Test
    void shouldUploadBatchSuccessfully() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "batch.csv",
                "text/csv",
                "content".getBytes());

        UploadBatchResponse response = new UploadBatchResponse();
        response.setBatchId(UUID.randomUUID());
        response.setStatus("RECIBIDO");

        when(batchService.uploadBatch(any(), eq("1792103456001"), any(), eq("PORTAL_WEB"), any()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(multipart("/api/v1/batches/upload")
                        .file(file)
                        .param("companyRuc", "1792103456001")
                        .param("channel", "PORTAL_WEB"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECIBIDO"));

        verify(batchService).uploadBatch(any(), eq("1792103456001"), any(), eq("PORTAL_WEB"), any());
    }

    @Test
    void shouldGetBatchStatusSuccessfully() throws Exception {
        // Given
        UUID batchId = UUID.randomUUID();
        BatchStatusResponse response = new BatchStatusResponse();
        response.setBatchId(batchId);
        response.setStatus("PROCESANDO_LINEAS");

        when(batchService.getBatch(batchId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESANDO_LINEAS"));
    }

    @Test
    void shouldListBatchesWithFilters() throws Exception {
        // Given
        BatchHistoryPageResponse response = new BatchHistoryPageResponse();
        response.setContent(List.of());
        response.setTotalElements(5L);

        when(batchService.listBatches("1792103456001", 0, 10)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/batches")
                        .param("companyRuc", "1792103456001")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
    }
}
