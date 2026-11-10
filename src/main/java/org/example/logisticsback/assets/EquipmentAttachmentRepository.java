package org.example.logisticsback.assets;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentAttachmentRepository {
    private final JdbcTemplate jdbc;

    public EquipmentAttachmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EquipmentAttachment> list(String assetId) {
        return jdbc.query("""
                select id, asset_id, category, name, mime_type, file_size, uploaded_by, uploaded_at, remark
                from equipment_attachments where asset_id = ? order by uploaded_at desc, id desc
                """, this::map, assetId);
    }

    public Optional<Stored> find(String id) {
        return jdbc.query("select * from equipment_attachments where id = ?", (r, row) -> new Stored(
                new EquipmentAttachment(r.getString("id"), r.getString("asset_id"), r.getString("category"),
                        r.getString("name"), r.getString("mime_type"), r.getLong("file_size"),
                        r.getObject("uploaded_by", Long.class),
                        r.getObject("uploaded_at", LocalDateTime.class), r.getString("remark")),
                r.getString("file_base64")), id).stream().findFirst();
    }

    public void insert(String id, String assetId, String category, String name, String mimeType,
                       long fileSize, String base64, Long uploadedBy, String remark) {
        jdbc.update("""
                insert into equipment_attachments (id, asset_id, category, name, mime_type, file_size,
                    file_base64, uploaded_by, remark) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, assetId, category, name, mimeType, fileSize, base64, uploadedBy, remark);
    }

    public void delete(String id) {
        jdbc.update("delete from equipment_attachments where id = ?", id);
    }

    private EquipmentAttachment map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new EquipmentAttachment(r.getString("id"), r.getString("asset_id"), r.getString("category"),
                r.getString("name"), r.getString("mime_type"), r.getLong("file_size"),
                r.getObject("uploaded_by", Long.class), r.getObject("uploaded_at", LocalDateTime.class),
                r.getString("remark"));
    }

    public record Stored(EquipmentAttachment metadata, String base64) {}
}
