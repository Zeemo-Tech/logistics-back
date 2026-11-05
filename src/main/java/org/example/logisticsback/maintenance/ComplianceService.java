package org.example.logisticsback.maintenance;

import static org.example.logisticsback.maintenance.MaintenanceSupport.*;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceService {
    private static final String SELECT="select c.*,coalesce(a.name,'区域事项') asset_name from safety_compliance_items c left join assets a on c.asset_id=a.id";
    private final MaintenanceSupport db;
    public ComplianceService(MaintenanceSupport db) { this.db=db; }
    public List<Compliance> list() { return db.jdbc.query(SELECT+" order by c.due_date,c.code",DataClassRowMapper.newInstance(Compliance.class)); }
    public Compliance get(String id) {
        return db.jdbc.query(SELECT+" where c.id=?",DataClassRowMapper.newInstance(Compliance.class),id).stream().findFirst().orElseThrow(() -> new BusinessException(404,"合规事项不存在"));
    }
    private Compliance lock(String id) {
        var c=get(id); if(c.assetId()!=null) db.equipment(c.assetId(),false);
        db.jdbc.queryForList("select id from safety_compliance_items where id=? for update",id); return get(id);
    }
    public List<ComplianceEvent> events(String id) { get(id); return db.jdbc.query("select * from safety_compliance_events where item_id=? order by sequence_id desc",DataClassRowMapper.newInstance(ComplianceEvent.class),id); }
    public List<Audit> history(String id) { get(id); return db.history("COMPLIANCE",id); }
    @Transactional
    public Compliance save(String id,ComplianceInput input,Long operator) {
        require(Set.of("STATUTORY_INSPECTION","CALIBRATION","FIRE_SAFETY","ELECTRICAL_SAFETY","WATER_SAFETY","OTHER").contains(input.itemType()),"合规事项类型不合法");
        var assetId=text(input.assetId()).isEmpty()?null:input.assetId();
        if(assetId!=null) db.equipment(assetId,true);
        if(id==null) {
            id=id();
            db.jdbc.update("""
                    insert into safety_compliance_items(id,code,name,item_type,asset_id,location,authority,owner,due_date,status,notes)
                    values(?,?,?,?,?,?,?,?,?,'ACTIVE',?)
                    """,id,"SC-"+id.substring(0,12).toUpperCase(),input.name().trim(),input.itemType(),assetId,input.location().trim(),input.authority().trim(),input.owner().trim(),input.dueDate(),text(input.notes()));
            db.audit("COMPLIANCE",id,"CREATE","登记合规事项："+input.name(),operator);
        } else {
            var old=lock(id); version(input.version(),old.version());
            require(!"ARCHIVED".equals(old.status()),"归档事项请先恢复再编辑");
            require(Objects.equals(old.assetId(),assetId) && old.itemType().equals(input.itemType()),"关联设备和合规类型不可变更，请新建事项");
            require(events(id).isEmpty() || old.dueDate().equals(input.dueDate()),"已有报检记录，到期日期请通过检验合格续期更新");
            db.jdbc.update("""
                    update safety_compliance_items set name=?,location=?,authority=?,owner=?,due_date=?,notes=?,version=version+1,updated_at=current_timestamp where id=?
                    """,input.name().trim(),input.location().trim(),input.authority().trim(),input.owner().trim(),input.dueDate(),text(input.notes()),id);
            db.audit("COMPLIANCE",id,"UPDATE","编辑事项；到期 "+old.dueDate()+" → "+input.dueDate()+"；负责人 "+old.owner()+" → "+input.owner(),operator);
        }
        db.syncDates(assetId); return get(id);
    }
    @Transactional
    public Compliance act(String id,ComplianceAction input,Long operator) {
        var c=lock(id); version(input.version(),c.version()); pastDate(input.eventDate());
        require(c.lastInspectionDate()==null || !input.eventDate().isBefore(c.lastInspectionDate()),"业务日期不能早于上次检验日期");
        String status=c.status(), result="",report=c.reportNumber();
        var due=c.dueDate(); var last=c.lastInspectionDate(); var next=c.nextDueDate();
        switch(input.action()) {
            case "APPLY" -> {
                require(Set.of("ACTIVE","RECTIFY").contains(status),"仅待报检或待整改事项可申请检验");
                if(c.assetId()!=null) db.equipment(c.assetId(),true);
                status="APPLIED"; result="已报检";
            }
            case "PASS", "FAIL" -> {
                require("APPLIED".equals(status),"请先登记报检，再录入检验结果");
                var apply=events(id).stream().filter(e -> "APPLY".equals(e.action())).findFirst().orElseThrow();
                require(!input.eventDate().isBefore(apply.eventDate()),"检验日期不能早于报检日期");
                require(!text(input.reportNumber()).isEmpty(),"请填写检验报告编号");
                require(input.nextDueDate()!=null && input.nextDueDate().isAfter(LocalDate.now()) && input.nextDueDate().isAfter(input.eventDate()),"请填写晚于今天的下次检验日期或整改期限");
                report=input.reportNumber().trim(); last=input.eventDate(); next=input.nextDueDate();
                if("PASS".equals(input.action())) { status="ACTIVE"; due=next; result="合格"; }
                else { status="RECTIFY"; result="不合格，待整改复检"; }
            }
            case "ARCHIVE" -> { require(!"ARCHIVED".equals(status),"事项已归档"); status="ARCHIVED"; result="已归档"; }
            case "REOPEN" -> {
                require("ARCHIVED".equals(status),"事项未归档"); if(c.assetId()!=null) db.equipment(c.assetId(),true);
                var previous=events(id).stream().filter(e -> Set.of("APPLY","PASS","FAIL").contains(e.action())).findFirst();
                status=previous.map(e -> "FAIL".equals(e.action())?"RECTIFY":"APPLY".equals(e.action())?"APPLIED":"ACTIVE").orElse("ACTIVE");
                result="恢复管理";
            }
            default -> throw new BusinessException(400,"不支持的合规操作");
        }
        db.jdbc.update("""
                update safety_compliance_items set status=?,due_date=?,authority=?,report_number=?,last_inspection_date=?,next_due_date=?,version=version+1,updated_at=current_timestamp where id=?
                """,status,due,input.authority().trim(),report,last,next,id);
        db.jdbc.update("insert into safety_compliance_events(id,item_id,action,event_date,authority,report_number,result,next_due_date,notes) values(?,?,?,?,?,?,?,?,?)",id(),id,input.action(),input.eventDate(),input.authority().trim(),text(input.reportNumber()),result,input.nextDueDate(),input.notes().trim());
        db.audit("COMPLIANCE",id,input.action(),result+"；"+input.notes(),operator);
        if(c.assetId()!=null) db.assets.addEvent(c.assetId(),"MAINTENANCE","合规事项 "+c.code()+"："+result+"；详见检验记录",operator);
        db.syncDates(c.assetId()); return get(id);
    }
    @Transactional
    public void delete(String id,int suppliedVersion,Long operator) {
        var c=lock(id); version(suppliedVersion,c.version()); require(events(id).isEmpty(),"事项已有报检或检验记录，请归档以保留历史");
        db.audit("COMPLIANCE",id,"DELETE","删除误建事项："+c.code()+" / "+c.name(),operator);
        db.jdbc.update("delete from safety_compliance_items where id=?",id); db.syncDates(c.assetId());
    }
}
