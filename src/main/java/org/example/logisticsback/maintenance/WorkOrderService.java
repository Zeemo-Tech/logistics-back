package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.approval.ApprovalRequests;
import org.example.logisticsback.approval.ApprovalService;
import org.example.logisticsback.assets.AssetRepository;
import org.example.logisticsback.common.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderService {
    private static final Map<String, String> AVAILABILITY_LABELS = Map.of(
            "AVAILABLE", "可用", "IN_USE", "使用中", "UNAVAILABLE", "不可用");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "URGENT");
    private static final Set<String> FAULT_TYPES = Set.of(
            "MECHANICAL", "ELECTRICAL", "OPERATION", "REFRIGERATION", "ELEVATOR", "FIRE", "OTHER");
    private final WorkOrderRepository repository;
    private final AssetRepository assets;
    private final ApprovalService approvals;
    private final ApplicationEventPublisher events;

    public WorkOrderService(WorkOrderRepository repository, AssetRepository assets,
                            ApprovalService approvals, ApplicationEventPublisher events) {
        this.repository = repository;
        this.assets = assets;
        this.approvals = approvals;
        this.events = events;
    }

    public List<WorkOrder> list(String assetId, String status, String keyword) {
        return repository.search(assetId, status, keyword);
    }

    public List<WorkOrder> list(String assetId, String status, String keyword, String approvalStatus) {
        return repository.search(assetId, status, keyword, approvalStatus);
    }

    public WorkOrder get(String id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(404, "运维工单不存在"));
    }

    public List<WorkOrderEvent> events(String id) { get(id); return repository.events(id); }

    @Transactional
    public WorkOrder create(WorkOrderRequests.Create request, Long operatorId) {
        require(PRIORITIES.contains(request.priority()), "优先级不合法");
        require(request.faultType() == null || request.faultType().isBlank()
                || FAULT_TYPES.contains(request.faultType()), "故障类型不合法");
        var asset = assets.lockById(request.assetId())
                .orElseThrow(() -> new BusinessException(404, "报修资产不存在"));
        require("ACTIVE".equals(asset.lifecycleStatus()), "仅在册资产可以报修");
        if (repository.hasOpenOrder(asset.id())) {
            throw new BusinessException(409, "该资产已有未结束工单，请继续处理现有工单");
        }
        boolean emergency = Boolean.TRUE.equals(request.emergency());
        boolean needsApproval = emergency || Boolean.TRUE.equals(request.needsApproval());
        var id = UUID.randomUUID().toString();
        var code = "WO-" + id.substring(0, 12).toUpperCase();
        var status = emergency ? "IN_PROGRESS" : needsApproval ? "PENDING_APPROVAL" : "REPORTED";
        var approvalStatus = needsApproval ? "PENDING" : "NONE";
        repository.insert(id, code, request, asset.availabilityStatus(), operatorId,
                repository.operatorName(operatorId), status, approvalStatus);
        assets.setAvailability(asset.id(), "MAINTENANCE");
        repository.addEvent(id, emergency ? "EMERGENCY_REPORT" : "REPORT",
                (emergency ? "紧急报修：" : "报修：") + request.description().trim(), operatorId);
        assets.addEvent(asset.id(), "MAINTENANCE", "报修工单 " + code + "，资产进入检修状态", operatorId);
        if (needsApproval) {
            approvals.create(new ApprovalRequests.Create("WORK_ORDER", id, request.title(),
                    request.reporterDepartmentId(), asset.id(), request.description(), request.repairPlan(),
                    request.quotedAmount(), null, Boolean.TRUE.equals(request.externalRepair()), emergency),
                    operatorId);
        }
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
        if ("REQUEST_APPROVAL".equals(request.action())) {
            require(Set.of("REPORTED", "APPROVAL_REJECTED").contains(order.status()),
                    "当前工单不能提交审核");
            require(!"PENDING".equals(order.approvalStatus()), "工单已在审批中");
            repository.updateApprovalFields(id, request.quotedAmount(), request.repairPlan());
            var current = get(id);
            var approval = approvals.create(new ApprovalRequests.Create("WORK_ORDER", id, current.title(),
                    current.reporterDepartmentId(), current.assetId(), current.description(),
                    current.repairPlan(), current.quotedAmount(), false, current.externalRepair(),
                    current.emergency()), operatorId);
            assets.setAvailability(asset.id(), "MAINTENANCE");
            repository.addEvent(id, "REQUEST_APPROVAL", "提交领导审核：" + approval.code(), operatorId);
            assets.addEvent(asset.id(), "MAINTENANCE", "工单 " + order.code() + " 提交领导审核", operatorId);
            return get(id);
        }
        var next = order.status();
        var assignee = order.assignee();
        var repairNotes = order.repairNotes();
        var acceptanceNotes = order.acceptanceNotes();
        String resolution = null;
        String description;
        switch (request.action()) {
            case "ASSIGN", "DISPATCH" -> {
                require(Set.of("REPORTED", "ASSIGNED", "PENDING_DISPATCH").contains(order.status()),
                        "当前工单不能派工");
                require(request.assignee() != null && !request.assignee().isBlank(), "请填写维修负责人");
                assignee = request.assignee().trim();
                next = "ASSIGNED";
                description = ("DISPATCH".equals(request.action()) ? "派单给：" : "派工给：") + assignee;
            }
            case "START" -> {
                require(Set.of("ASSIGNED", "PENDING_DISPATCH").contains(order.status())
                        || (order.emergency() && "PENDING_APPROVAL".equals(order.status())),
                        "请先派工或完成审批，再开始维修");
                require(request.actualArrival() != null, "请填写实际到场时间");
                require(request.siteTechnician() != null && !request.siteTechnician().isBlank(),
                        "请填写现场维修人员");
                repository.updateStartInfo(id, request.actualArrival(), request.siteTechnician().trim(),
                        request.sitePhone() == null ? "" : request.sitePhone().trim(),
                        request.siteNotes() == null ? "" : request.siteNotes().trim());
                next = "IN_PROGRESS";
                description = "开始维修；实际到场 " + request.actualArrival();
            }
            case "EMERGENCY_START" -> {
                require(order.emergency(), "仅紧急抢修工单可以先行抢修");
                require(Set.of("PENDING_APPROVAL", "REPORTED").contains(order.status()), "当前工单不能发起抢修");
                next = "IN_PROGRESS";
                description = "紧急抢修先行实施，待补充审批";
            }
            case "PAUSE" -> {
                require("IN_PROGRESS".equals(order.status()), "仅维修中的工单可以暂停");
                repairNotes = requiredNotes(request.notes(), "请填写暂停原因");
                repository.pause(id, repairNotes);
                next = "PAUSED";
                description = "暂停维修：" + repairNotes;
            }
            case "RESUME" -> {
                require("PAUSED".equals(order.status()), "仅暂停中的工单可以恢复维修");
                repository.resume(id);
                next = "IN_PROGRESS";
                description = "恢复维修";
            }
            case "TRANSFER" -> {
                require(Set.of("ASSIGNED", "IN_PROGRESS", "PAUSED").contains(order.status()),
                        "当前工单不能转派");
                require(request.assignee() != null && !request.assignee().isBlank(), "请填写转派人员");
                assignee = request.assignee().trim();
                next = "ASSIGNED";
                description = "转派给：" + assignee;
            }
            case "EXTEND" -> {
                require(Set.of("ASSIGNED", "IN_PROGRESS", "PAUSED").contains(order.status()),
                        "当前工单不能延期");
                require(request.expectedCompletion() != null, "请选择延期后的预计完成时间");
                repository.extend(id, request.expectedCompletion());
                description = "维修延期至：" + request.expectedCompletion();
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
                        && AVAILABILITY_LABELS.containsKey(request.resolutionAvailability()),
                        "请选择验收后的资产使用状态");
                resolution = request.resolutionAvailability();
                next = "COMPLETED";
                description = "验收通过：" + acceptanceNotes + "；资产状态：" + AVAILABILITY_LABELS.get(resolution);
            }
            case "REJECT" -> {
                require("PENDING_ACCEPTANCE".equals(order.status()), "仅待验收工单可以退回返修");
                acceptanceNotes = requiredNotes(request.notes(), "请填写退回原因");
                next = "ACCEPTANCE_REJECTED";
                description = "验收不通过：" + acceptanceNotes;
            }
            case "RESTART" -> {
                require("ACCEPTANCE_REJECTED".equals(order.status()), "仅验收不通过的工单可以重新维修");
                next = "IN_PROGRESS";
                description = "验收不通过后重新维修";
            }
            case "CANCEL" -> {
                require(Set.of("REPORTED", "ASSIGNED", "PENDING_APPROVAL", "PENDING_DISPATCH", "APPROVAL_REJECTED")
                        .contains(order.status()), "维修开始后不能取消，请完成维修与验收");
                acceptanceNotes = requiredNotes(request.notes(), "请填写取消原因");
                next = "CANCELLED";
                resolution = "MAINTENANCE".equals(order.previousAvailability())
                        ? "UNAVAILABLE" : order.previousAvailability();
                description = "取消：" + acceptanceNotes + "；资产状态：" + AVAILABILITY_LABELS.get(resolution);
            }
            default -> throw new BusinessException(400, "不支持的工单操作");
        }
        repository.transition(id, next, assignee, repairNotes, acceptanceNotes, resolution,
                request.laborCost(), request.partsCost(), request.downtimeMinutes());
        repository.addEvent(id, request.action(), description, operatorId);
        if (resolution != null) {
            assets.setAvailability(asset.id(), resolution);
        }
        if ("COMPLETED".equals(next)) {
            events.publishEvent(new WorkOrderCompletedEvent(id, asset.id()));
        }
        var assetDescription = "工单 " + order.code() + "：" + description;
        assets.addEvent(asset.id(), "MAINTENANCE", assetDescription.length() > 450
                ? assetDescription.substring(0, 450) + "…（详见工单）" : assetDescription, operatorId);
        return get(id);
    }

    @Transactional
    public WorkOrder updateRepair(String id, WorkOrderRequests.Repair input, Long operator) {
        var order = get(id);
        assets.lockById(order.assetId());
        MaintenanceSupport.version(input.version(), order.version());
        require(!Set.of("COMPLETED", "CANCELLED").contains(order.status()), "已结束工单不能修改维修信息");
        boolean external = input.externalRepair() == null ? order.externalRepair() : input.externalRepair();
        repository.updateRepair(id, input, external);
        repository.addEvent(id, "REPAIR_UPDATE", external ? "更新外协维修与费用信息" : "更新维修方案与费用信息", operator);
        return get(id);
    }

    @Transactional
    public WorkOrder addPart(String id, WorkOrderRequests.Part input, Long operator) {
        var order = get(id);
        require(!Set.of("COMPLETED", "CANCELLED").contains(order.status()), "已结束工单不能添加配件");
        repository.addPart(id, input);
        repository.addEvent(id, "PART_ADD", "登记配件：" + input.name() + " × " + input.quantity(), operator);
        return get(id);
    }

    @Transactional
    public WorkOrder removePart(String id, String partId, Long operator) {
        get(id);
        repository.removePart(id, partId);
        repository.addEvent(id, "PART_REMOVE", "移除配件记录 " + partId, operator);
        return get(id);
    }

    public List<WorkOrderPart> parts(String id) {
        get(id);
        return repository.parts(id);
    }

    @Transactional
    public WorkOrder edit(String id,WorkOrderRequests.Edit input,Long operator) {
        var initial=get(id); assets.lockById(initial.assetId()); var order=get(id);
        MaintenanceSupport.version(input.version(),order.version());
        require(Set.of("REPORTED", "APPROVAL_REJECTED").contains(order.status()),
                "仅待审核或审批驳回的工单可编辑报修资料，维修过程请通过处理记录补充");
        require(PRIORITIES.contains(input.priority()),"优先级不合法");
        require(input.faultType() == null || input.faultType().isBlank()
                || FAULT_TYPES.contains(input.faultType()), "故障类型不合法");
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
