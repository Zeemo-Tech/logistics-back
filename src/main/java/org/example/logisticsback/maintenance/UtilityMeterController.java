package org.example.logisticsback.maintenance;

import java.util.Base64;
import java.util.List;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.logisticsback.auth.AuthInterceptor;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;

@RestController
@RequestMapping("/api/maintenance/meters")
public class UtilityMeterController {
    private final UtilityMeterService service;

    public UtilityMeterController(UtilityMeterService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<UtilityMeter>> list() {
        return ApiResponse.ok(service.list());
    }
    @GetMapping("/{id}") public ApiResponse<UtilityMeter> get(@PathVariable String id) { return ApiResponse.ok(service.get(id)); }
    @GetMapping("/{id}/readings") public ApiResponse<List<Reading>> readings(@PathVariable String id) { return ApiResponse.ok(service.readings(id)); }
    @GetMapping("/{id}/history") public ApiResponse<List<Audit>> history(@PathVariable String id) { return ApiResponse.ok(service.history(id)); }
    @GetMapping("/{id}/readings/{readingId}/photo") public ResponseEntity<ByteArrayResource> photo(@PathVariable String id, @PathVariable String readingId) {
        var photo = service.readingPhoto(id, readingId);
        var bytes = Base64.getDecoder().decode(photo.base64() == null ? "" : photo.base64());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(photo.name()).build().toString())
                .contentType(MediaType.parseMediaType(photo.mimeType() == null || photo.mimeType().isBlank() ? "application/octet-stream" : photo.mimeType()))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
    @PostMapping public ApiResponse<UtilityMeter> create(@Valid @RequestBody MeterInput input,HttpServletRequest r) { return ApiResponse.ok(service.save(null,input,operator(r))); }
    @PutMapping("/{id}") public ApiResponse<UtilityMeter> update(@PathVariable String id,@Valid @RequestBody MeterInput input,HttpServletRequest r) { return ApiResponse.ok(service.save(id,input,operator(r))); }
    @PostMapping("/{id}/state") public ApiResponse<UtilityMeter> state(@PathVariable String id,@Valid @RequestBody StateInput input,HttpServletRequest r) { return ApiResponse.ok(service.state(id,input,operator(r))); }
    @PostMapping("/{id}/readings") public ApiResponse<Reading> read(@PathVariable String id,@Valid @RequestBody ReadingInput input,HttpServletRequest r) { return ApiResponse.ok(service.record(id,null,input,operator(r))); }
    @PutMapping("/{id}/readings/{readingId}") public ApiResponse<Reading> correct(@PathVariable String id,@PathVariable String readingId,@Valid @RequestBody ReadingInput input,HttpServletRequest r) { return ApiResponse.ok(service.record(id,readingId,input,operator(r))); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable String id,@RequestParam int version,HttpServletRequest r) { service.delete(id,version,operator(r)); return ApiResponse.ok(null); }
    private Long operator(HttpServletRequest r) { return (Long) r.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE); }
}
