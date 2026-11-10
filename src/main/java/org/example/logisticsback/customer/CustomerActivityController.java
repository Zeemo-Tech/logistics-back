package org.example.logisticsback.customer;

import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api")
public class CustomerActivityController {
    private final CustomerActivityService service;

    public CustomerActivityController(CustomerActivityService service) {
        this.service = service;
    }

    @GetMapping("/customer-intentions")
    public ApiResponse<List<CustomerActivityModels.Intention>> intentions(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String stage) {
        return ApiResponse.ok(service.intentions(customerId, stage));
    }

    @PostMapping("/customer-intentions")
    public ApiResponse<CustomerActivityModels.Intention> createIntention(
            @Valid @RequestBody CustomerActivityModels.IntentionRequest input) {
        return ApiResponse.ok(service.createIntention(input), "客户意向已创建");
    }

    @PutMapping("/customer-intentions/{id}")
    public ApiResponse<CustomerActivityModels.Intention> updateIntention(
            @PathVariable String id,
            @Valid @RequestBody CustomerActivityModels.IntentionRequest input) {
        return ApiResponse.ok(service.updateIntention(id, input), "客户意向已更新");
    }

    @DeleteMapping("/customer-intentions/{id}")
    public ApiResponse<Void> deleteIntention(@PathVariable String id) {
        service.deleteIntention(id);
        return ApiResponse.ok(null, "客户意向已删除");
    }

    @GetMapping("/customer-visits")
    public ApiResponse<List<CustomerActivityModels.Visit>> visits(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String intentionId) {
        return ApiResponse.ok(service.visits(customerId, intentionId));
    }

    @PostMapping("/customer-visits")
    public ApiResponse<CustomerActivityModels.Visit> createVisit(
            @Valid @RequestBody CustomerActivityModels.VisitRequest input) {
        return ApiResponse.ok(service.createVisit(input), "跟进记录已保存");
    }

    @DeleteMapping("/customer-visits/{id}")
    public ApiResponse<Void> deleteVisit(@PathVariable String id) {
        service.deleteVisit(id);
        return ApiResponse.ok(null, "跟进记录已删除");
    }
}
