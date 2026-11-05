package org.example.logisticsback.export;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public final class ExportResponse {
    private ExportResponse() {}

    public static ResponseEntity<byte[]> file(byte[] content, ExportFormat format, String baseName) {
        var encoded = URLEncoder.encode(baseName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, format.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded + format.extension())
                .body(content);
    }
}
