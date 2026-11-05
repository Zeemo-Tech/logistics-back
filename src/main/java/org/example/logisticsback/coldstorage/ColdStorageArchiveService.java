package org.example.logisticsback.coldstorage;

import java.util.ArrayList;
import java.util.List;
import org.example.logisticsback.export.ExportTable;
import org.springframework.stereotype.Service;

@Service
public class ColdStorageArchiveService {
    private static final List<String> HEADERS =
            List.of("序号", "经营主体", "冷库名称", "冷库类型", "冷库等级", "监测设备", "所在地区");

    private final ColdStorageArchiveRepository repository;

    public ColdStorageArchiveService(ColdStorageArchiveRepository repository) {
        this.repository = repository;
    }

    public List<ColdStorageArchive> list(String operator, String name, String storageType,
                                         Boolean hasDevice, String grade) {
        return repository.search(operator, name, storageType, hasDevice, grade);
    }

    public ExportTable export(String operator, String name, String storageType,
                              Boolean hasDevice, String grade) {
        var records = repository.search(operator, name, storageType, hasDevice, grade);
        var rows = new ArrayList<List<String>>();
        for (int i = 0; i < records.size(); i++) {
            var record = records.get(i);
            rows.add(List.of(
                    String.valueOf(i + 1),
                    record.operator(),
                    record.name(),
                    record.storageType(),
                    record.grade(),
                    record.device().isBlank() ? "未安装" : record.device(),
                    record.region()));
        }
        return new ExportTable("冷库档案", HEADERS, rows);
    }
}
