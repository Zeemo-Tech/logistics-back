package org.example.logisticsback.export;

import org.example.logisticsback.common.BusinessException;

public enum ExportFormat {
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
    CSV("text/csv;charset=UTF-8", ".csv");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() { return contentType; }
    public String extension() { return extension; }

    public static ExportFormat parse(String value) {
        if (value == null || value.isBlank() || "xlsx".equalsIgnoreCase(value.trim())) {
            return XLSX;
        }
        if ("csv".equalsIgnoreCase(value.trim())) {
            return CSV;
        }
        throw new BusinessException(400, "不支持的导出格式: " + value);
    }
}
