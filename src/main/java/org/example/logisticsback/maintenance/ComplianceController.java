package org.example.logisticsback.maintenance;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;

@RestController
@RequestMapping("/api/maintenance/compliance")
public class ComplianceController {
    private final ComplianceService service;
    public ComplianceController(ComplianceService service) { this.service=service; }
    @GetMapping public ApiResponse<List<Compliance>> list() { return ApiResponse.ok(service.list()); }
    @GetMapping("/{id}") public ApiResponse<Compliance> get(@PathVariable String id) { return ApiResponse.ok(service.get(id)); }
    @GetMapping("/{id}/events") public ApiResponse<List<ComplianceEvent>> events(@PathVariable String id) { return ApiResponse.ok(service.events(id)); }
    @GetMapping("/{id}/history") public ApiResponse<List<Audit>> history(@PathVariable String id) { return ApiResponse.ok(service.history(id)); }
    @PostMapping public ApiResponse<Compliance> create(@Valid @RequestBody ComplianceInput input,HttpServletRequest r) { return ApiResponse.ok(service.save(null,input,operator(r))); }
    @PutMapping("/{id}") public ApiResponse<Compliance> update(@PathVariable String id,@Valid @RequestBody ComplianceInput input,HttpServletRequest r) { return ApiResponse.ok(service.save(id,input,operator(r))); }
    @PostMapping("/{id}/actions") public ApiResponse<Compliance> act(@PathVariable String id,@Valid @RequestBody ComplianceAction input,HttpServletRequest r) { return ApiResponse.ok(service.act(id,input,operator(r))); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable String id,@RequestParam int version,HttpServletRequest r) { service.delete(id,version,operator(r)); return ApiResponse.ok(null); }
    private Long operator(HttpServletRequest r) { return (Long) r.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE); }
}
