package org.example.logisticsback.coldstorage;

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
@RequestMapping("/api/cold-storage-archives")
public class ColdStorageArchiveController {
    private final ColdStorageArchiveService service;

    public ColdStorageArchiveController(ColdStorageArchiveService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ColdStorageArchive>> list(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String storageType,
            @RequestParam(required = false) Boolean hasDevice,
            @RequestParam(required = false) String grade) {
        return ApiResponse.ok(service.list(operator, name, storageType, hasDevice, grade));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String storageType,
            @RequestParam(required = false) Boolean hasDevice,
            @RequestParam(required = false) String grade) {
        var exportFormat = ExportFormat.parse(format);
        var table = service.export(operator, name, storageType, hasDevice, grade);
        return ExportResponse.file(ExportWriter.write(table, exportFormat), exportFormat, "冷库档案");
    }
}
