package org.example.logisticsback.maintenance;

import java.time.LocalDate;
import java.util.List;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance/repair-archive")
public class RepairArchiveController {
    private final RepairArchiveService service;

    public RepairArchiveController(RepairArchiveService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RepairArchiveEntry>> list(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.list(assetId, keyword, from, to));
    }

    @GetMapping("/{workOrderId}")
    public ApiResponse<RepairArchiveEntry> get(@PathVariable String workOrderId) {
        return ApiResponse.ok(service.get(workOrderId));
    }
}
