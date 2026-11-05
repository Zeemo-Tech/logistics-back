package org.example.logisticsback.maintenance;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance/work-orders")
public class WorkOrderController {
    private final WorkOrderService service;
    public WorkOrderController(WorkOrderService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<WorkOrder>> list(@RequestParam(required = false) String assetId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(assetId, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkOrder> get(@PathVariable String id) { return ApiResponse.ok(service.get(id)); }

    @GetMapping("/{id}/events")
    public ApiResponse<List<WorkOrderEvent>> events(@PathVariable String id) { return ApiResponse.ok(service.events(id)); }

    @PostMapping
    public ApiResponse<WorkOrder> create(@Valid @RequestBody WorkOrderRequests.Create input, HttpServletRequest request) {
        return ApiResponse.ok(service.create(input, operator(request)), "报修工单已创建");
    }

    @PostMapping("/{id}/actions")
    public ApiResponse<WorkOrder> action(@PathVariable String id, @Valid @RequestBody WorkOrderRequests.Action input,
                                        HttpServletRequest request) {
        return ApiResponse.ok(service.act(id, input, operator(request)), "工单已更新");
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkOrder> edit(@PathVariable String id,@Valid @RequestBody WorkOrderRequests.Edit input,HttpServletRequest r) {
        return ApiResponse.ok(service.edit(id,input,operator(r)));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,@RequestParam int version,HttpServletRequest r) {
        service.delete(id,version,operator(r));return ApiResponse.ok(null);
    }

    private Long operator(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
