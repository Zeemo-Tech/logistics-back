package org.example.logisticsback.contract;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/contracts/{contractId}/attachments")
public class ContractAttachmentController {
    private final ContractAttachmentService service;

    public ContractAttachmentController(ContractAttachmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ContractAttachmentViews.Metadata>> list(@PathVariable String contractId) {
        return ApiResponse.ok(service.list(contractId));
    }

    @GetMapping("/{attachmentId}/preview")
    public ApiResponse<ContractAttachmentViews.Preview> preview(@PathVariable String contractId, @PathVariable String attachmentId) {
        return ApiResponse.ok(service.preview(contractId, attachmentId));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(@PathVariable String contractId, @PathVariable String attachmentId) {
        service.delete(contractId, attachmentId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{attachmentId}/file")
    public ResponseEntity<byte[]> file(@PathVariable String contractId, @PathVariable String attachmentId) {
        var file = service.file(contractId, attachmentId);
        var encoded = URLEncoder.encode(file.name(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, file.mimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }
}
