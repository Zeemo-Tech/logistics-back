package org.example.logisticsback.maintenance;

import static org.example.logisticsback.maintenance.MaintenanceSupport.*;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenancePlanService {
    private static final String SELECT = "select p.*,a.code asset_code,a.name asset_name,a.category from maintenance_plans p join assets a on a.id=p.asset_id";
    private final MaintenanceSupport db;
    private final WorkOrderService orders;
    public MaintenancePlanService(MaintenanceSupport db, WorkOrderService orders) { this.db=db; this.orders=orders; }
    public List<Plan> list() { return db.jdbc.query(SELECT+" order by p.next_due_date,p.code", DataClassRowMapper.newInstance(Plan.class)); }
    public Plan get(String id) {
        return db.jdbc.query(SELECT+" where p.id=?", DataClassRowMapper.newInstance(Plan.class),id).stream()
                .findFirst().orElseThrow(() -> new BusinessException(404,"巡检计划不存在"));
    }
    private Plan lock(String id) { var p=get(id); db.equipment(p.assetId(),false); db.jdbc.queryForList("select id from maintenance_plans where id=? for update",id); return get(id); }
    public List<Execution> executions(String id) { get(id); return db.jdbc.query("select * from maintenance_plan_executions where plan_id=? order by sequence_id desc",DataClassRowMapper.newInstance(Execution.class),id); }
    public List<Audit> history(String id) { get(id); return db.history("PLAN",id); }
    @Transactional
    public Plan save(String id,PlanInput input,Long operator) {
        require(Set.of("PREVENTIVE","INSPECTION").contains(input.planType()),"计划类型不合法");
        var method = text(input.maintenanceMethod()).isEmpty() ? "INTERNAL" : input.maintenanceMethod();
        require(Set.of("INTERNAL","EXTERNAL").contains(method),"维保方式不合法");
        int reminderDays = input.reminderDays()==null ? 30 : input.reminderDays();
        require(reminderDays>=0 && reminderDays<=365,"提前提醒天数需在 0 到 365 之间");
        var asset=db.equipment(input.assetId(),true);
        if(id==null) {
            id=id();
            db.jdbc.update("""
                    insert into maintenance_plans(id,code,name,asset_id,plan_type,frequency,interval_days,next_due_date,owner,checklist,status,notes,reminder_days,maintenance_method,needs_approval,estimated_cost)
                    values(?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,?,?)
                    """,id,"MP-"+id.substring(0,12).toUpperCase(),input.name().trim(),asset.id(),input.planType(),input.frequency().trim(),input.intervalDays(),input.nextDueDate(),input.owner().trim(),input.checklist().trim(),text(input.notes()),reminderDays,method,Boolean.TRUE.equals(input.needsApproval()),input.estimatedCost());
            db.audit("PLAN",id,"CREATE","新建计划："+input.name(),operator);
        } else {
            var old=lock(id); version(input.version(),old.version());
            require(old.assetId().equals(input.assetId()) && old.planType().equals(input.planType()),"计划关联设备和类型不可更换，请新建计划");
            db.jdbc.update("""
                    update maintenance_plans set name=?,frequency=?,interval_days=?,next_due_date=?,owner=?,checklist=?,notes=?,reminder_days=?,maintenance_method=?,needs_approval=?,estimated_cost=?,version=version+1,updated_at=current_timestamp where id=?
                    """,input.name().trim(),input.frequency().trim(),input.intervalDays(),input.nextDueDate(),input.owner().trim(),input.checklist().trim(),text(input.notes()),reminderDays,method,Boolean.TRUE.equals(input.needsApproval()),input.estimatedCost(),id);
            db.audit("PLAN",id,"UPDATE","更新计划；执行日期 "+old.nextDueDate()+" → "+input.nextDueDate()+"；周期 "+old.intervalDays()+" → "+input.intervalDays()+" 天；负责人 "+old.owner()+" → "+input.owner(),operator);
        }
        db.syncDates(asset.id()); return get(id);
    }
    @Transactional
    public Plan state(String id,StateInput input,Long operator) {
        var plan=lock(id); version(input.version(),plan.version());
        require(Set.of("ACTIVE","PAUSED").contains(input.status()) && !input.status().equals(plan.status()),"计划状态变更不合法");
        if("ACTIVE".equals(input.status())) db.equipment(plan.assetId(),true);
        db.jdbc.update("update maintenance_plans set status=?,version=version+1,updated_at=current_timestamp where id=?",input.status(),id);
        db.audit("PLAN",id,input.status(),input.reason(),operator); db.syncDates(plan.assetId()); return get(id);
    }
    @Transactional
    public Execution execute(String id,PlanExecute input,Long operator) {
        var plan=lock(id); version(input.version(),plan.version()); db.equipment(plan.assetId(),true);
        require("ACTIVE".equals(plan.status()),"暂停的计划不能执行");
        require(Set.of("NORMAL","ABNORMAL").contains(input.result()),"执行结果不合法");
        // A second execution in the same cycle requires the newly returned version and a due date that has arrived.
        require(executions(id).isEmpty() || !plan.nextDueDate().isAfter(LocalDate.now()),"本周期已执行，请到下次执行日期再登记");
        String orderId=null;
        boolean needsOrder = "ABNORMAL".equals(input.result()) || plan.needsApproval();
        if(needsOrder) {
            var existing=orders.list(plan.assetId(),null,null).stream()
                    .filter(o -> !Set.of("COMPLETED","CANCELLED","APPROVAL_REJECTED").contains(o.status())).findFirst();
            orderId=existing.isPresent() ? existing.get().id() : orders.create(new WorkOrderRequests.Create(
                    plan.assetId(),"计划维保："+plan.name().substring(0,Math.min(plan.name().length(),100)),
                    input.notes(),"NORMAL","OTHER",null,false,null,false,plan.needsApproval(),
                    "EXTERNAL".equals(plan.maintenanceMethod()),null,null,null,null,null,null,null,
                    plan.checklist(),null,null,plan.estimatedCost(),null),operator).id();
        }
        var executionId=id();
        db.jdbc.update("insert into maintenance_plan_executions(id,plan_id,scheduled_date,executor,result,checklist_result,notes,work_order_id) values(?,?,?,?,?,?,?,?)",executionId,id,plan.nextDueDate(),input.executor().trim(),input.result(),input.checklistResult().trim(),input.notes().trim(),orderId);
        db.jdbc.update("update maintenance_plans set next_due_date=?,version=version+1,updated_at=current_timestamp where id=?",LocalDate.now().plusDays(plan.intervalDays()),id);
        db.audit("PLAN",id,"EXECUTE",input.executor()+"；"+input.result()+"；"+input.notes(),operator);
        if("PREVENTIVE".equals(plan.planType())) {
            db.jdbc.update("update assets set last_maintenance_date=? where id=?",LocalDate.now(),plan.assetId());
        } else {
            db.jdbc.update("update assets set last_inspection_date=? where id=?",LocalDate.now(),plan.assetId());
        }
        db.assets.addEvent(plan.assetId(),"MAINTENANCE","计划 "+plan.code()+" 执行结果："+input.result()+"；详见计划执行记录",operator);
        db.syncDates(plan.assetId());
        return executions(id).stream().filter(e -> executionId.equals(e.id())).findFirst().orElseThrow();
    }
    @Transactional
    public void delete(String id,int version,Long operator) {
        var p=lock(id); version(version,p.version()); require(executions(id).isEmpty(),"计划已有巡检/保养记录，请暂停计划以保留历史");
        Integer referenced = db.jdbc.queryForObject("""
                select count(*) from maintenance_plan_tasks where plan_id = ? and (
                    approval_id is not null or work_order_id is not null
                    or status in ('PENDING_APPROVAL', 'APPROVED', 'COMPLETED'))
                """, Integer.class, id);
        if (referenced != null && referenced > 0) {
            throw new BusinessException(409, "计划已进入审批或维修流程，不能删除，请暂停计划以保留历史");
        }
        db.jdbc.update("delete from maintenance_plan_tasks where plan_id = ?", id);
        db.audit("PLAN",id,"DELETE","删除误建计划："+p.code()+" / "+p.name(),operator);
        db.jdbc.update("delete from maintenance_plans where id=?",id); db.syncDates(p.assetId());
    }
}
