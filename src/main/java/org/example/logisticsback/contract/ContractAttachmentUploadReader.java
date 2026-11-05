package org.example.logisticsback.contract;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class ContractAttachmentUploadReader {
    public Parsed read(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return new Parsed("SPREADSHEET", lower.endsWith(".xlsx")
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/vnd.ms-excel", sheets(bytes));
        }
        if (lower.endsWith(".pdf") && bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return new Parsed("PDF", "application/pdf", List.of());
        }
        String mime = imageMime(lower, bytes);
        if (mime != null) return new Parsed("IMAGE", mime, List.of());
        throw new BusinessException(400, "文件格式不受支持或内容不正确：" + name + "，请上传 PDF、Excel 或图片");
    }

    private String imageMime(String name, byte[] b) {
        if (b.length < 12) return null;
        if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && (b[0] & 255) == 255 && (b[1] & 255) == 216) return "image/jpeg";
        if (name.endsWith(".png") && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71) return "image/png";
        if (name.endsWith(".gif") && new String(b, 0, 3, StandardCharsets.US_ASCII).equals("GIF")) return "image/gif";
        if (name.endsWith(".webp") && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if (name.endsWith(".bmp") && b[0] == 66 && b[1] == 77) return "image/bmp";
        return null;
    }

    private List<ContractAttachmentViews.Sheet> sheets(byte[] bytes) {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var result = new ArrayList<ContractAttachmentViews.Sheet>();
            var formatter = new DataFormatter(Locale.CHINA);
            formatter.setUseCachedValuesForFormulaCells(true);
            int remainingCells = 20000;
            for (int index = 0; index < Math.min(workbook.getNumberOfSheets(), 20) && remainingCells > 0; index++) {
                var sheet = workbook.getSheetAt(index);
                var first = sheet.getRow(sheet.getFirstRowNum());
                int sourceWidth = 0;
                for (int row = sheet.getFirstRowNum(); row <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 2000); row++) {
                    var source = sheet.getRow(row);
                    if (source != null) sourceWidth = Math.max(sourceWidth, source.getLastCellNum());
                }
                int width = Math.min(sourceWidth, 100);
                var columns = new ArrayList<String>();
                for (int col = 0; col < width; col++) columns.add(first == null ? "" : formatter.formatCellValue(first.getCell(col)));
                var rows = new ArrayList<List<String>>();
                int maxRows = Math.min(2000, remainingCells / Math.max(width, 1));
                for (int row = sheet.getFirstRowNum() + 1; row <= sheet.getLastRowNum() && rows.size() < maxRows; row++) {
                    var source = sheet.getRow(row);
                    var cells = new ArrayList<String>();
                    for (int col = 0; col < width; col++) cells.add(source == null ? "" : formatter.formatCellValue(source.getCell(col)));
                    rows.add(cells);
                }
                remainingCells -= rows.size() * Math.max(width, 1);
                boolean truncated = sheet.getLastRowNum() - sheet.getFirstRowNum() > rows.size()
                        || sourceWidth > width || workbook.getNumberOfSheets() > 20 || remainingCells == 0;
                result.add(new ContractAttachmentViews.Sheet(sheet.getSheetName(), columns, rows,
                        truncated ? "在线预览已截取部分内容，完整数据请下载原文件查看" : "共 " + rows.size() + " 条明细"));
            }
            if (result.isEmpty()) throw new BusinessException(400, "Excel 文件中没有工作表");
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(400, "Excel 无法读取，请确认文件完整且未加密");
        }
    }

    public record Parsed(String format, String mimeType, List<ContractAttachmentViews.Sheet> sheets) {}
}
