package org.example.logisticsback.rail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import org.example.logisticsback.common.ApiResponse;
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
@RequestMapping("/api/rail")
public class RailController {
    private final RailService service;

    public RailController(RailService service) {
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
        return ApiResponse.ok(service.create(entity, input), "火车运输记录已创建");
    }

    @PutMapping("/{entity}/{id}")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable String entity,
            @PathVariable String id,
            @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.update(entity, id, input), "火车运输记录已更新");
    }

    @DeleteMapping("/{entity}/{id}")
    public ApiResponse<Void> delete(@PathVariable String entity, @PathVariable String id) {
        service.delete(entity, id);
        return ApiResponse.ok(null, "火车运输记录已删除");
    }

    @GetMapping("/attachments")
    public ApiResponse<List<Map<String, Object>>> attachments(
            @RequestParam String entity,
            @RequestParam String recordId) {
        return ApiResponse.ok(service.attachments(entity, recordId));
    }

    @PostMapping("/attachments")
    public ApiResponse<Map<String, Object>> uploadAttachment(
            @RequestParam String entity,
            @RequestParam String recordId,
            @RequestParam(defaultValue = "业务单据") String docType,
            @RequestParam(defaultValue = "") String uploadedBy,
            @RequestParam MultipartFile file) {
        return ApiResponse.ok(service.uploadAttachment(entity, recordId, docType, uploadedBy, file), "单据已上传");
    }

    @GetMapping("/attachments/{id}/file")
    public ResponseEntity<byte[]> attachmentFile(@PathVariable String id) {
        var metadata = service.attachmentMetadata(id);
        var name = URLEncoder.encode(metadata.get("FILE_NAME").toString(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, metadata.get("MIME_TYPE").toString())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
                .body(service.attachmentFile(id));
    }

    @DeleteMapping("/attachments/{id}")
    public ApiResponse<Void> deleteAttachment(@PathVariable String id) {
        service.deleteAttachment(id);
        return ApiResponse.ok(null, "单据已删除");
    }
}
