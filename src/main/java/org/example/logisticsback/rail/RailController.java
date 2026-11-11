package org.example.logisticsback.rail;

import java.util.List;
import java.util.Map;

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
}
