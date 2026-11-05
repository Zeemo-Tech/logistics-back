package org.example.logisticsback.contract;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContractAttachmentRepository {
    private static final String METADATA_COLUMNS = """
            id, contract_id, name, document_type, resource, document_date, uploader,
            uploaded_at, file_size, status, format, mime_type, is_demo
            """;
    private final JdbcTemplate jdbc;

    public ContractAttachmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContractAttachmentViews.Metadata> list(String contractId) {
        if (jdbc.queryForObject("select count(*) from contracts where id = ?", Integer.class, contractId) == 0) {
            throw new BusinessException(404, "合同不存在");
        }
        // Metadata queries deliberately exclude document bytes and preview data.
        return jdbc.query("select " + METADATA_COLUMNS + " from contract_attachments where contract_id = ? and deleted_at is null order by uploaded_at desc, id",
                (rs, rowNum) -> metadata(rs), contractId);
    }

    public StoredPreview preview(String contractId, String attachmentId) {
        var results = jdbc.query("""
                select format, is_demo, preview_json from contract_attachments where contract_id = ? and id = ? and deleted_at is null
                """, (rs, rowNum) -> new StoredPreview(rs.getString("format"), rs.getBoolean("is_demo"), rs.getString("preview_json")),
                contractId, attachmentId);
        if (results.isEmpty()) throw new BusinessException(404, "该合同下的附件不存在");
        return results.get(0);
    }

    public StoredFile file(String contractId, String attachmentId) {
        var results = jdbc.query("""
                select name, mime_type, file_base64 from contract_attachments where contract_id = ? and id = ? and deleted_at is null
                """, (rs, rowNum) -> new StoredFile(rs.getString("name"), rs.getString("mime_type"), rs.getString("file_base64")),
                contractId, attachmentId);
        if (results.isEmpty()) throw new BusinessException(404, "该合同下的附件不存在");
        return results.get(0);
    }

    private static ContractAttachmentViews.Metadata metadata(ResultSet rs) throws SQLException {
        return new ContractAttachmentViews.Metadata(rs.getString("id"), rs.getString("contract_id"), rs.getString("name"),
                rs.getString("document_type"), rs.getString("resource"), rs.getString("document_date"), rs.getString("uploader"),
                rs.getString("uploaded_at"), rs.getString("file_size"), rs.getString("status"), rs.getString("format"),
                rs.getString("mime_type"), rs.getBoolean("is_demo"));
    }

    public List<ContractAttachmentViews.ListItem> listAll() {
        return jdbc.query("""
                select a.id, a.contract_id, a.name, a.document_type, a.resource, a.document_date, a.uploader,
                    a.uploaded_at, a.file_size, a.status, a.format, a.mime_type, a.is_demo, c.title as contract_name
                from contract_attachments a
                join contracts c on c.id = a.contract_id
                where a.deleted_at is null order by a.uploaded_at desc, a.id desc
                """, (rs, rowNum) -> {
                    var m = metadata(rs);
                    return new ContractAttachmentViews.ListItem(m.id(), m.contractId(), rs.getString("contract_name"),
                            m.name(), m.type(), m.resource(), m.documentDate(), m.uploader(), m.uploadedAt(),
                            m.size(), m.status(), m.format(), m.mimeType(), m.demo());
                });
    }

    public void delete(String contractId, String attachmentId) {
        int changed = jdbc.update("""
                update contract_attachments set deleted_at = current_timestamp
                where contract_id = ? and id = ? and deleted_at is null
                """, contractId, attachmentId);
        if (changed == 0) throw new BusinessException(404, "该合同下的附件不存在或已删除");
    }

    public void insert(ContractAttachmentViews.Metadata m, String previewJson, byte[] bytes, String remark) {
        jdbc.update("""
                insert into contract_attachments (id, contract_id, name, document_type, resource, document_date,
                    uploader, uploaded_at, file_size, status, format, mime_type, is_demo, preview_json, file_base64, remark)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?, ?)
                """, m.id(), m.contractId(), m.name(), m.type(), m.resource(), m.documentDate(), m.uploader(),
                m.uploadedAt(), m.size(), m.status(), m.format(), m.mimeType(), previewJson,
                java.util.Base64.getEncoder().encodeToString(bytes), remark);
    }

    public record StoredPreview(String format, boolean demo, String json) {}
    public record StoredFile(String name, String mimeType, String base64) {}
}
