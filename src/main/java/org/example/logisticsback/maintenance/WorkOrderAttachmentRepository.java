package org.example.logisticsback.maintenance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkOrderAttachmentRepository {
    private final JdbcTemplate jdbc;

    public WorkOrderAttachmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkOrderAttachment> list(String workOrderId) {
        return jdbc.query("""
                select id, work_order_id, category, name, mime_type, file_size, uploaded_by, uploaded_at, remark
                from maintenance_work_order_attachments where work_order_id = ?
                order by uploaded_at desc, id desc
                """, this::map, workOrderId);
    }

    public Optional<Stored> find(String id) {
        return jdbc.query("select * from maintenance_work_order_attachments where id = ?",
                (r, row) -> new Stored(new WorkOrderAttachment(r.getString("id"),
                        r.getString("work_order_id"), r.getString("category"), r.getString("name"),
                        r.getString("mime_type"), r.getLong("file_size"),
                        r.getObject("uploaded_by", Long.class),
                        r.getObject("uploaded_at", LocalDateTime.class), r.getString("remark")),
                        r.getString("file_base64")), id).stream().findFirst();
    }

    public void insert(String id, String workOrderId, String category, String name, String mimeType,
                       long fileSize, String base64, Long uploadedBy, String remark) {
        jdbc.update("""
                insert into maintenance_work_order_attachments (id, work_order_id, category, name, mime_type,
                    file_size, file_base64, uploaded_by, remark)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, workOrderId, category, name, mimeType, fileSize, base64, uploadedBy, remark);
    }

    public void delete(String id) {
        jdbc.update("delete from maintenance_work_order_attachments where id = ?", id);
    }

    private WorkOrderAttachment map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new WorkOrderAttachment(r.getString("id"), r.getString("work_order_id"),
                r.getString("category"), r.getString("name"), r.getString("mime_type"),
                r.getLong("file_size"), r.getObject("uploaded_by", Long.class),
                r.getObject("uploaded_at", LocalDateTime.class), r.getString("remark"));
    }

    public record Stored(WorkOrderAttachment metadata, String base64) {}
}
