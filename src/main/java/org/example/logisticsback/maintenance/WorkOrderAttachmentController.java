package org.example.logisticsback.maintenance;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/maintenance/work-orders/{workOrderId}/attachments")
public class WorkOrderAttachmentController {
    private final WorkOrderAttachmentService service;

    public WorkOrderAttachmentController(WorkOrderAttachmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WorkOrderAttachment>> list(@PathVariable String workOrderId) {
        return ApiResponse.ok(service.list(workOrderId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<WorkOrderAttachment>> upload(@PathVariable String workOrderId,
            @RequestParam String category,
            @RequestParam(defaultValue = "") String remark,
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {
        return ApiResponse.ok(service.upload(workOrderId, category, remark, files, operator(request)), "附件已上传");
    }

    @GetMapping("/{attachmentId}/file")
    public ResponseEntity<ByteArrayResource> file(@PathVariable String workOrderId,
                                                  @PathVariable String attachmentId) {
        var stored = service.file(workOrderId, attachmentId);
        var bytes = Base64.getDecoder().decode(stored.base64() == null ? "" : stored.base64());
        var metadata = stored.metadata();
        var disposition = ContentDisposition.attachment().filename(metadata.name()).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(metadata.mimeType()))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(@PathVariable String workOrderId, @PathVariable String attachmentId) {
        service.delete(workOrderId, attachmentId);
        return ApiResponse.ok(null, "附件已删除");
    }

    private static Long operator(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
