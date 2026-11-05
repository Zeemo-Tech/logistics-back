package org.example.logisticsback.management;

import java.util.List;
import org.example.logisticsback.common.ApiResponse;
import org.example.logisticsback.export.ExportFormat;
import org.example.logisticsback.export.ExportResponse;
import org.example.logisticsback.export.ExportWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management-records")
public class ManagementRecordController {
    private final ManagementRecordService service;

    public ManagementRecordController(ManagementRecordService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ManagementRecord>> list(
            @RequestParam(required = false, defaultValue = "operator-archives") String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(type, keyword, category, region, status));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String format,
            @RequestParam(required = false, defaultValue = "operator-archives") String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status) {
        var exportFormat = ExportFormat.parse(format);
        var table = service.export(type, keyword, category, region, status);
        return ExportResponse.file(ExportWriter.write(table, exportFormat), exportFormat, table.sheetName());
    }
}
