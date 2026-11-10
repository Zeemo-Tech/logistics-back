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
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CustomerRecord>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String nature,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(keyword, nature, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerRecord> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<CustomerRecord> create(@Valid @RequestBody CustomerRequest input) {
        return ApiResponse.ok(service.create(input), "客户档案已创建");
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerRecord> update(@PathVariable String id,
                                               @Valid @RequestBody CustomerRequest input) {
        return ApiResponse.ok(service.update(id, input), "客户档案已更新");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok(null, "客户档案已删除");
    }
}
