package org.example.logisticsback.maintenance;

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
import static org.example.logisticsback.maintenance.MaintenanceModels.*;

@RestController
@RequestMapping("/api/maintenance/plan-tasks")
public class PlanTaskController {
    private final PlanTaskService service;

    public PlanTaskController(PlanTaskService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<PlanTask>> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanTask> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/generate")
    public ApiResponse<Integer> generate() {
        var count = service.generateDueTasks();
        return ApiResponse.ok(count, "已生成 " + count + " 条到期维保任务");
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<PlanTask> confirm(@PathVariable String id,
            @Valid @RequestBody PlanTaskConfirm input, HttpServletRequest request) {
        return ApiResponse.ok(service.confirm(id, input, operator(request)), "维保任务已确认");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<PlanTask> cancel(@PathVariable String id,
            @Valid @RequestBody PlanTaskCancel input, HttpServletRequest request) {
        return ApiResponse.ok(service.cancel(id, input, operator(request)), "维保任务已取消");
    }

    private static Long operator(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
