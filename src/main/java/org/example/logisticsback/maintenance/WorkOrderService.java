package org.example.logisticsback.maintenance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.assets.AssetRepository;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderService {
    private static final Map<String, String> AVAILABILITY_LABELS = Map.of(
            "AVAILABLE", "可用", "IN_USE", "使用中", "UNAVAILABLE", "不可用");
    private final WorkOrderRepository repository;
    private final AssetRepository assets;

    public WorkOrderService(WorkOrderRepository repository, AssetRepository assets) {
        this.repository = repository;
        this.assets = assets;
    }

    public List<WorkOrder> list(String assetId, String status, String keyword) {
        return repository.search(assetId, status, keyword);
    }

    public WorkOrder get(String id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(404, "运维工单不存在"));
    }

    public List<WorkOrderEvent> events(String id) { get(id); return repository.events(id); }

    @Transactional
    public WorkOrder create(WorkOrderRequests.Create request, Long operatorId) {
        require(Set.of("LOW", "NORMAL", "URGENT").contains(request.priority()), "优先级不合法");
        var asset = assets.lockById(request.assetId())
                .orElseThrow(() -> new BusinessException(404, "报修资产不存在"));
        require("ACTIVE".equals(asset.lifecycleStatus()), "仅在册资产可以报修");
        if (repository.hasOpenOrder(asset.id())) {
            throw new BusinessException(409, "该资产已有未结束工单，请继续处理现有工单");
        }
        var id = UUID.randomUUID().toString();
        var code = "WO-" + id.substring(0, 12).toUpperCase();
        repository.insert(id, code, request, asset.availabilityStatus(), operatorId);
        assets.setAvailability(asset.id(), "MAINTENANCE");
        repository.addEvent(id, "REPORT", "报修：" + request.description().trim(), operatorId);
        assets.addEvent(asset.id(), "MAINTENANCE", "报修工单 " + code + "，资产进入检修状态", operatorId);
        return get(id);
    }

    @Transactional
    public WorkOrder act(String id, WorkOrderRequests.Action request, Long operatorId) {
        var initial = get(id);
        var asset = assets.lockById(initial.assetId())
                .orElseThrow(() -> new BusinessException(404, "工单关联资产不存在"));
        var order = get(id);
        if (request.version() == null || request.version() != order.version()) {
            throw new BusinessException(409, "工单已更新，请刷新后重试");
        }
        require("ACTIVE".equals(asset.lifecycleStatus()), "关联资产已停用，请先核实资产状态");
        var next = order.status();
        var assignee = order.assignee();
        var repairNotes = order.repairNotes();
        var acceptanceNotes = order.acceptanceNotes();
        String resolution = null;
        String description;
        switch (request.action()) {
            case "ASSIGN" -> {
                require(Set.of("REPORTED", "ASSIGNED").contains(order.status()), "当前工单不能派工");
                require(request.assignee() != null && !request.assignee().isBlank(), "请填写维修负责人");
                assignee = request.assignee().trim();
                next = "ASSIGNED";
                description = "派工给：" + assignee;
            }
            case "START" -> {
                require("ASSIGNED".equals(order.status()), "请先派工，再开始维修");
                next = "IN_PROGRESS";
                description = "开始维修";
            }
            case "SUBMIT" -> {
                require("IN_PROGRESS".equals(order.status()), "仅维修中的工单可以提交验收");
                repairNotes = requiredNotes(request.notes(), "请填写维修处理结果");
                next = "PENDING_ACCEPTANCE";
                description = "提交验收：" + repairNotes;
            }
            case "ACCEPT" -> {
                require("PENDING_ACCEPTANCE".equals(order.status()), "仅待验收工单可以验收");
                acceptanceNotes = requiredNotes(request.notes(), "请填写验收意见");
                require(request.resolutionAvailability() != null
                        && AVAILABILITY_LABELS.containsKey(request.resolutionAvailability()), "请选择验收后的资产使用状态");
                resolution = request.resolutionAvailability();
                next = "COMPLETED";
                description = "验收通过：" + acceptanceNotes + "；资产状态：" + AVAILABILITY_LABELS.get(resolution);
            }
            case "REJECT" -> {
                require("PENDING_ACCEPTANCE".equals(order.status()), "仅待验收工单可以退回返修");
                acceptanceNotes = requiredNotes(request.notes(), "请填写退回原因");
                next = "IN_PROGRESS";
                description = "退回返修：" + acceptanceNotes;
            }
            case "CANCEL" -> {
                require(Set.of("REPORTED", "ASSIGNED").contains(order.status()), "维修开始后不能取消，请完成维修与验收");
                acceptanceNotes = requiredNotes(request.notes(), "请填写取消原因");
                next = "CANCELLED";
                resolution = "MAINTENANCE".equals(order.previousAvailability()) ? "UNAVAILABLE" : order.previousAvailability();
                description = "取消：" + acceptanceNotes + "；资产状态：" + AVAILABILITY_LABELS.get(resolution);
            }
            default -> throw new BusinessException(400, "不支持的工单操作");
        }
        repository.transition(id, next, assignee, repairNotes, acceptanceNotes, resolution);
        repository.addEvent(id, request.action(), description, operatorId);
        if (resolution != null) {
            assets.setAvailability(asset.id(), resolution);
        }
        var assetDescription = "工单 " + order.code() + "：" + description;
        assets.addEvent(asset.id(), "MAINTENANCE", assetDescription.length() > 450
                ? assetDescription.substring(0, 450) + "…（详见工单）" : assetDescription, operatorId);
        return get(id);
    }

    @Transactional
    public WorkOrder edit(String id,WorkOrderRequests.Edit input,Long operator) {
        var initial=get(id); assets.lockById(initial.assetId()); var order=get(id);
        MaintenanceSupport.version(input.version(),order.version());
        require("REPORTED".equals(order.status()),"仅待派工工单可编辑报修资料，维修过程请通过处理记录补充");
        require(Set.of("LOW","NORMAL","URGENT").contains(input.priority()),"优先级不合法");
        repository.edit(id,input);
        repository.addEvent(id,"EDIT","编辑报修："+input.title()+"；优先级 "+input.priority(),operator);
        return get(id);
    }
    @Transactional
    public void delete(String id,int version,Long operator) {
        var initial=get(id); assets.lockById(initial.assetId()); var order=get(id);
        MaintenanceSupport.version(version,order.version());
        require("CANCELLED".equals(order.status()),"仅已取消的误报工单可移出列表，已完成维修必须保留");
        repository.addEvent(id,"DELETE","移除已取消工单，保留后台审计记录",operator);
        repository.softDelete(id);
    }

    private static String requiredNotes(String notes, String message) {
        require(notes != null && !notes.isBlank(), message);
        return notes.trim();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(400, message);
    }
}
