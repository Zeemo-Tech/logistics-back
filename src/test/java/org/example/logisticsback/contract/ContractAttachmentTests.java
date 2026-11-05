package org.example.logisticsback.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ContractAttachmentTests {
    @Autowired private ContractAttachmentService service;
    @Autowired private ContractAttachmentController controller;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void listsOnlyAttachmentsOfRequestedContractAndHandlesEmptyContracts() {
        var attachments = service.list("HT-2026-018");
        assertEquals(1, attachments.size());
        var attachment = attachments.get(0);
        assertEquals("果蔬冷库入库单_0815.xlsx", attachment.name());
        assertEquals("HT-2026-018", attachment.contractId());
        assertEquals("SPREADSHEET", attachment.format());
        assertTrue(attachment.demo());
        assertTrue(service.list("HT-2026-014").isEmpty());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.list("missing")).getCode());
    }

    @Test
    void spreadsheetPreviewReadsPersistedCellsWithCorrectInventoryTotal() {
        var preview = service.preview("HT-2026-018", "DJ-20260816-007");
        assertEquals("SPREADSHEET", preview.format());
        assertTrue(preview.demo());
        var sheet = preview.sheets().get(0);
        assertEquals("入库明细", sheet.name());
        assertEquals(6, sheet.columns().size());
        assertEquals(4, sheet.rows().size());
        assertEquals("小青柠", sheet.rows().get(0).get(1));
        assertEquals(new BigDecimal("18.0"), sheet.rows().stream().map(row -> new BigDecimal(row.get(2))).reduce(BigDecimal.ZERO, BigDecimal::add));
        var workbook = service.file("HT-2026-018", "DJ-20260816-007");
        assertEquals('P', workbook.bytes()[0]);
        assertEquals('K', workbook.bytes()[1]);
    }

    @Test
    void pdfAndImageFilesHaveActualBytesAndCorrectResponseHeaders() {
        var response = controller.file("HT-2026-017", "DJ-20260815-005");
        assertEquals("application/pdf", response.getHeaders().getFirst("Content-Type"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("inline; filename*=UTF-8''"));
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        var pdf = response.getBody();
        assertNotNull(pdf);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(pdf.length > 1000);
        assertEquals("%PDF-", new String(service.file("HT-2026-016", "DJ-20260816-008").bytes(), 0, 5, StandardCharsets.US_ASCII));
        var image = service.file("HT-2026-015", "DJ-20260815-006");
        assertEquals("image/jpeg", image.mimeType());
        assertEquals(0xff, image.bytes()[0] & 0xff);
        assertEquals(0xd8, image.bytes()[1] & 0xff);
        assertTrue(service.preview("HT-2026-017", "DJ-20260815-005").sheets().isEmpty());
    }

    @Test
    void attachmentAccessAlwaysChecksItsContract() {
        assertEquals(404, assertThrows(BusinessException.class, () -> service.preview("HT-2026-018", "DJ-20260815-005")).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.file("HT-2026-018", "DJ-20260815-005")).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.preview("HT-2026-017", "missing")).getCode());
    }

    @Test
    void invalidOrMissingStoredContentReturnsUsefulErrors() {
        jdbc.update("update contract_attachments set preview_json = null where id = 'DJ-20260816-007'");
        assertEquals(404, assertThrows(BusinessException.class, () -> service.preview("HT-2026-018", "DJ-20260816-007")).getCode());
        jdbc.update("update contract_attachments set preview_json = 'invalid-json' where id = 'DJ-20260816-007'");
        assertEquals(500, assertThrows(BusinessException.class, () -> service.preview("HT-2026-018", "DJ-20260816-007")).getCode());
        jdbc.update("update contract_attachments set file_base64 = 'invalid!' where id = 'DJ-20260815-005'");
        assertEquals(500, assertThrows(BusinessException.class, () -> service.file("HT-2026-017", "DJ-20260815-005")).getCode());
    }
}
