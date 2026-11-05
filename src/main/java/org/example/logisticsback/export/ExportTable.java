package org.example.logisticsback.export;

import java.util.List;

public record ExportTable(String sheetName, List<String> headers, List<List<String>> rows) {
    public ExportTable {
        sheetName = sheetName == null || sheetName.isBlank() ? "Sheet1" : sheetName;
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : rows;
    }
}
