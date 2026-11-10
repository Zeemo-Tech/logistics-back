package org.example.logisticsback.maintenance;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;

@RestController
@RequestMapping("/api/maintenance/plans")
public class MaintenancePlanController {
    private final MaintenancePlanService service;
    private final PlanTaskService tasks;
    public MaintenancePlanController(MaintenancePlanService service, PlanTaskService tasks) {
        this.service=service; this.tasks=tasks;
    }
    @GetMapping public ApiResponse<List<Plan>> list() { return ApiResponse.ok(service.list()); }
    @GetMapping("/{id}") public ApiResponse<Plan> get(@PathVariable String id) { return ApiResponse.ok(service.get(id)); }
    @GetMapping("/{id}/executions") public ApiResponse<List<Execution>> executions(@PathVariable String id) { return ApiResponse.ok(service.executions(id)); }
    @GetMapping("/{id}/history") public ApiResponse<List<Audit>> history(@PathVariable String id) { return ApiResponse.ok(service.history(id)); }
    @PostMapping public ApiResponse<Plan> create(@Valid @RequestBody PlanInput input,HttpServletRequest r) { return ApiResponse.ok(service.save(null,input,operator(r))); }
    @PutMapping("/{id}") public ApiResponse<Plan> update(@PathVariable String id,@Valid @RequestBody PlanInput input,HttpServletRequest r) { return ApiResponse.ok(service.save(id,input,operator(r))); }
    @PostMapping("/{id}/state") public ApiResponse<Plan> state(@PathVariable String id,@Valid @RequestBody StateInput input,HttpServletRequest r) { return ApiResponse.ok(service.state(id,input,operator(r))); }
    @PostMapping("/{id}/executions") public ApiResponse<Execution> execute(@PathVariable String id,@Valid @RequestBody PlanExecute input,HttpServletRequest r) { return ApiResponse.ok(service.execute(id,input,operator(r))); }
    @PostMapping("/{id}/submit") public ApiResponse<PlanTask> submit(@PathVariable String id,@Valid @RequestBody PlanSubmit input,HttpServletRequest r) { return ApiResponse.ok(tasks.submitPlan(id,input,operator(r)),"已提交"); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable String id,@RequestParam int version,HttpServletRequest r) { service.delete(id,version,operator(r)); return ApiResponse.ok(null); }
    private Long operator(HttpServletRequest r) { return (Long) r.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE); }
}
