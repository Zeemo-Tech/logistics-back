package org.example.logisticsback.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.export.ExportTable;
import org.springframework.stereotype.Service;

@Service
public class ManagementRecordService {
    private static final Map<String, ProfileMeta> PROFILES = new LinkedHashMap<>();

    static {
        PROFILES.put("operator-archives", new ProfileMeta("经营主体档案", "主体名称", "从业人员"));
        PROFILES.put("refrigerated-vehicle-archives", new ProfileMeta("冷藏车档案", "车辆编号", "核载质量"));
        PROFILES.put("supporting-facilities", new ProfileMeta("配套设施管理", "设施名称", "服务能力"));
        PROFILES.put("operator-certificates", new ProfileMeta("经营主体证书", "证书名称", "有效期"));
        PROFILES.put("personnel-certificates", new ProfileMeta("从业人员证书", "持证人员", "有效期"));
        PROFILES.put("subsidy-projects", new ProfileMeta("补贴项目", "项目名称", "申报金额"));
        PROFILES.put("acceptance-records", new ProfileMeta("验收记录", "验收项目", "核定补贴"));
    }

    private final ManagementRecordRepository repository;

    public ManagementRecordService(ManagementRecordRepository repository) {
        this.repository = repository;
    }

    public List<ManagementRecord> list(String type, String keyword, String category, String region, String status) {
        var resolved = resolveType(type);
        return repository.search(resolved, keyword, category, region, status);
    }

    public ExportTable export(String type, String keyword, String category, String region, String status) {
        var resolved = resolveType(type);
        var meta = PROFILES.get(resolved);
        var headers = List.of("档案编号", meta.entityLabel(), "业务类型", "归属经营主体",
                "所在乡镇", meta.metricLabel(), "联系人", "状态", "更新时间");
        var records = repository.search(resolved, keyword, category, region, status);
        var rows = new ArrayList<List<String>>();
        for (var record : records) {
            rows.add(List.of(
                    record.id(),
                    record.name(),
                    record.category(),
                    record.operator(),
                    record.region(),
                    record.metric(),
                    record.contact(),
                    record.status(),
                    record.updatedAt()));
        }
        return new ExportTable(meta.title() + "清单", headers, rows);
    }

    private String resolveType(String type) {
        var resolved = type == null || type.isBlank() ? "operator-archives" : type.trim();
        if (!PROFILES.containsKey(resolved)) {
            throw new BusinessException(400, "不支持的档案类型: " + type);
        }
        return resolved;
    }

    private record ProfileMeta(String title, String entityLabel, String metricLabel) {}
}
