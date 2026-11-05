package org.example.logisticsback.contract;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.export.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ContractAttachmentListTests {
    @Autowired ContractAttachmentService service;
    @Autowired ContractService contracts;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void listsDatabaseAttachmentsWithContractNames() {
        var list = service.listAll();
        assertEquals(4, list.size());
        var invoice = list.stream().filter(item -> item.id().equals("DJ-20260815-005")).findFirst().orElseThrow();
        assertEquals("综合冷库分区租赁合同", invoice.contractName());
        assertEquals("HT-2026-017", invoice.contractId());
    }

    @Test
    void deletionUpdatesAllReadEndpointsAndContractCountsButChecksAssociationFirst() {
        assertEquals(404, assertThrows(BusinessException.class, () -> service.delete("HT-2026-018", "DJ-20260815-005")).getCode());
        assertEquals(4, service.listAll().size());
        assertEquals(1, contracts.get("HT-2026-017").documentCount());
        service.delete("HT-2026-017", "DJ-20260815-005");
        assertEquals(3, service.listAll().size());
        assertTrue(service.list("HT-2026-017").isEmpty());
        assertEquals(0, contracts.get("HT-2026-017").documentCount());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.preview("HT-2026-017", "DJ-20260815-005")).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.file("HT-2026-017", "DJ-20260815-005")).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.delete("HT-2026-017", "DJ-20260815-005")).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> contracts.get("missing")).getCode());
    }

    @Test
    void uploadedSpreadsheetCanBeListedPreviewedAndDownloaded() throws Exception {
        byte[] bytes = ExportWriter.write(new ExportTable("入库单", List.of("商品", "吨数"), List.of(List.of("香蕉", "5.2"))), ExportFormat.XLSX);
        var file = new MockMultipartFile("files", "入库单.xlsx", "application/octet-stream", bytes);
        var result = service.upload("HT-2026-018", "入库单", "百川果蔬冷库", "2026-11-03", "测试员", "联调备注", List.of(file));
        var uploaded = result.get(0);
        assertFalse(uploaded.demo());
        assertEquals("联调备注", jdbc.queryForObject("select remark from contract_attachments where id = ?", String.class, uploaded.id()));
        assertEquals(5, service.listAll().size());
        assertEquals(2, contracts.get("HT-2026-018").documentCount());
        assertEquals("香蕉", service.preview(uploaded.contractId(), uploaded.id()).sheets().get(0).rows().get(0).get(0));
        assertArrayEquals(bytes, service.file(uploaded.contractId(), uploaded.id()).bytes());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.file("HT-2026-018", "DJ-20260816-007").bytes()))) {
            assertEquals("小青柠", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void spreadsheetPreviewPreservesColumnsBeyondTheTitleRow() throws Exception {
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                var output = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("入库明细");
            sheet.createRow(0).createCell(0).setCellValue("果蔬入库单");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("商品");
            row.createCell(1).setCellValue("吨数");
            row.createCell(2).setCellValue("批次");
            workbook.write(output);
            var file = new MockMultipartFile("files", "入库单.xlsx", "application/octet-stream", output.toByteArray());
            var item = service.upload("HT-2026-018", "入库单", "百川果蔬冷库", "2026-11-03", "测试员", "", List.of(file)).get(0);
            var preview = service.preview(item.contractId(), item.id()).sheets().get(0);
            assertEquals(3, preview.columns().size());
            assertEquals("批次", preview.rows().get(0).get(2));
        }
    }

    @Test
    void invalidFilesAndMetadataAreRejected() {
        byte[] bytes = service.file("HT-2026-017", "DJ-20260815-005").bytes();
        var valid = new MockMultipartFile("files", "发票.pdf", "application/pdf", bytes);
        var invalid = new MockMultipartFile("files", "损坏.pdf", "application/pdf", new byte[]{1, 2, 3});
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload("HT-2026-017", "发票", "百川综合冷库", "2026-11-03", "测试员", "联调备注", List.of(invalid, valid))).getCode());
        assertEquals(4, service.listAll().size());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload("HT-2026-017", "发票", "百川综合冷库", "invalid", "测试员", "联调备注", List.of(valid))).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload("HT-2026-017", "发票", "百川综合冷库", "2026-11-03", "测试员", "联调备注", List.of())).getCode());
    }
}
