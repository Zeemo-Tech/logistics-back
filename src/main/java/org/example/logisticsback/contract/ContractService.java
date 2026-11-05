package org.example.logisticsback.contract;

import java.util.ArrayList;
import java.util.List;
import org.example.logisticsback.export.ExportTable;
import org.springframework.stereotype.Service;

@Service
public class ContractService {
    private static final List<String> HEADERS = List.of(
            "合同编号", "合同名称", "合同类型", "合同相对方", "归属园区资源",
            "开始日期", "结束日期", "合同金额", "负责人", "单据数", "状态");

    private final ContractRepository repository;

    public ContractService(ContractRepository repository) {
        this.repository = repository;
    }

    public List<ContractRecord> list(String keyword, String category, String status, String resource) {
        return repository.search(keyword, category, status, resource);
    }

    public ContractRecord get(String id) {
        return repository.get(id);
    }

    public ExportTable export(String keyword, String category, String status, String resource) {
        var records = repository.search(keyword, category, status, resource);
        var rows = new ArrayList<List<String>>();
        for (var record : records) {
            rows.add(List.of(
                    record.id(),
                    record.title(),
                    record.category(),
                    record.counterparty(),
                    record.resource(),
                    record.startDate(),
                    record.endDate(),
                    record.amount(),
                    record.owner(),
                    String.valueOf(record.documentCount()),
                    record.status()));
        }
        return new ExportTable("合同清单", HEADERS, rows);
    }
}
