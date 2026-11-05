package org.example.logisticsback.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.example.logisticsback.assets.AssetService;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EquipmentMaintenanceTests {
    @Autowired MaintenancePlanService plans;
    @Autowired ComplianceService compliance;
    @Autowired UtilityMeterService meters;
    @Autowired WorkOrderService orders;
    @Autowired AssetService assets;
    @Autowired JdbcTemplate jdbc;
    private final LocalDate today=LocalDate.now();

    private String equipment(String subcategory) {
        var id=UUID.randomUUID().toString();
        jdbc.update("""
                insert into assets(id,code,kind,name,category,subcategory,ownership,owner_name,site,location,custodian,lifecycle_status,availability_status)
                values(?,?,'FACILITY','测试设备','水电设施',?,'OWNED','测试园区','测试园区','A区','工程班组','ACTIVE','IN_USE')
                """,id,"TEST-"+id.substring(0,10),subcategory);
        return id;
    }
    private PlanInput planInput(String assetId,Integer version,LocalDate due) {
        return new PlanInput("配电房维保",assetId,"PREVENTIVE","每30天",30,due,"王工","核查接线、温升与保护装置","测试",version);
    }
    private ComplianceInput complianceInput(String assetId,Integer version,LocalDate due) {
        return new ComplianceInput("计量检定","CALIBRATION",assetId,"A区","计量检测机构","李工",due,"年度检定",version);
    }
    private ComplianceAction action(Compliance c,String type,LocalDate next) {
        return new ComplianceAction(c.version(),type,today,"计量检测机构","RPT-2026-001",next,"完成现场检查并登记结果");
    }
    private MeterInput meterInput(String assetId,Integer version) {
        return new MeterInput("M-"+assetId.substring(0,8),assetId,"A区水表","水表","A区给水主管","抄表员",new BigDecimal("2"),30,new BigDecimal("100"),today.minusDays(3),today.plusDays(27),version);
    }
    private ReadingInput reading(UtilityMeter m,String value,LocalDate date) {
        return new ReadingInput(m.version(),new BigDecimal(value),date,date.plusDays(30),"NORMAL","现场核对底数");
    }

    @Test void planPersistsEditsPausesAndDeletesOnlyWithoutExecutions() {
        var id=equipment("配电柜");
        var p=plans.save(null,planInput(id,null,today.minusDays(1)),1L);
        assertEquals(today.minusDays(1),assets.get(id).maintenanceDueDate());
        var original=p;
        p=plans.save(p.id(),planInput(id,p.version(),today.plusDays(2)),1L);
        assertEquals(409,assertThrows(BusinessException.class,()->plans.save(original.id(),planInput(id,original.version(),today),1L)).getCode());
        p=plans.state(p.id(),new StateInput(p.version(),"PAUSED","停产维护"),1L);
        assertNull(assets.get(id).maintenanceDueDate());
        var paused=p;
        assertThrows(BusinessException.class,()->plans.execute(paused.id(),new PlanExecute(paused.version(),"王工","NORMAL","全部合格","巡检完成"),1L));
        assertEquals(3,plans.history(p.id()).size());
        plans.delete(p.id(),p.version(),1L);
        assertThrows(BusinessException.class,()->plans.get(paused.id()));
    }
    @Test void executionAdvancesDateAndRetainsHistory() {
        var p=plans.save(null,planInput(equipment("电梯"),null,today.minusDays(3)),1L);
        var e=plans.execute(p.id(),new PlanExecute(p.version(),"王工","NORMAL","安全装置正常","维保完成"),1L);
        assertNull(e.workOrderId());
        var next=plans.get(p.id());
        assertEquals(today.plusDays(30),next.nextDueDate());
        assertEquals(next.nextDueDate(),assets.get(p.assetId()).maintenanceDueDate());
        assertThrows(BusinessException.class,()->plans.execute(next.id(),new PlanExecute(next.version(),"王工","NORMAL","正常","重复执行"),1L));
        assertThrows(BusinessException.class,()->plans.delete(next.id(),next.version(),1L));
        assertEquals(1,plans.executions(p.id()).size());
    }
    @Test void abnormalExecutionCreatesOneOrderAndSubsequentPlansLinkIt() {
        var asset=equipment("压缩机组");
        var a=plans.save(null,planInput(asset,null,today),1L);
        var b=plans.save(null,planInput(asset,null,today),1L);
        var first=plans.execute(a.id(),new PlanExecute(a.version(),"王工","ABNORMAL","运行电流偏高","轴承异响"),1L);
        var second=plans.execute(b.id(),new PlanExecute(b.version(),"李工","ABNORMAL","温升异常","检查轴承"),1L);
        assertEquals(first.workOrderId(),second.workOrderId());
        assertEquals(1,orders.list(asset,null,null).size());
        assertEquals("MAINTENANCE",assets.get(asset).availabilityStatus());
    }
    @Test void complianceApplicationPassAndRenewalSyncAssetDate() {
        var asset=equipment("地磅");
        var c=compliance.save(null,complianceInput(asset,null,today.plusDays(5)),1L);
        c=compliance.save(c.id(),complianceInput(asset,c.version(),today.plusDays(6)),1L);
        assertEquals(today.plusDays(6),assets.get(asset).inspectionDueDate());
        c=compliance.act(c.id(),action(c,"APPLY",null),1L);
        var applied=c;
        assertThrows(BusinessException.class,()->compliance.act(applied.id(),action(applied,"PASS",today.minusDays(1)),1L));
        c=compliance.act(c.id(),action(c,"PASS",today.plusYears(1)),1L);
        assertEquals("ACTIVE",c.status());
        assertEquals(today.plusYears(1),assets.get(asset).inspectionDueDate());
        assertEquals(2,compliance.events(c.id()).size());
        var complete=c;
        assertThrows(BusinessException.class,()->compliance.delete(complete.id(),complete.version(),1L));
        assertThrows(BusinessException.class,()->compliance.save(complete.id(),complianceInput(asset,complete.version(),today.plusYears(2)),1L));
    }
    @Test void failedInspectionRequiresRetestAndArchivePreservesRisk() {
        var c=compliance.save(null,complianceInput(equipment("电梯"),null,today),1L);
        c=compliance.act(c.id(),action(c,"APPLY",null),1L);
        c=compliance.act(c.id(),action(c,"FAIL",today.plusDays(7)),1L);
        assertEquals("RECTIFY",c.status());
        c=compliance.act(c.id(),action(c,"ARCHIVE",null),1L);
        assertNull(assets.get(c.assetId()).inspectionDueDate());
        c=compliance.act(c.id(),action(c,"REOPEN",null),1L);
        assertEquals("RECTIFY",c.status());
        c=compliance.act(c.id(),action(c,"APPLY",null),1L);
        c=compliance.act(c.id(),action(c,"PASS",today.plusYears(1)),1L);
        assertEquals("ACTIVE",c.status());
        assertEquals(6,compliance.events(c.id()).size());
    }
    @Test void deleteUnusedComplianceAndRejectIllegalTransitions() {
        var c=compliance.save(null,complianceInput(null,null,today),1L);
        assertThrows(BusinessException.class,()->compliance.act(c.id(),action(c,"PASS",today.plusYears(1)),1L));
        compliance.delete(c.id(),c.version(),1L);
        assertThrows(BusinessException.class,()->compliance.get(c.id()));
    }
    @Test void meterCalculatesSnapshotsAndCorrectionRetainsVoidedOriginal() {
        var m=meters.save(null,meterInput(equipment("水表"),null),1L);
        var first=meters.record(m.id(),null,reading(m,"110",today.minusDays(2)),1L);
        assertEquals(0,new BigDecimal("20").compareTo(first.consumption()));
        m=meters.get(m.id());
        var second=meters.record(m.id(),null,reading(m,"140",today.minusDays(1)),1L);
        m=meters.get(m.id());
        var latest=m;
        assertThrows(BusinessException.class,()->meters.record(latest.id(),first.id(),reading(latest,"120",first.readDate()),1L));
        var corrected=meters.record(m.id(),second.id(),reading(m,"130",second.readDate()),1L);
        assertEquals(second.id(),corrected.correctionOf());
        assertEquals(0,new BigDecimal("40").compareTo(corrected.consumption()));
        assertEquals("VOID",meters.readings(m.id()).stream().filter(r->r.id().equals(second.id())).findFirst().orElseThrow().status());
        assertEquals(corrected.id(),meters.readings(m.id()).get(0).id());
        assertEquals(3,meters.readings(m.id()).size());
        assertEquals(0,new BigDecimal("40").compareTo(meters.get(m.id()).consumption()));
        var current=meters.get(m.id());
        assertThrows(BusinessException.class,()->meters.delete(current.id(),current.version(),1L));
    }
    @Test void meterRejectsDuplicateBindingBadDatesDecreasingAndUnexplainedSpikes() {
        var asset=equipment("水表");var m=meters.save(null,meterInput(asset,null),1L);
        assertThrows(BusinessException.class,()->meters.save(null,meterInput(asset,null),1L));
        assertThrows(BusinessException.class,()->meters.record(m.id(),null,reading(m,"99",today.minusDays(2)),1L));
        assertThrows(BusinessException.class,()->meters.record(m.id(),null,reading(m,"110",today.plusDays(1)),1L));
        meters.record(m.id(),null,reading(m,"110",today.minusDays(2)),1L);
        var next=meters.get(m.id());
        assertThrows(BusinessException.class,()->meters.record(next.id(),null,new ReadingInput(next.version(),new BigDecimal("1000"),today.minusDays(1),today.plusDays(29),"NORMAL",""),1L));
        var spike=meters.record(next.id(),null,reading(next,"1000",today.minusDays(1)),1L);
        assertEquals("ALERT",spike.status());
    }
    @Test void unusedMeterCanBeEditedStoppedRestoredAndDeleted() {
        var id=equipment("水表");var m=meters.save(null,meterInput(id,null),1L);
        m=meters.save(m.id(),meterInput(id,m.version()),1L);
        m=meters.state(m.id(),new StateInput(m.version(),"INACTIVE","停用核对"),1L);
        var inactive=m;
        assertThrows(BusinessException.class,()->meters.record(inactive.id(),null,reading(inactive,"110",today.minusDays(2)),1L));
        m=meters.state(m.id(),new StateInput(m.version(),"ACTIVE","恢复"),1L);
        assertEquals(4,meters.history(m.id()).size());
        meters.delete(m.id(),m.version(),1L);
        assets.delete(id,1L);
        assertThrows(BusinessException.class,()->assets.get(id));
    }
    @Test void reportedOrderCanBeEditedAndCancelledOrderRemovedWithoutErasingAudit() {
        var id=equipment("照明");
        var w=orders.create(new WorkOrderRequests.Create(id,"照明故障","路灯不亮","NORMAL"),1L);
        var first=w;
        w=orders.edit(w.id(),new WorkOrderRequests.Edit(w.version(),"主干道路灯不亮","三盏路灯不亮","URGENT"),1L);
        assertEquals("URGENT",w.priority());
        assertEquals(409,assertThrows(BusinessException.class,()->orders.edit(first.id(),new WorkOrderRequests.Edit(first.version(),"重复修改","描述","NORMAL"),1L)).getCode());
        w=orders.act(w.id(),new WorkOrderRequests.Action("ASSIGN",w.version(),"工程班",null,null),1L);
        var assigned=w;
        assertThrows(BusinessException.class,()->orders.edit(assigned.id(),new WorkOrderRequests.Edit(assigned.version(),"修改","描述","NORMAL"),1L));
        w=orders.act(w.id(),new WorkOrderRequests.Action("CANCEL",w.version(),null,"核查为误报",null),1L);
        orders.delete(w.id(),w.version(),1L);
        assertTrue(orders.list(id,null,null).isEmpty());
        assertEquals(5,jdbc.queryForObject("select count(*) from maintenance_work_order_events where work_order_id=?",Integer.class,w.id()));
        assertThrows(BusinessException.class,()->assets.delete(id,1L));
    }
}
