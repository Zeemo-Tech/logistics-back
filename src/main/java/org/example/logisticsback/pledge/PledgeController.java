package org.example.logisticsback.pledge;

import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.example.logisticsback.export.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pledges")
public class PledgeController {
    private final PledgeService service;
    public PledgeController(PledgeService service) { this.service=service; }
    @GetMapping public ApiResponse<Map<String,Object>> workspace(HttpServletRequest r) { return ApiResponse.ok(service.workspace(actor(r))); }
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable String id,HttpServletRequest r) { return ApiResponse.ok(service.detail(id,actor(r))); }
    @PostMapping public ApiResponse<Map<String,Object>> create(@Valid @RequestBody PledgeRequests.Business input,HttpServletRequest r) { return ApiResponse.ok(service.create(input,actor(r))); }
    @DeleteMapping("/{id}") public ApiResponse<Void> remove(@PathVariable String id,@RequestParam int version,HttpServletRequest r) { service.remove(id,version,actor(r));return ApiResponse.ok(null,"监管业务已删除"); }
    @PostMapping("/{id}/commands") public ApiResponse<Map<String,Object>> command(@PathVariable String id,@Valid @RequestBody PledgeRequests.Command input,HttpServletRequest r) { return ApiResponse.ok(service.command(id,input,actor(r))); }
    @PostMapping(value="/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String,Object>> upload(@PathVariable String id,@RequestParam String category,@RequestParam MultipartFile file,HttpServletRequest r) { return ApiResponse.ok(service.upload(id,category,file,actor(r))); }
    @GetMapping("/{id}/documents/{documentId}/file")
    public ResponseEntity<ByteArrayResource> file(@PathVariable String id,@PathVariable String documentId,HttpServletRequest r) {
        var file=service.file(id,documentId,actor(r));var bytes=Base64.getDecoder().decode(file.get("fileBase64").toString());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.get("mimeType").toString()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.get("name").toString(),StandardCharsets.UTF_8).build().toString())
            .contentLength(bytes.length).body(new ByteArrayResource(bytes));
    }
    @GetMapping("/{id}/export") public ResponseEntity<byte[]> export(@PathVariable String id,HttpServletRequest r) {
        var detail=service.detail(id,actor(r));
        @SuppressWarnings("unchecked") var business=(Map<String,Object>)detail.get("business");
        @SuppressWarnings("unchecked") var events=(List<Map<String,Object>>)detail.get("events");
        var rows=new ArrayList<List<String>>();
        rows.add(List.of("业务编号",business.get("code").toString(),"融资人",business.get("borrower").toString(),"银行",business.get("bankName").toString()));
        for (var event:events) rows.add(List.of(event.get("createdAt").toString(),event.get("action").toString(),event.get("operatorName").toString(),event.get("description").toString(),"",""));
        return ExportResponse.file(ExportWriter.write(new ExportTable("监管业务留痕",List.of("时间 / 字段","操作 / 内容","操作人 / 字段","操作说明 / 内容","字段","内容"),rows),ExportFormat.XLSX),ExportFormat.XLSX,"仓单质押监管业务");
    }
    private Long actor(HttpServletRequest r) { return (Long)r.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE); }
}
