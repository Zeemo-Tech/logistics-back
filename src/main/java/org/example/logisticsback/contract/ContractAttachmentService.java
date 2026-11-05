package org.example.logisticsback.contract;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.export.ExportWriter;
import org.example.logisticsback.export.ExportTable;
import org.example.logisticsback.export.ExportFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class ContractAttachmentService {
    private final ContractAttachmentRepository repository;
    private final ObjectMapper mapper;
    private final ContractRepository contracts;
    private final ContractAttachmentUploadReader reader;

    public ContractAttachmentService(ContractAttachmentRepository repository, ObjectMapper mapper,
            ContractRepository contracts, ContractAttachmentUploadReader reader) {
        this.repository = repository;
        this.mapper = mapper;
        this.contracts = contracts;
        this.reader = reader;
    }

    public List<ContractAttachmentViews.Metadata> list(String contractId) {
        return repository.list(contractId);
    }

    public List<ContractAttachmentViews.ListItem> listAll() {
        return repository.listAll();
    }

    @Transactional
    public void delete(String contractId, String attachmentId) {
        repository.delete(contractId, attachmentId);
    }

    @Transactional
    public List<ContractAttachmentViews.Metadata> upload(String contractId, String type, String resource,
            String documentDate, String uploader, String remark, List<MultipartFile> files) {
        contracts.get(contractId);
        validateText(type, 64, "单据类型");
        validateText(resource, 255, "归属资源");
        validateText(uploader, 64, "经办人");
        if (remark == null || remark.length() > 1000) throw new BusinessException(400, "备注不得超过 1000 字");
        try { LocalDate.parse(documentDate); }
        catch (RuntimeException exception) { throw new BusinessException(400, "请选择有效的单据日期"); }
        if (files == null || files.isEmpty() || files.size() > 8) throw new BusinessException(400, "每次请选择 1 至 8 个文件");
        var result = new ArrayList<ContractAttachmentViews.Metadata>();
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (var file : files) {
            if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) throw new BusinessException(400, "文件不能为空，单个文件不得超过 20 MB");
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
            validateText(name, 255, "文件名称");
            byte[] bytes;
            try { bytes = file.getBytes(); }
            catch (IOException exception) { throw new BusinessException(400, "文件内容读取失败"); }
            var parsed = reader.read(name, bytes);
            String size = file.getSize() >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MB", file.getSize() / 1024.0 / 1024)
                    : String.format(Locale.ROOT, "%.1f KB", file.getSize() / 1024.0);
            var metadata = new ContractAttachmentViews.Metadata("DJ-" + UUID.randomUUID(), contractId, name, type.trim(),
                    resource.trim(), documentDate, uploader.trim(), timestamp, size, "已归档", parsed.format(), parsed.mimeType(), false);
            repository.insert(metadata, parsed.sheets().isEmpty() ? null : mapper.writeValueAsString(parsed.sheets()), bytes, remark.trim());
            result.add(metadata);
        }
        return result;
    }

    private void validateText(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength) throw new BusinessException(400, label + "不能为空且不得超过 " + maxLength + " 字");
    }

    public ContractAttachmentViews.Preview preview(String contractId, String attachmentId) {
        var stored = repository.preview(contractId, attachmentId);
        List<ContractAttachmentViews.Sheet> sheets = List.of();
        if (stored.format().equals("SPREADSHEET")) {
            if (stored.json() == null || stored.json().isBlank()) throw new BusinessException(404, "该附件尚未提供表格内容");
            try {
                sheets = Arrays.asList(mapper.readValue(stored.json(), ContractAttachmentViews.Sheet[].class));
            } catch (RuntimeException exception) {
                throw new BusinessException(500, "附件表格内容读取失败");
            }
        }
        return new ContractAttachmentViews.Preview(stored.format(), stored.demo(), sheets);
    }

    public ContractAttachmentViews.FileContent file(String contractId, String attachmentId) {
        var stored = repository.file(contractId, attachmentId);
        if (stored.base64() == null || stored.base64().isBlank()) {
            // Older spreadsheet demo records store cells only; provide a real workbook for downloads.
            var data = preview(contractId, attachmentId);
            if (data.format().equals("SPREADSHEET") && !data.sheets().isEmpty()) {
                var sheet = data.sheets().get(0);
                return new ContractAttachmentViews.FileContent(stored.name(), stored.mimeType(),
                        ExportWriter.write(new ExportTable(sheet.name(), sheet.columns(), sheet.rows()), ExportFormat.XLSX));
            }
            throw new BusinessException(404, "该附件尚未提供文件内容");
        }
        try {
            return new ContractAttachmentViews.FileContent(stored.name(), stored.mimeType(), Base64.getDecoder().decode(stored.base64()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(500, "附件文件内容读取失败");
        }
    }
}
