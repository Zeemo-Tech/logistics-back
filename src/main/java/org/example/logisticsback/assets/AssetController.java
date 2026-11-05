package org.example.logisticsback.assets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.example.logisticsback.export.ExportFormat;
import org.example.logisticsback.export.ExportResponse;
import org.example.logisticsback.export.ExportWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AssetRecord>> list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String availabilityStatus) {
        return ApiResponse.ok(service.list(new AssetQuery(kind, keyword, category,
                lifecycleStatus, availabilityStatus)));
    }

    @GetMapping("/overview")
    public ApiResponse<AssetViews.Overview> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/ledgers/{kind}")
    public ApiResponse<AssetViews.Page> ledger(
            @PathVariable String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String availabilityStatus) {
        return ApiResponse.ok(service.ledger(new AssetQuery(kind, keyword, category,
                lifecycleStatus, availabilityStatus), page, pageSize));
    }

    @GetMapping("/binding-options")
    public ApiResponse<List<AssetViews.Option>> bindingOptions(
            @RequestParam String kind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String excludeId,
            @RequestParam(required = false) String selectedId) {
        return ApiResponse.ok(service.bindingOptions(kind, keyword, excludeId, selectedId));
    }

    @GetMapping("/{id}/relations")
    public ApiResponse<AssetViews.Relations> relations(@PathVariable String id) {
        return ApiResponse.ok(service.relations(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<AssetRecord> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<AssetEvent>> events(@PathVariable String id) {
        return ApiResponse.ok(service.events(id));
    }

    @PostMapping
    public ApiResponse<AssetRecord> create(@Valid @RequestBody AssetUpsertRequest request,
                                           HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.create(request, operatorId(servletRequest)), "资产已创建");
    }

    @PutMapping("/{id}")
    public ApiResponse<AssetRecord> update(@PathVariable String id,
                                           @Valid @RequestBody AssetUpsertRequest request,
                                           HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.update(id, request, operatorId(servletRequest)), "资产已更新");
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        service.delete(id,operatorId(request)); return ApiResponse.ok(null);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String availabilityStatus) {
        var exportFormat = ExportFormat.parse(format);
        var table = service.export(new AssetQuery(kind, keyword, category,
                lifecycleStatus, availabilityStatus));
        return ExportResponse.file(ExportWriter.write(table, exportFormat), exportFormat, "资产台账");
    }

    private static Long operatorId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
