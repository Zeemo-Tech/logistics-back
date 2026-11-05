package org.example.logisticsback.contract;

import java.util.List;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/contract-attachments")
public class ContractAttachmentListController {
    private final ContractAttachmentService service;

    public ContractAttachmentListController(ContractAttachmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ContractAttachmentViews.ListItem>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<List<ContractAttachmentViews.Metadata>> upload(
            @RequestParam String contractId, @RequestParam String type,
            @RequestParam String resource, @RequestParam String documentDate,
            @RequestParam String uploader, @RequestParam(defaultValue = "") String remark,
            @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok(service.upload(contractId, type, resource, documentDate, uploader, remark, files));
    }
}
