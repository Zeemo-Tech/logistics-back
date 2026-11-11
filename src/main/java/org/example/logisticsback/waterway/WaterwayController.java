package org.example.logisticsback.waterway;

import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.example.logisticsback.common.ApiResponse;
import org.example.logisticsback.export.ExportFormat;
import org.example.logisticsback.export.ExportResponse;
import org.example.logisticsback.export.ExportTable;
import org.example.logisticsback.export.ExportWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waterway")
public class WaterwayController {
    private final WaterwayService service;

    public WaterwayController(WaterwayService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/{entity}")
    public ApiResponse<List<Map<String, Object>>> list(
            @PathVariable String entity,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(entity, keyword));
    }

    @PostMapping("/{entity}")
    public ApiResponse<Map<String, Object>> create(
            @PathVariable String entity,
            @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.create(entity, input), "水路运输记录已创建");
    }

    @PutMapping("/{entity}/{id}")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable String entity,
            @PathVariable String id,
            @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.update(entity, id, input), "水路运输记录已更新");
    }

    @PostMapping("/bookings/{id}/release")
    public ApiResponse<Map<String, Object>> releaseBooking(
            @PathVariable String id,
            @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.releaseBooking(id, input), "未到港舱位已释放");
    }

    @DeleteMapping("/{entity}/{id}")
    public ApiResponse<Void> delete(@PathVariable String entity, @PathVariable String id) {
        service.delete(entity, id);
        return ApiResponse.ok(null, "水路运输记录已删除");
    }

    @GetMapping("/voyages/{voyageId}/documents")
    public ApiResponse<List<Map<String, Object>>> voyageDocuments(@PathVariable String voyageId) {
        return ApiResponse.ok(service.voyageDocuments(voyageId));
    }

    @PostMapping("/voyages/{voyageId}/documents")
    public ApiResponse<Map<String, Object>> uploadVoyageDocument(
            @PathVariable String voyageId,
            @RequestParam String documentType,
            @RequestParam(defaultValue = "") String uploadedBy,
            @RequestParam MultipartFile file) {
        return ApiResponse.ok(service.uploadVoyageDocument(voyageId, documentType, uploadedBy, file));
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> voyageDocumentFile(@PathVariable String id) {
        var metadata = service.voyageDocumentMetadata(id);
        var name = URLEncoder.encode(metadata.get("FILE_NAME").toString(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, metadata.get("MIME_TYPE").toString())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
                .body(service.voyageDocumentFile(id));
    }

    @PostMapping("/import/{entity}")
    public ApiResponse<Map<String, Object>> importSpreadsheet(@PathVariable String entity, @RequestParam MultipartFile file) {
        return ApiResponse.ok(service.importSpreadsheet(entity, file));
    }

    @GetMapping("/export/{entity}")
    public ResponseEntity<byte[]> export(@PathVariable String entity, @RequestParam(defaultValue = "xlsx") String format) {
        var headers = switch (entity) {
            case "voyages" -> List.of("航次号", "船舶", "方向", "起运港", "目的港", "计划发船", "已订舱", "实际装船", "容量", "状态");
            case "containers" -> List.of("柜号", "客户", "航次", "货物", "重量", "提单号", "车牌", "进港状态", "审核状态", "运输状态");
            case "vehicles" -> List.of("车牌", "挂车", "司机", "电话", "柜号", "航次", "预计进港", "实际进港", "进港状态");
            case "loading" -> List.of("航次", "柜号", "车牌", "排队序号", "计划吊柜", "改期", "延期原因", "吊柜完成", "装船时间", "状态");
            case "returnPlans" -> List.of("计划编号", "目标航次", "计划日期", "计划数量", "已补柜号", "待补数量", "港口", "状态");
            case "emptyReturns" -> List.of("柜号", "客户", "当前港口", "目标航次", "状态", "确认时间");
            case "checkpoints" -> List.of("航次", "时间", "港口", "柜号", "客户", "货物", "设置温度", "送风温度", "回风温度", "经度", "纬度", "位置");
            case "fees" -> List.of("费用编号", "航次", "柜号", "项目", "金额", "币种", "付款方", "收款方", "发生时间", "票据号", "状态");
            default -> throw new org.example.logisticsback.common.BusinessException(404, "该台账暂不支持导出");
        };
        var formatValue = ExportFormat.parse(format);
        var records = service.list(entity, null);
        var keys = switch (entity) {
            case "voyages" -> List.of("voyageNo", "vesselName", "direction", "originPort", "destinationPort", "plannedDeparture", "bookedCount", "loadedCount", "availableCapacity", "status");
            case "containers" -> List.of("containerNo", "customerName", "voyageNo", "cargo", "weight", "billOfLading", "truckPlate", "gateStatus", "reviewStatus", "transportStatus");
            case "vehicles" -> List.of("plate", "trailer", "driver", "phone", "containerNo", "voyageNo", "expectedArrival", "actualGateIn", "gateStatus");
            case "loading" -> List.of("voyageNo", "containerNo", "plate", "queueNo", "plannedAt", "rescheduledAt", "delayReason", "completedAt", "loadedAt", "status");
            case "returnPlans" -> List.of("code", "returnVoyageNo", "plannedDate", "plannedCount", "assignedCount", "pendingCount", "currentPort", "status");
            case "emptyReturns" -> List.of("containerNo", "customerName", "currentPort", "returnVoyageNo", "status", "confirmedAt");
            case "checkpoints" -> List.of("voyageNo", "occurredAt", "portName", "containerNo", "customerName", "product", "setTemperature", "supplyAir", "returnAir", "longitude", "latitude", "location");
            case "fees" -> List.of("code", "voyageNo", "containerNo", "category", "amount", "currency", "payer", "payee", "incurredAt", "invoiceNo", "status");
            default -> List.of();
        };
        var rows = records.stream().map(record -> keys.stream().map(key -> String.valueOf(record.getOrDefault(key, ""))).toList()).toList();
        var file = ExportResponse.file(ExportWriter.write(new ExportTable("水路运输台账", headers, rows), formatValue), formatValue, "水路运输台账");
        return file;
    }
}
