package org.example.logisticsback.maintenance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.example.logisticsback.assets.AssetRecord;
import org.example.logisticsback.assets.AssetRepository;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceSupport {
    final JdbcTemplate jdbc;
    final AssetRepository assets;
    public MaintenanceSupport(JdbcTemplate jdbc, AssetRepository assets) { this.jdbc = jdbc; this.assets = assets; }
    static void require(boolean valid, String message) { if (!valid) throw new BusinessException(400, message); }
    static String text(String value) { return value == null ? "" : value.trim(); }
    static String id() { return UUID.randomUUID().toString(); }
    static void version(Integer supplied, int actual) {
        if (supplied == null || supplied != actual) throw new BusinessException(409, "记录已更新，请刷新后重试");
    }
    static void pastDate(LocalDate date) { require(date != null && !date.isAfter(LocalDate.now()), "记录日期不能晚于今天"); }
    AssetRecord equipment(String id, boolean active) {
        var asset = assets.lockById(id).orElseThrow(() -> new BusinessException(404, "关联设备不存在"));
        require("FACILITY".equals(asset.kind()) || "DEVICE".equals(asset.kind()), "请选择设备维修台账中的设备");
        require(!active || "ACTIVE".equals(asset.lifecycleStatus()), "关联设备已停用或退役");
        return asset;
    }
    void audit(String type, String id, String action, String description, Long operator) {
        jdbc.update("insert into maintenance_audit(id, entity_type, entity_id, action, description, operator_id) values(?,?,?,?,?,?)",
                id(), type, id, action, description, operator);
    }
    List<MaintenanceModels.Audit> history(String type, String id) {
        return jdbc.query("""
                select e.id, e.action, e.description, e.operator_id,
                  coalesce(nullif(u.real_name,''),u.username,'系统') operator_name,e.created_at
                from maintenance_audit e left join users u on e.operator_id=u.id
                where e.entity_type=? and e.entity_id=? order by e.sequence_id desc
                """, DataClassRowMapper.newInstance(MaintenanceModels.Audit.class), type,id);
    }
    void syncDates(String assetId) {
        if (assetId == null) return;
        jdbc.update("""
                update assets set maintenance_due_date=(select min(next_due_date) from maintenance_plans
                    where asset_id=? and status='ACTIVE' and plan_type='PREVENTIVE'),
                  inspection_due_date=(select min(due_date) from safety_compliance_items
                    where asset_id=? and status <> 'ARCHIVED'), updated_at=current_timestamp where id=?
                """, assetId,assetId,assetId);
    }
}
