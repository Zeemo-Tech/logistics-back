package org.example.logisticsback.contract;

import jakarta.validation.Valid;
import java.util.List;
import org.example.logisticsback.common.ApiResponse;
import org.example.logisticsback.export.ExportFormat;
import org.example.logisticsback.export.ExportResponse;
import org.example.logisticsback.export.ExportWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ContractRecord>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String resource) {
        return ApiResponse.ok(service.list(keyword, category, status, resource));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String resource) {
        var exportFormat = ExportFormat.parse(format);
        var table = service.export(keyword, category, status, resource);
        return ExportResponse.file(ExportWriter.write(table, exportFormat), exportFormat, "合同清单");
    }

    @GetMapping("/{contractId}")
    public ApiResponse<ContractRecord> detail(@PathVariable String contractId) {
        return ApiResponse.ok(service.get(contractId));
    }

    @PostMapping
    public ApiResponse<ContractRecord> create(@Valid @RequestBody ContractRequest input) {
        return ApiResponse.ok(service.create(input), "合同已创建");
    }

    @PutMapping("/{contractId}")
    public ApiResponse<ContractRecord> update(@PathVariable String contractId,
                                              @Valid @RequestBody ContractRequest input) {
        return ApiResponse.ok(service.update(contractId, input), "合同已更新");
    }

    @DeleteMapping("/{contractId}")
    public ApiResponse<Void> delete(@PathVariable String contractId) {
        service.delete(contractId);
        return ApiResponse.ok(null, "合同已删除");
    }
}
