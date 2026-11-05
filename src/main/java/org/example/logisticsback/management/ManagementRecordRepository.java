package org.example.logisticsback.management;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManagementRecordRepository {
    private static final List<String> OPERATORS = List.of(
            "百川物流智慧冷链中心", "勐腊县供销农产品有限公司", "磨憨口岸国际物流有限公司",
            "勐满镇果蔬合作社", "勐仑镇热带果品合作社", "关累港冷链物流有限公司");
    private static final List<String> REGIONS = List.of("勐腊镇", "磨憨镇", "勐满镇", "勐仑镇", "关累镇", "易武镇");
    private static final List<String> CONTACTS = List.of("岩温", "玉罕", "李明", "张伟", "王芳", "罗军");

    private final JdbcTemplate jdbc;

    public ManagementRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        if (jdbc.queryForObject("select count(*) from management_records", Integer.class) == 0) {
            seed();
        }
    }

    private void seed() {
        for (var profile : seedProfiles()) {
            var count = profile.recordCount() == null ? 18 : profile.recordCount();
            var operatorNames = profile.operatorNames() == null ? OPERATORS : profile.operatorNames();
            for (int i = 0; i < count; i++) {
                var id = profile.codePrefix() + "-" + String.format("%03d", i + 1);
                var name = profile.names().get(i % profile.names().size());
                var category = profile.categories().get(i % profile.categories().size());
                var operator = operatorNames.get(i % operatorNames.size());
                var region = REGIONS.get(i % REGIONS.size());
                var status = profile.statuses().get(i % profile.statuses().size());
                var metric = (profile.baseValue() + (i % 6) * profile.valueStep()) + " " + profile.metricUnit();
                var contact = CONTACTS.get(i % CONTACTS.size())
                        + "  13" + (8 + (i % 2)) + "****" + last4(3621 + i * 37);
                var updatedAt = "2026-08-" + String.format("%02d", 12 - (i % 8))
                        + " " + String.format("%02d", 9 + (i % 8)) + ":20";
                jdbc.update(
                        "insert into management_records (id, record_type, name, category, operator, region, status, metric, contact, updated_at) "
                                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, profile.type(), name, category, operator, region, status, metric, contact, updatedAt);
            }
        }
    }

    private static String last4(int value) {
        var text = String.valueOf(value);
        return text.substring(Math.max(0, text.length() - 4));
    }

    public List<ManagementRecord> search(String type, String keyword, String category, String region, String status) {
        var sql = new StringBuilder("select * from management_records where 1 = 1");
        var params = new ArrayList<Object>();

        sql.append(" and record_type = ?");
        params.add(type);

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(id) like lower(?) or lower(name) like lower(?) or lower(operator) like lower(?))");
            var pattern = "%" + keyword + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            params.add(category);
        }
        if (region != null && !region.isBlank()) {
            sql.append(" and region = ?");
            params.add(region);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            params.add(status);
        }

        sql.append(" order by id");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    private ManagementRecord map(ResultSet result, int rowNumber) throws SQLException {
        return new ManagementRecord(
                result.getString("id"),
                result.getString("record_type"),
                result.getString("name"),
                result.getString("category"),
                result.getString("operator"),
                result.getString("region"),
                result.getString("status"),
                result.getString("metric"),
                result.getString("contact"),
                result.getString("updated_at"));
    }

    private record ProfileSeed(
            String type,
            String codePrefix,
            List<String> names,
            List<String> categories,
            List<String> statuses,
            int baseValue,
            int valueStep,
            String metricUnit,
            Integer recordCount,
            List<String> operatorNames) {}

    private static List<ProfileSeed> seedProfiles() {
        return List.of(
                new ProfileSeed("operator-archives", "ML-QY",
                        List.of("百川物流智慧冷链中心"),
                        List.of("冷链物流", "农产品加工", "合作社", "商贸流通"),
                        List.of("正常", "待完善", "暂停经营"),
                        18, 7, "人", 1, List.of("百川物流智慧冷链中心")),
                new ProfileSeed("refrigerated-vehicle-archives", "云K",
                        List.of("云K·CL618", "云K·CL527", "云K·ML369", "云K·BC808", "云K·MH256", "云K·GL117"),
                        List.of("厢式冷藏车", "冷冻运输车", "多温区冷藏车"),
                        List.of("运营中", "维保中", "停运"),
                        6, 2, "吨", 18, null),
                new ProfileSeed("supporting-facilities", "ML-PT",
                        List.of("果蔬分拣包装线", "食品安全检测中心", "冷链装卸月台", "集中制冰站", "托盘周转中心"),
                        List.of("分拣包装", "制冰供冷", "检验检测", "装卸月台"),
                        List.of("正常运行", "维护中", "待启用"),
                        3, 1, "项", 18, null),
                new ProfileSeed("operator-certificates", "ML-ZS",
                        List.of("食品经营许可证", "道路运输经营许可证", "绿色食品认证", "质量管理体系认证"),
                        List.of("食品经营许可", "道路运输许可", "质量体系认证", "产地认证"),
                        List.of("有效", "即将到期", "已过期"),
                        6, 3, "月", 18, null),
                new ProfileSeed("personnel-certificates", "ML-RY",
                        List.of("岩温", "玉罕", "李明", "张伟", "王芳", "罗军"),
                        List.of("健康证", "特种设备作业证", "制冷工证", "驾驶员从业资格证"),
                        List.of("有效", "即将到期", "已过期"),
                        5, 4, "月", 18, null),
                new ProfileSeed("subsidy-projects", "ML-BT",
                        List.of("农产品产地冷藏保鲜项目", "冷链设备节能改造项目", "冷藏运输车辆购置项目", "温湿度监测升级项目"),
                        List.of("冷库建设", "设备更新", "车辆购置", "数字化改造"),
                        List.of("实施中", "待验收", "已完成"),
                        48, 16, "万元", 18, null),
                new ProfileSeed("acceptance-records", "ML-YS",
                        List.of("冷库建设项目验收", "冷链车辆购置验收", "制冷设备改造验收", "数字化监测项目验收"),
                        List.of("现场验收", "资料复核", "绩效评价"),
                        List.of("验收通过", "整改复验", "待验收"),
                        36, 12, "万元", 18, null));
    }
}
