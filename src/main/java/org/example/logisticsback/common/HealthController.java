package org.example.logisticsback.common;

import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public ApiResponse<Health> health() {
        return ApiResponse.ok(new Health("UP", OffsetDateTime.now()));
    }

    public record Health(String status, OffsetDateTime timestamp) {}
}
