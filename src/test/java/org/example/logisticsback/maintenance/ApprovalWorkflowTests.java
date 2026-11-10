package org.example.logisticsback.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.example.logisticsback.approval.ApprovalRequests;
import org.example.logisticsback.approval.ApprovalService;
import org.example.logisticsback.approval.MaintenanceApproval;
import org.example.logisticsback.assets.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ApprovalWorkflowTests {
    @Autowired private WorkOrderService workOrders;
    @Autowired private ApprovalService approvals;
    @Autowired private RepairArchiveService archive;
    @Autowired private StatisticsService statistics;
    @Autowired private AssetRepository assets;

    @Test
    void approvalCreatesDispatchableOrderAndCompletedRepairEntersArchive() {
        var asset = assets.search(new org.example.logisticsback.assets.AssetQuery("EQUIPMENT", null, null, null, null))
                .stream().filter(item -> "ACTIVE".equals(item.lifecycleStatus())).findFirst().orElseThrow();
        var created = workOrders.create(new WorkOrderRequests.Create(asset.id(), "审批流程测试",
                "设备运行异常，需要维修", "NORMAL", "MECHANICAL", null, false, null,
                false, true, false, null, null, null, null, null, null, null, null, null, null, null), 1L);
        assertEquals("PENDING_APPROVAL", created.status());
        assertEquals("PENDING", created.approvalStatus());
        assertNotNull(created.approvalId());

        var approval = approvals.get(created.approvalId());
        assertEquals("PENDING", approval.status());
        approvals.act(approval.id(), new ApprovalRequests.Action("APPROVE", approval.version(), null, "同意维修"), 1L);

        var approved = workOrders.get(created.id());
        assertEquals("PENDING_DISPATCH", approved.status());
        assertEquals("APPROVED", approved.approvalStatus());

        var dispatched = workOrders.act(created.id(), new WorkOrderRequests.Action("DISPATCH",
                approved.version(), "外协维修组", "派单", null), 1L);
        assertEquals("ASSIGNED", dispatched.status());
        var started = workOrders.act(created.id(), new WorkOrderRequests.Action("START",
                dispatched.version(), null, null, null, null, null, null, null, null, null, null,
                LocalDateTime.now(), "外协维修员", null, null), 1L);
        assertEquals("IN_PROGRESS", started.status());
        workOrders.addPart(created.id(), new WorkOrderRequests.Part("压缩机密封圈", "O 型",
                new BigDecimal("2"), "个", new BigDecimal("120"), "备件库", "", ""), 1L);
        var submitted = workOrders.act(created.id(), new WorkOrderRequests.Action("SUBMIT",
                workOrders.get(created.id()).version(), null, "更换密封圈并试运行正常", null), 1L);
        assertEquals("PENDING_ACCEPTANCE", submitted.status());
        var accepted = workOrders.act(created.id(), new WorkOrderRequests.Action("ACCEPT",
                submitted.version(), null, "验收合格", "AVAILABLE", null, new BigDecimal("200"),
                null, 90, null), 1L);
        assertEquals("COMPLETED", accepted.status());
        assertTrue(accepted.totalCost().compareTo(new BigDecimal("440")) >= 0);

        var entry = archive.get(created.id());
        assertEquals("COMPLETED", entry.order().status());
        assertEquals(1, entry.parts().size());
        assertTrue(entry.totalCost().signum() > 0);

        var stats = statistics.overview();
        assertTrue(stats.metrics().equipmentTotal() > 0);
        assertTrue(stats.metrics().pendingApprovals() >= 0);
    }

    @Test
    void workOrderCanBeSubmittedForApprovalAndResubmittedAfterReject() {
        var asset = assets.search(new org.example.logisticsback.assets.AssetQuery("EQUIPMENT", null, null, null, null))
                .stream().filter(item -> "ACTIVE".equals(item.lifecycleStatus())).findFirst().orElseThrow();
        var order = workOrders.create(new WorkOrderRequests.Create(asset.id(), "待提交审核工单",
                "报修后先保存，再提交审核", "NORMAL"), 1L);
        assertEquals("REPORTED", order.status());
        var submitted = workOrders.act(order.id(), new WorkOrderRequests.Action("REQUEST_APPROVAL",
                order.version(), null, "请审核", null), 1L);
        assertEquals("PENDING_APPROVAL", submitted.status());
        assertEquals("PENDING", submitted.approvalStatus());
        var approval = approvals.get(submitted.approvalId());
        approvals.act(approval.id(), new ApprovalRequests.Action("REJECT", approval.version(),
                null, "请补充故障照片"), 1L);
        var rejected = workOrders.get(order.id());
        assertEquals("APPROVAL_REJECTED", rejected.status());
        var edited = workOrders.edit(order.id(), new WorkOrderRequests.Edit(rejected.version(),
                "补充资料后的工单", "已补充故障说明", "NORMAL"), 1L);
        var resubmitted = workOrders.act(edited.id(), new WorkOrderRequests.Action("REQUEST_APPROVAL",
                edited.version(), null, "已补充，重新提交", null), 1L);
        assertEquals("PENDING_APPROVAL", resubmitted.status());
        assertNotEquals(approval.id(), resubmitted.approvalId());
    }

    @Test
    void rejectedApprovalReleasesAssetAndAllowsReReport() {
        var asset = assets.search(new org.example.logisticsback.assets.AssetQuery("EQUIPMENT", null, null, null, null))
                .stream().filter(item -> "ACTIVE".equals(item.lifecycleStatus())).findFirst().orElseThrow();
        var availability = asset.availabilityStatus();
        var created = workOrders.create(new WorkOrderRequests.Create(asset.id(), "审批驳回测试",
                "测试驳回后释放设备", "NORMAL", null, null, false, null, false, true, false,
                null, null, null, null, null, null, null, null, null, null, null), 1L);
        var approval = approvals.get(created.approvalId());
        approvals.act(approval.id(), new ApprovalRequests.Action("REJECT", approval.version(), null,
                "信息不完整，请补充"), 1L);
        var rejected = workOrders.get(created.id());
        assertEquals("APPROVAL_REJECTED", rejected.status());
        assertEquals("REJECTED", rejected.approvalStatus());
        assertEquals(availability, assets.findById(asset.id()).orElseThrow().availabilityStatus());
        assertFalse(workOrders.list(null, null, null).stream()
                .anyMatch(order -> order.assetId().equals(asset.id())
                        && List.of("REPORTED", "PENDING_APPROVAL", "PENDING_DISPATCH", "ASSIGNED",
                        "IN_PROGRESS", "PAUSED", "PENDING_ACCEPTANCE").contains(order.status())
                        && !order.id().equals(created.id())));
    }
}
