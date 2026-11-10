package org.example.logisticsback.maintenance;

import static org.example.logisticsback.maintenance.MaintenanceSupport.*;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UtilityMeterService {
    private final MaintenanceSupport db;
    private final UtilityMeterRepository repository;
    public UtilityMeterService(MaintenanceSupport db,UtilityMeterRepository repository) { this.db=db; this.repository=repository; }
    public List<UtilityMeter> list() { return repository.list(); }
    public UtilityMeter get(String id) { return list().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(() -> new BusinessException(404,"水电表不存在")); }
    private UtilityMeter lock(String id) { var m=get(id); db.equipment(m.assetId(),false); db.jdbc.queryForList("select id from utility_meters where id=? for update",id); return get(id); }
    public List<Reading> readings(String id) { get(id); return db.jdbc.query("select * from utility_meter_readings where meter_id=? order by sequence_id desc",DataClassRowMapper.newInstance(Reading.class),id); }
    public List<Audit> history(String id) { get(id); return db.history("METER",id); }
    public ReadingPhoto readingPhoto(String meterId, String readingId) {
        get(meterId);
        var photo = db.jdbc.query("""
                select photo_name, photo_mime, photo_base64 from utility_meter_readings
                where id = ? and meter_id = ?
                """, rs -> rs.next() ? new ReadingPhoto(rs.getString("photo_name"),
                rs.getString("photo_mime"), rs.getString("photo_base64")) : null, readingId, meterId);
        if (photo == null) throw new BusinessException(404, "抄表照片不存在");
        return photo;
    }
    @Transactional
    public UtilityMeter save(String id,MeterInput input,Long operator) {
        require(Set.of("水表","电表").contains(input.kind()),"表计类型不合法");
        if(input.nextInspectionDate()!=null && input.lastInspectionDate()!=null) {
            require(input.nextInspectionDate().isAfter(input.lastInspectionDate()),"下次检定日期必须晚于最近检定日期");
        }
        var a=db.equipment(input.assetId(),true);
        require(input.kind().equals(a.subcategory()),"关联设备子类必须与水表/电表类型一致，请先在设备台账建档");
        require(db.jdbc.queryForObject("select count(*) from utility_meters where (code=? or asset_id=?) and id<>?",Integer.class,input.code().trim(),input.assetId(),id==null?"":id)==0,"表号或关联设备已登记，不可重复建表");
        if(id==null) {
            pastDate(input.readDate()); require(input.nextReadDueDate().isAfter(input.readDate()),"下次抄表日期必须晚于底数日期");
            id=id();
            db.jdbc.update("""
                    insert into utility_meters(id,code,asset_id,name,kind,location,current_reading,previous_reading,unit,read_date,next_read_due_date,status,multiplier,owner,lifecycle_status,cycle_days,initial_reading,area,model,install_date,last_inspection_date,next_inspection_date)
                    values(?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,'ACTIVE',?,?,?,?,?,?,?)
                    """,id,input.code().trim(),a.id(),input.name().trim(),input.kind(),input.location().trim(),input.initialReading(),input.initialReading(),"电表".equals(input.kind())?"kWh":"m³",input.readDate(),input.nextReadDueDate(),input.multiplier(),input.owner().trim(),input.cycleDays(),input.initialReading(),text(input.area()),text(input.model()),input.installDate(),input.lastInspectionDate(),input.nextInspectionDate());
            db.audit("METER",id,"CREATE","新建表计："+input.code()+"；初始底数 "+input.initialReading(),operator);
        } else {
            var old=lock(id); version(input.version(),old.version());
            require(old.assetId().equals(input.assetId()) && old.kind().equals(input.kind()) && old.code().equals(input.code().trim()),"表号、关联设备和类型不可更换；换表请停用旧表并新建");
            require(old.initialReading().compareTo(input.initialReading())==0 && old.readDate().equals(input.readDate()) && old.nextReadDueDate().equals(input.nextReadDueDate()),"读数和抄表日期请通过录入抄表或更正修改");
            db.jdbc.update("update utility_meters set name=?,location=?,owner=?,multiplier=?,cycle_days=?,area=?,model=?,install_date=?,last_inspection_date=?,next_inspection_date=?,version=version+1 where id=?",input.name().trim(),input.location().trim(),input.owner().trim(),input.multiplier(),input.cycleDays(),text(input.area()),text(input.model()),input.installDate(),input.lastInspectionDate(),input.nextInspectionDate(),id);
            db.audit("METER",id,"UPDATE","更新表计资料；倍率 "+old.multiplier()+" → "+input.multiplier()+"（仅影响后续抄表）；负责人 "+old.owner()+" → "+input.owner(),operator);
        }
        return get(id);
    }
    @Transactional
    public UtilityMeter state(String id,StateInput input,Long operator) {
        var m=lock(id); version(input.version(),m.version()); require(Set.of("ACTIVE","INACTIVE").contains(input.status()) && !m.lifecycleStatus().equals(input.status()),"表计状态变更不合法");
        if("ACTIVE".equals(input.status())) db.equipment(m.assetId(),true);
        db.jdbc.update("update utility_meters set lifecycle_status=?,version=version+1 where id=?",input.status(),id);
        db.audit("METER",id,input.status(),input.reason(),operator); return get(id);
    }
    @Transactional
    public Reading record(String id,String correctionId,ReadingInput input,Long operator) {
        var m=lock(id); version(input.version(),m.version()); db.equipment(m.assetId(),true);
        require("ACTIVE".equals(m.lifecycleStatus()),"停用表计不能录入或更正读数");
        pastDate(input.readDate()); require(input.nextReadDueDate().isAfter(input.readDate()),"下次抄表日期必须晚于本次抄表日期");
        require(Set.of("NORMAL","ALERT").contains(input.status()),"读数状态不合法");
        var previous=m.currentReading(); BigDecimal multiplier=m.multiplier();
        if(correctionId==null) require(input.readDate().isAfter(m.readDate()),"抄表日期须晚于上次日期，同日误录请更正最近一笔");
        else {
            var latest=readings(id).stream().filter(r -> !"VOID".equals(r.status())).findFirst().orElseThrow(() -> new BusinessException(400,"没有可更正的抄表记录"));
            require(latest.id().equals(correctionId),"只能更正最近一笔有效记录，历史期间不能直接改写");
            require(input.readDate().equals(latest.readDate()),"更正须保留原抄表日期");
            require(!text(input.note()).isEmpty(),"更正读数必须填写原因");
            previous=latest.previousReading(); multiplier=latest.multiplier();
        }
        require(input.currentReading().compareTo(previous)>=0,"累计读数不能小于上期读数，换表请停用旧表并登记新表");
        var consumption=input.currentReading().subtract(previous).multiply(multiplier);
        boolean spike=correctionId==null && m.consumption().signum()>0 && consumption.compareTo(m.consumption().multiply(new BigDecimal("3")))>0;
        var status=spike?"ALERT":input.status();
        require(!"ALERT".equals(status) || !text(input.note()).isEmpty(),"异常用量（超过上期三倍）须填写核查说明");
        var readingId=id();
        if(correctionId!=null) db.jdbc.update("update utility_meter_readings set status='VOID' where id=?",correctionId);
        db.jdbc.update("insert into utility_meter_readings(id,meter_id,previous_reading,current_reading,multiplier,consumption,read_date,next_read_due_date,status,note,correction_of,photo_name,photo_mime,photo_base64) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",readingId,id,previous,input.currentReading(),multiplier,consumption,input.readDate(),input.nextReadDueDate(),status,text(input.note()),correctionId,text(input.photoName()),text(input.photoMime()),input.photoBase64());
        db.jdbc.update("update utility_meters set previous_reading=?,current_reading=?,read_date=?,next_read_due_date=?,status=?,version=version+1 where id=?",previous,input.currentReading(),input.readDate(),input.nextReadDueDate(),status,id);
        db.audit("METER",id,correctionId==null?"READ":"CORRECT","抄表 "+input.readDate()+"："+input.currentReading()+"；用量 "+consumption+"；"+text(input.note()),operator);
        return readings(id).stream().filter(r -> readingId.equals(r.id())).findFirst().orElseThrow();
    }
    @Transactional
    public void delete(String id,int suppliedVersion,Long operator) {
        var m=lock(id); version(suppliedVersion,m.version()); require(readings(id).isEmpty(),"表计已有抄表记录，请停用保留历史");
        db.audit("METER",id,"DELETE","删除误建表计："+m.code(),operator); db.jdbc.update("delete from utility_meters where id=?",id);
    }
}
