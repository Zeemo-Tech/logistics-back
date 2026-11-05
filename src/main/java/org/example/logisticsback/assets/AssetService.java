package org.example.logisticsback.assets;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.export.ExportTable;
import org.example.logisticsback.maintenance.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {
    private static final Set<String> KINDS = Set.of(
            "WAREHOUSE", "COLD_STORAGE", "MOBILE_CABINET", "VEHICLE", "DEVICE", "FACILITY");
    private static final Set<String> OWNERSHIPS = Set.of("OWNED", "LEASED", "CUSTOMER");
    private static final Set<String> LIFECYCLE_STATUSES = Set.of("ACTIVE", "INACTIVE", "RETIRED");
    private static final Set<String> AVAILABILITY_STATUSES = Set.of(
            "AVAILABLE", "IN_USE", "MAINTENANCE", "UNAVAILABLE");
    private static final Set<String> DEVICE_CONDITIONS = Set.of("ONLINE", "OFFLINE", "ALERT");
    private static final Map<String, String> CODE_PREFIXES = Map.of(
            "WAREHOUSE", "WH", "COLD_STORAGE", "CS", "MOBILE_CABINET", "MC",
            "VEHICLE", "VEH", "DEVICE", "DEV", "FACILITY", "FAC");
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("WAREHOUSE", "普通仓库"), Map.entry("COLD_STORAGE", "固定冷库"),
            Map.entry("MOBILE_CABINET", "移动冷柜"), Map.entry("VEHICLE", "运输车辆"),
            Map.entry("DEVICE", "物联设备"), Map.entry("FACILITY", "园区设施"),
            Map.entry("OWNED", "自有"), Map.entry("LEASED", "租入"), Map.entry("CUSTOMER", "客户所有"),
            Map.entry("ACTIVE", "在册"), Map.entry("INACTIVE", "停用"), Map.entry("RETIRED", "退役"),
            Map.entry("AVAILABLE", "可用"), Map.entry("IN_USE", "使用中"),
            Map.entry("MAINTENANCE", "检修中"), Map.entry("UNAVAILABLE", "不可用"),
            Map.entry("ONLINE", "在线"), Map.entry("OFFLINE", "离线"), Map.entry("ALERT", "异常"));

    private final AssetRepository repository;
    private final WorkOrderRepository workOrders;

    public AssetService(AssetRepository repository, WorkOrderRepository workOrders) {
        this.repository = repository;
        this.workOrders = workOrders;
    }

    public List<AssetRecord> list(AssetQuery query) {
        return repository.search(query);
    }

    public AssetViews.Overview overview() {
        return repository.overview();
    }

    public AssetViews.Page ledger(AssetQuery query, int page, int pageSize) {
        require(query.kind() != null && (KINDS.contains(query.kind()) || "EQUIPMENT".equals(query.kind())), "资产类型不合法");
        require(page >= 1, "页码必须大于 0");
        require(pageSize >= 1 && pageSize <= 100, "每页数量需在 1 到 100 之间");
        require(query.lifecycleStatus() == null || query.lifecycleStatus().isBlank()
                || LIFECYCLE_STATUSES.contains(query.lifecycleStatus()), "生命周期状态不合法");
        require(query.availabilityStatus() == null || query.availabilityStatus().isBlank()
                || AVAILABILITY_STATUSES.contains(query.availabilityStatus()), "使用状态不合法");
        return repository.page(query, page, pageSize);
    }

    public AssetViews.Relations relations(String id) {
        return repository.relations(get(id));
    }

    public List<AssetViews.Option> bindingOptions(String kind, String keyword, String excludeId, String selectedId) {
        require("DEVICE".equals(kind) || "MOBILE_CABINET".equals(kind), "该类型资产不能设置绑定关系");
        return repository.bindingOptions(kind, keyword, excludeId, selectedId);
    }

    public AssetRecord get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "资产不存在或已删除"));
    }

    public List<AssetEvent> events(String id) {
        get(id);
        return repository.events(id);
    }

    @Transactional
    public AssetRecord create(AssetUpsertRequest request, Long operatorId) {
        validate(request, null);
        var id = UUID.randomUUID().toString();
        var code = CODE_PREFIXES.get(request.kind()) + "-" + id.substring(0, 12).toUpperCase();
        repository.insert(id, code, request);
        repository.addEvent(id, "CREATED", "新建资产档案", operatorId);
        return get(id);
    }

    @Transactional
    public AssetRecord update(String id, AssetUpsertRequest request, Long operatorId) {
        var existing = repository.lockById(id)
                .orElseThrow(() -> new BusinessException(404, "资产不存在或已删除"));
        if (workOrders.hasOpenOrder(id) && (!existing.lifecycleStatus().equals(request.lifecycleStatus())
                || !existing.availabilityStatus().equals(request.availabilityStatus()))) {
            throw new BusinessException(409, "资产存在未结束运维工单，请通过工单验收或取消处理状态");
        }
        if (!existing.kind().equals(request.kind())) {
            throw new BusinessException(400, "资产类型不能直接变更");
        }
        validate(request, id);
        if (!"ACTIVE".equals(request.lifecycleStatus()) && repository.hasActiveDependents(id)) {
            throw new BusinessException(400, "该资产仍绑定在用设备或冷柜，请先解除绑定");
        }
        if (!"ACTIVE".equals(request.lifecycleStatus()) && repository.hasActiveMaintenance(id)) {
            throw new BusinessException(409,"请先暂停巡检计划、停用水电表并归档合规事项，再停用或退役设备");
        }
        if ("RETIRED".equals(existing.lifecycleStatus()) && !"RETIRED".equals(request.lifecycleStatus())) {
            throw new BusinessException(400,"退役设备不可恢复，请新建设备档案");
        }
        repository.update(id, request);
        repository.addEvent(id, "UPDATED", "更新资产档案", operatorId);
        if (!existing.lifecycleStatus().equals(request.lifecycleStatus())
                || !existing.availabilityStatus().equals(request.availabilityStatus())) {
            repository.addEvent(id, "STATUS_CHANGED",
                    "生命周期：" + label(existing.lifecycleStatus()) + " → " + label(request.lifecycleStatus())
                    + "；使用状态：" + label(existing.availabilityStatus()) + " → " + label(request.availabilityStatus()), operatorId);
        }
        var nextLinkedAssetId = request.linkedAssetId() == null || request.linkedAssetId().isBlank()
                ? null : request.linkedAssetId();
        if (!java.util.Objects.equals(existing.linkedAssetId(), nextLinkedAssetId)) {
            repository.addEvent(id, "BINDING_CHANGED",
                    "资产绑定：" + linkedCode(existing.linkedAssetId()) + " → " + linkedCode(nextLinkedAssetId), operatorId);
        }
        return get(id);
    }

    @Transactional
    public void delete(String id, Long operator) {
        var a=repository.lockById(id).orElseThrow(() -> new BusinessException(404,"设备不存在"));
        require("FACILITY".equals(a.kind()) || "DEVICE".equals(a.kind()),"该菜单仅支持删除设备档案");
        if(repository.hasMaintenanceReferences(id)) throw new BusinessException(409,"设备已有计划、工单、表计、合规或绑定关系，请停用或退役以保留业务历史");
        require(repository.events(id).stream().noneMatch(e -> "MAINTENANCE".equals(e.eventType())),"设备已有维护历史，请停用或退役");
        repository.deleteUnreferenced(id,operator);
    }

    public ExportTable export(AssetQuery query) {
        var headers = List.of("资产编号", "资产名称", "资产类型", "分类", "权属", "权属主体",
                "园区", "位置", "负责人", "生命周期", "使用状态", "设备状态", "关联资产编号",
                "设计容量", "已占用", "容量单位", "温度下限", "温度上限", "剩余电量",
                "核载吨数", "保险到期日", "最后上报", "备注", "设备子类", "生产厂家", "规格型号",
                "出厂编号", "投用日期", "维保单位", "维护周期", "质保到期", "下次维保", "下次检验");
        var rows = new ArrayList<List<String>>();
        for (var asset : repository.search(query)) {
            rows.add(List.of(
                    value(asset.code()), value(asset.name()), label(asset.kind()), value(asset.category()),
                    label(asset.ownership()), value(asset.ownerName()), value(asset.site()),
                    value(asset.location()), value(asset.custodian()), label(asset.lifecycleStatus()),
                    label(asset.availabilityStatus()), label(asset.conditionStatus()), linkedCode(asset.linkedAssetId()),
                    value(asset.capacityTotal()), value(asset.capacityUsed()), value(asset.capacityUnit()),
                    value(asset.temperatureMin()), value(asset.temperatureMax()), value(asset.batteryPercent()),
                    value(asset.ratedLoadTons()), value(asset.insuranceExpiry()), value(asset.lastSeen()),
                    value(asset.notes()), value(asset.subcategory()), value(asset.manufacturer()),
                    value(asset.equipmentModel()), value(asset.serialNumber()), value(asset.commissionedDate()),
                    value(asset.serviceProvider()), value(asset.maintenanceCycle()), value(asset.warrantyExpiry()),
                    value(asset.maintenanceDueDate()), value(asset.inspectionDueDate())));
        }
        return new ExportTable("资产台账", headers, rows);
    }

    private void validate(AssetUpsertRequest request, String currentId) {
        require(KINDS.contains(request.kind()), "资产类型不合法");
        require(OWNERSHIPS.contains(request.ownership()), "资产权属不合法");
        require(LIFECYCLE_STATUSES.contains(request.lifecycleStatus()), "生命周期状态不合法");
        require(AVAILABILITY_STATUSES.contains(request.availabilityStatus()), "使用状态不合法");
        require(!"IN_USE".equals(request.availabilityStatus())
                || "ACTIVE".equals(request.lifecycleStatus()), "非在册资产不能处于使用中");
        if(request.commissionedDate()!=null) require(!request.commissionedDate().isAfter(java.time.LocalDate.now()),"投用日期不能晚于今天");
        if(request.warrantyExpiry()!=null && request.commissionedDate()!=null) require(!request.warrantyExpiry().isBefore(request.commissionedDate()),"质保到期不能早于投用日期");
        nonNegative(request.capacityTotal(), "设计容量");
        nonNegative(request.capacityUsed(), "已占用容量");
        nonNegative(request.ratedLoadTons(), "核载吨数");
        if (request.capacityUsed() != null) {
            require(request.capacityTotal() != null, "填写已占用容量前需填写设计容量");
            require(request.capacityUsed().compareTo(request.capacityTotal()) <= 0, "已占用容量不能大于设计容量");
        }
        if ("WAREHOUSE".equals(request.kind()) || "COLD_STORAGE".equals(request.kind())) {
            require(request.capacityTotal() != null && request.capacityTotal().signum() > 0,
                    "仓储资产必须填写设计容量");
            require("WAREHOUSE".equals(request.kind()) ? "m²".equals(request.capacityUnit())
                    : "吨".equals(request.capacityUnit()), "容量单位与资产类型不匹配");
        }
        if (request.temperatureMin() != null && request.temperatureMax() != null) {
            require(request.temperatureMin().compareTo(request.temperatureMax()) <= 0,
                    "温度下限不能高于上限");
        }
        if (request.batteryPercent() != null) {
            require(request.batteryPercent().compareTo(BigDecimal.ZERO) >= 0
                    && request.batteryPercent().compareTo(new BigDecimal("100")) <= 0,
                    "剩余电量需在 0 到 100 之间");
        }
        if ("DEVICE".equals(request.kind())) {
            require(request.conditionStatus() == null || DEVICE_CONDITIONS.contains(request.conditionStatus()),
                    "设备状态不合法");
        } else {
            require(request.conditionStatus() == null, "非设备资产不能设置设备状态");
        }
        var linkedId = request.linkedAssetId();
        if (linkedId != null && !linkedId.isBlank()) {
            require(!linkedId.equals(currentId), "资产不能绑定自身");
            require("DEVICE".equals(request.kind()) || "MOBILE_CABINET".equals(request.kind()),
                    "该类型资产不能设置绑定关系");
            var linked = get(linkedId);
            require("ACTIVE".equals(linked.lifecycleStatus()), "不能绑定停用或退役资产");
            require(!"MOBILE_CABINET".equals(request.kind()) || "VEHICLE".equals(linked.kind()),
                    "移动冷柜只能绑定车辆");
            require(!"DEVICE".equals(request.kind()) || !"DEVICE".equals(linked.kind()),
                    "设备不能绑定其他设备");
        }
    }

    private static void nonNegative(BigDecimal value, String label) {
        if (value != null) require(value.signum() >= 0, label + "不能为负数");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(400, message);
    }

    private static String value(Object input) {
        return input == null ? "" : input.toString();
    }

    private static String label(String input) {
        return input == null ? "" : LABELS.getOrDefault(input, input);
    }

    private String linkedCode(String id) {
        return id == null ? "未绑定" : get(id).code();
    }
}
