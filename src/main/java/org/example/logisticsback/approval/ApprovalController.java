package org.example.logisticsback.approval;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance/approvals")
public class ApprovalController {
    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MaintenanceApproval>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(status, businessType, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaintenanceApproval> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/records")
    public ApiResponse<List<MaintenanceApprovalRecord>> records(@PathVariable String id) {
        return ApiResponse.ok(service.records(id));
    }

    @PostMapping
    public ApiResponse<MaintenanceApproval> create(@Valid @RequestBody ApprovalRequests.Create input,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(service.create(input, operator(request)), "审批已提交");
    }

    @PostMapping("/{id}/actions")
    public ApiResponse<MaintenanceApproval> action(@PathVariable String id,
            @Valid @RequestBody ApprovalRequests.Action input, HttpServletRequest request) {
        return ApiResponse.ok(service.act(id, input, operator(request)), "审批已处理");
    }

    private static Long operator(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
