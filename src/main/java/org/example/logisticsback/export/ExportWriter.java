package org.example.logisticsback.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.logisticsback.common.BusinessException;

public final class ExportWriter {
    private ExportWriter() {}

    public static byte[] write(ExportTable table, ExportFormat format) {
        return format == ExportFormat.CSV ? toCsv(table) : toXlsx(table);
    }

    private static byte[] toCsv(ExportTable table) {
        var builder = new StringBuilder();
        builder.append('\uFEFF'); // UTF-8 BOM so Excel opens Chinese text correctly
        appendCsvRow(builder, table.headers());
        for (var row : table.rows()) {
            appendCsvRow(builder, row);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsvRow(StringBuilder builder, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) builder.append(',');
            builder.append(escapeCsvValue(cells.get(i)));
        }
        builder.append("\r\n");
    }

    private static String escapeCsvValue(String value) {
        var text = value == null ? "" : value;
        var quoted = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quoted) return text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static byte[] toXlsx(ExportTable table) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(trimSheetName(table.sheetName()));
            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            writeRow(sheet, 0, table.headers(), headerStyle);
            var rowIndex = 1;
            for (var row : table.rows()) {
                writeRow(sheet, rowIndex++, row, null);
            }
            for (var header : table.headers()) {
                // auto-size up to a sane cap
                sheet.autoSizeColumn(table.headers().indexOf(header));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "生成 Excel 文件失败");
        }
    }

    private static void writeRow(Sheet sheet, int rowIndex, List<String> cells, org.apache.poi.ss.usermodel.CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(cells.get(i) == null ? "" : cells.get(i));
            if (style != null) cell.setCellStyle(style);
        }
    }

    private static String trimSheetName(String name) {
        // Excel sheet names are limited to 31 characters
        return name.length() > 31 ? name.substring(0, 31) : name;
    }
}
