package org.example.logisticsback.pledge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import static org.example.logisticsback.pledge.PledgeRequests.*;

@Service
@Transactional(readOnly=true)
public class PledgeService {
    private static final Set<String> WRITE_ROLES=Set.of("admin","leader","园区领导","pledge_operator","pledge_reviewer");
    private final PledgeRepository db;
    private final UserRepository users;
    private final org.example.logisticsback.customer.CustomerActivityService activities;
    private final tools.jackson.databind.ObjectMapper mapper;
    public PledgeService(PledgeRepository db,UserRepository users,
                         org.example.logisticsback.customer.CustomerActivityService activities,
                         tools.jackson.databind.ObjectMapper mapper) {
        this.db=db;this.users=users;this.activities=activities;this.mapper=mapper;
    }
    public Map<String,Object> workspace(Long actor) {
        user(actor);
        var result=new LinkedHashMap<String,Object>();
        result.put("businesses",db.query("select * from pledge_businesses where deleted_at is null order by updated_at desc,id"));
        result.put("lots",db.lots());
        result.put("receipts",db.query("select * from pledge_receipts where business_id in (select id from pledge_businesses where deleted_at is null) order by created_at desc,id"));
        result.put("instructions",db.query("select * from pledge_instructions where business_id in (select id from pledge_businesses where deleted_at is null) order by created_at desc,id"));
        result.put("outboundOrders",db.query("select * from pledge_outbound_orders where business_id in (select id from pledge_businesses where deleted_at is null) order by created_at desc,id"));
        result.put("inspections",db.query("select * from pledge_inspections where business_id in (select id from pledge_businesses where deleted_at is null) order by created_at desc,id"));
        result.put("canWrite",user(actor).getRoles().stream().anyMatch(WRITE_ROLES::contains));
        result.put("canReview",user(actor).getRoles().stream().anyMatch(WRITE_ROLES::contains));
        return result;
    }
    public Map<String,Object> detail(String id,Long actor) {
        user(actor);var result=new LinkedHashMap<String,Object>();
        var current=business(id);
        result.put("business",current);
        var snapshot=str(current,"intentionSnapshot");
        result.put("sourceIntention",snapshot.isBlank()?null:mapper.readValue(snapshot,org.example.logisticsback.customer.CustomerActivityModels.Intention.class));
        result.put("relatedContracts",db.query("select id,title,category,status,start_date,end_date from contracts where id=? or id=?",str(current,"agreementContractId"),str(current,"contractId")));
        result.put("lots",db.lots());
        result.put("receipts",db.query("select * from pledge_receipts where business_id=? order by created_at desc,id",id));
        result.put("lines",db.query("select l.* from pledge_receipt_lines l join pledge_receipts r on r.id=l.receipt_id where r.business_id=?",id));
        result.put("instructions",db.query("select * from pledge_instructions where business_id=? order by created_at desc,id",id));
        result.put("movements",db.query("select * from pledge_movements where business_id=? order by created_at desc,id",id));
        result.put("outboundOrders",db.query("select * from pledge_outbound_orders where business_id=? order by created_at desc,id",id));
        result.put("inspections",db.query("select * from pledge_inspections where business_id=? order by created_at desc,id",id));
        result.put("documents",db.query("select id,business_id,category,name,mime_type,file_size,uploaded_by,created_at from pledge_documents where business_id=? order by created_at desc,id",id));
        result.put("events",db.query("select * from pledge_events where business_id=? order by created_at desc,id",id));
        return result;
    }
    @Transactional public Map<String,Object> create(Business input,Long actor) {
        write(actor);db.lock();require(!input.endDate().isBefore(input.startDate()),"协议终止日期不能早于生效日期");
        var borrower=customer(input.borrowerCustomerId(),"融资人客户档案");
        var owner=customer(input.ownerCustomerId(),"货主客户档案");
        if(input.contractId()!=null&&!input.contractId().isBlank()) {
            var contract=linkedContract(input.contractId(),"服务合同");
            require(input.borrowerCustomerId().equals(str(contract,"customerId"))
                || input.ownerCustomerId().equals(str(contract,"customerId")),"合同相对方必须与融资人或货主一致");
            require(!"已归档".equals(str(contract,"status")),"服务合同已归档，请选择有效合同");
        }
        var agreement=linkedContract(input.agreementContractId(),"监管协议");
        require("监管协议".equals(str(agreement,"category")),"所选监管协议合同类型必须为监管协议");
        require(input.borrowerCustomerId().equals(str(agreement,"customerId"))
            || input.ownerCustomerId().equals(str(agreement,"customerId")),"监管协议相对方必须与融资人或货主一致");
        require(!"已归档".equals(str(agreement,"status")),"监管协议已归档");
        org.example.logisticsback.customer.CustomerActivityModels.Intention source=null;
        if(input.sourceIntentionId()!=null&&!input.sourceIntentionId().isBlank()) {
            source=activities.getIntention(input.sourceIntentionId());
            require("仓单质押".equals(source.business()),"来源意向必须为仓单质押业务");
            require(!Set.of("已流失","已终止").contains(source.stage()),"来源意向已结束，不能建立监管业务");
            require(input.borrowerCustomerId().equals(source.customerId()),"融资人必须与来源意向客户一致");
            require(db.query("select id from pledge_businesses where source_intention_id=? and deleted_at is null",source.id()).isEmpty(),"该意向已建立监管业务，请查看已有业务");
            org.example.logisticsback.customer.PledgeDemand.validate(source.pledgeDemand(),true);
            var expectedOwner=source.pledgeDemand().ownerCustomerId();
            if(expectedOwner==null||expectedOwner.isBlank()) expectedOwner=source.customerId();
            require(expectedOwner.equals(input.ownerCustomerId()),"货主与来源意向不一致，请先确认意向资料");
            var linked=Arrays.asList(str(agreement,"intentionIds").split(","));
            require(str(agreement,"intentionIds").isBlank()||linked.contains(source.id()),"监管协议关联的意向与本次来源意向不一致");
        }
        if(!str(agreement,"startDate").isBlank())
            require(!LocalDate.parse(str(agreement,"startDate")).isAfter(input.startDate()),"监管协议生效日期不能晚于业务生效日期");
        if(!str(agreement,"endDate").isBlank())
            require(!LocalDate.parse(str(agreement,"endDate")).isBefore(input.endDate()),"监管协议终止日期不能早于业务终止日期");
        String id=uuid();
        db.insert("pledge_businesses",row("id",id,"code",code("ZY"),"title",input.title().trim(),
            "borrower",str(borrower,"name"),"owner_name",str(owner,"name"),"bank_name",input.bankName().trim(),
            "issuer",input.issuer().trim(),"supervisor",input.supervisor().trim(),"manager",input.manager().trim(),
            "agreement_number",input.agreementNumber().trim(),"start_date",input.startDate(),"end_date",input.endDate(),
            "rules_text",input.rulesText().trim(),"status","DRAFT","created_by",actor,
            "borrower_customer_id",input.borrowerCustomerId().trim(),"owner_customer_id",input.ownerCustomerId().trim(),
            "contract_id",input.contractId()==null||input.contractId().isBlank()?null:input.contractId().trim(),
            "agreement_contract_id",input.agreementContractId().trim(),"source_intention_id",source==null?null:source.id(),
            "intention_snapshot",source==null?null:mapper.writeValueAsString(source)));
        event(id,"CREATE",id,"建立质押监管业务",actor);return detail(id,actor);
    }
    private Map<String,Object> business(String id) {
        var item=db.one("pledge_businesses",id);
        require(item.get("deletedAt")==null,404,"监管业务已删除");
        return item;
    }
    @Transactional public void remove(String id,int version,Long actor) {
        write(actor);db.lock();var item=business(id);
        require(num(item,"version").intValue()==version,"数据已更新，请刷新后重新删除");
        require(db.query("select id from pledge_receipts where business_id=? and status in ('PLEDGED','PARTIAL_RELEASED')",id).isEmpty(),"仍有未解押仓单，请先完成解押后删除");
        require(db.query("select id from pledge_outbound_orders where business_id=? and status in ('PENDING','APPROVED')",id).isEmpty(),"仍有待出库单，请先完成或撤销出库申请");
        require(db.query("select id from pledge_inspections where business_id=? and status='OPEN'",id).isEmpty(),"仍有监管异常，请先处理后删除");
        require(db.query("select rl.id from pledge_receipt_lines rl join pledge_receipts r on r.id=rl.receipt_id join pledge_lots l on l.id=rl.lot_id join pledge_businesses b on b.id=r.business_id where l.business_id=? and r.business_id<>? and b.deleted_at is null",id,id).isEmpty(),"库存已被其他业务的仓单引用，不能删除来源业务");
        require(db.query("select p.id from pledge_inspections p join pledge_lots l on l.id=p.lot_id join pledge_businesses b on b.id=p.business_id where l.business_id=? and p.business_id<>? and b.deleted_at is null",id,id).isEmpty(),"库存已被其他业务的盘点记录引用，不能删除来源业务");
        event(id,"DELETE",id,"删除监管业务；原始记录与库存流水保留归档",actor);
        db.update("update pledge_businesses set deleted_at=current_timestamp,deleted_by=?,version=version+1,updated_at=current_timestamp where id=?",actor,id);
    }
    private Map<String,Object> customer(String id,String label) {
        require(id!=null&&!id.isBlank(),"请选择"+label);
        var rows=db.query("select * from customers where id=?",id);
        require(!rows.isEmpty(),label+"不存在，请先在客户档案中建立");
        return rows.get(0);
    }
    private Map<String,Object> linkedContract(String id,String label) {
        require(id!=null&&!id.isBlank(),"请选择"+label);
        var rows=db.query("select * from contracts where id=?",id);
        require(!rows.isEmpty(),label+"不存在，请先在合同档案中建立");
        return rows.get(0);
    }
    @Transactional public Map<String,Object> command(String id,Command c,Long actor) {
        write(actor);db.lock();var business=business(id);
        require(num(business,"version").intValue()==c.version(),"数据已更新，请刷新后重新操作");
        require(!Set.of("CLOSED","CANCELLED").contains(str(business,"status")),"业务已结束，不允许继续操作");
        if (!Set.of("ACTIVATE","CANCEL","AMEND_AGREEMENT").contains(c.action()))
            require("ACTIVE".equals(str(business,"status")),"请先上传并启用监管协议");
        switch(c.action()) {
            case "ACTIVATE" -> {
                require("DRAFT".equals(str(business,"status")),"当前阶段不能重复启用");
                document(id,c.documentId(),"AGREEMENT");agreementValid(business);
                var agreement=linkedContract(str(business,"agreementContractId"),"监管协议");
                require(Set.of("履约中","即将到期").contains(str(agreement,"status")),"请先在合同档案确认监管协议已签署生效");
                db.update("update pledge_businesses set status='ACTIVE' where id=?",id);
            }
            case "AMEND_AGREEMENT" -> {
                review(actor);document(id,c.documentId(),"AGREEMENT");
                require(c.validUntil()!=null&&!c.validUntil().isBefore(LocalDate.now()),"请选择有效的协议终止日期");
                text(c.note(),"补充协议说明",1000);
                db.update("update pledge_businesses set end_date=? where id=?",c.validUntil(),id);
            }
            case "CANCEL" -> {
                require("DRAFT".equals(str(business,"status")),"仅草稿业务可撤销");
                db.update("update pledge_businesses set status='CANCELLED' where id=?",id);
            }
            case "INSTRUCTION" -> addInstruction(id,business,c,actor);
            case "VERIFY_INSTRUCTION","REJECT_INSTRUCTION","REVOKE_INSTRUCTION" -> instructionDecision(id,c,actor);
            case "INBOUND","REGISTER_STOCK" -> receive(id,business,c,actor);
            case "DRAFT_RECEIPT" -> draftReceipt(id,business,c,actor);
            case "ISSUE_RECEIPT","REJECT_RECEIPT","CANCEL_RECEIPT" -> receiptDecision(id,c,actor);
            case "SUBMIT_RECEIPT" -> {
                var receipt=owned("pledge_receipts",id,c.targetId());state(receipt,"ISSUED");
                document(id,c.documentId(),"BANK_RECEIPT");
                db.update("update pledge_receipts set status='SUBMITTED',submission_document_id=?,submitted_at=current_timestamp where id=?",c.documentId(),c.targetId());
            }
            case "CONFIRM_PLEDGE" -> {
                agreementValid(business);
                var receipt=owned("pledge_receipts",id,c.targetId());state(receipt,"SUBMITTED");
                var instruction=instruction(id,c.instructionId(),"PLEDGE",str(receipt,"id"),null);
                require(num(instruction,"quantity").compareTo(num(receipt,"quantity"))==0,"出质指令数量必须与仓单一致");
                review(actor);
                for(var line:lines(c.targetId())) {
                    require(!db.frozen(str(line,"lotId")),"货物存在监管异常，不能确认质押");
                    var lot=db.one("pledge_lots",str(line,"lotId"));
                    require(lot.get("expiryDate")==null||!LocalDate.parse(str(lot,"expiryDate")).isBefore(LocalDate.now()),"货物已过期，不能确认质押");
                }
                db.update("update pledge_receipts set status='PLEDGED',pledged_at=current_timestamp where id=?",c.targetId());
                consume(instruction,num(receipt,"quantity"));
            }
            case "RELEASE" -> release(id,c,actor);
            case "OUTBOUND_REQUEST" -> outboundRequest(id,c,actor);
            case "OUTBOUND_APPROVE","OUTBOUND_REJECT","OUTBOUND_CANCEL","OUTBOUND_COMPLETE" -> outboundDecision(id,c,actor);
            case "INSPECT" -> inspect(id,c,actor);
            case "RESOLVE_INSPECTION" -> resolve(id,c,actor);
            case "MOVE" -> move(id,c,actor);
            case "ADJUST_STOCK" -> adjust(id,c,actor);
            case "CLOSE" -> { review(actor);close(id); }
            default -> throw new BusinessException(400,"不支持的业务操作");
        }
        db.update("update pledge_businesses set version=version+1,updated_at=current_timestamp where id=?",id);
        event(id,c.action(),c.targetId(),description(c),actor);
        return detail(id,actor);
    }
    private void addInstruction(String id,Map<String,Object> b,Command c,Long actor) {
        require(Set.of("INBOUND","PLEDGE","RELEASE","OUTBOUND","MOVE","ADJUST_IN","ADJUST_OUT").contains(safe(c.type())),"请选择指令类型");
        text(c.number(),"银行指令编号",100);positive(c.quantity());text(c.unit(),"计量单位",20);
        require(c.validUntil()!=null&&!c.validUntil().isBefore(LocalDate.now()),"银行指令已过期或未设置有效期");
        document(id,c.documentId(),"BANK_INSTRUCTION");
        require(db.query("select id from pledge_instructions where business_id=? and number=?",id,c.number().trim()).isEmpty(),"银行指令编号已登记，请勿重复录入");
        String receiptId=null,lotId=null,warehouseId=null;
        if ("INBOUND".equals(c.type())) {
            text(c.goodsName(),"货物名称",160);text(c.specification(),"规格",160);text(c.batchNumber(),"批次",100);
            warehouseId=warehouse(c.warehouseId()).get("id").toString();
        } else {
            var receipt=owned("pledge_receipts",id,c.receiptId());receiptId=c.receiptId();
            require(c.unit().trim().equals(str(receipt,"unit")),"指令单位与仓单不一致");
            if ("PLEDGE".equals(c.type())) {
                state(receipt,"SUBMITTED");require(c.quantity().compareTo(num(receipt,"quantity"))==0,"出质数量必须等于仓单数量");
            } else {
                require(Set.of("PLEDGED","PARTIAL_RELEASED","RELEASED").contains(str(receipt,"status")),"仓单尚未确认质押");
                lotId=requiredLine(receiptId,c.lotId()).get("lotId").toString();
                var line=requiredLine(receiptId,lotId);
                var limit="RELEASE".equals(c.type())?num(line,"quantity").subtract(num(line,"released")):
                    "OUTBOUND".equals(c.type())?num(line,"released").subtract(num(line,"outbound")):num(line,"quantity").subtract(num(line,"outbound"));
                if (!Set.of("ADJUST_IN","ADJUST_OUT").contains(c.type())) require(c.quantity().compareTo(limit)<=0,"指令数量超出对应批次可操作数量");
                if ("MOVE".equals(c.type())) { text(c.location(),"目标库位",160); }
            }
        }
        db.insert("pledge_instructions",row("id",uuid(),"business_id",id,"number",c.number().trim(),"instruction_type",c.type(),
            "receipt_id",receiptId,"lot_id",lotId,"warehouse_id",warehouseId,"goods_name",safe(c.goodsName()),
            "specification","MOVE".equals(c.type())?c.location().trim():safe(c.specification()),"batch_number",safe(c.batchNumber()),
            "quantity",c.quantity(),"unit",c.unit().trim(),"valid_until",c.validUntil(),"document_id",c.documentId(),"status","PENDING","created_by",actor));
    }
    private void instructionDecision(String id,Command c,Long actor) {
        review(actor);var item=owned("pledge_instructions",id,c.targetId());
        if ("REVOKE_INSTRUCTION".equals(c.action())) {
            state(item,"VERIFIED");
            require(db.query("select id from pledge_outbound_orders where instruction_id=? and status in ('PENDING','APPROVED')",c.targetId()).isEmpty(),"请先撤销该指令下待执行的出库单");
            text(c.note(),"撤销原因",1000);
            db.update("update pledge_instructions set status='REVOKED',verify_note=? where id=?",c.note(),c.targetId());return;
        }
        state(item,"PENDING");text(c.note(),"核验说明",1000);
        if ("VERIFY_INSTRUCTION".equals(c.action())) require(!LocalDate.parse(str(item,"validUntil")).isBefore(LocalDate.now()),"指令已过期");
        db.update("update pledge_instructions set status=?,verified_by=?,verify_note=? where id=?",
            "VERIFY_INSTRUCTION".equals(c.action())?"VERIFIED":"REJECTED",actor,c.note().trim(),c.targetId());
    }
    private void receive(String id,Map<String,Object> b,Command c,Long actor) {
        agreementValid(b);
        positive(c.quantity());text(c.location(),"库位",160);text(c.batchNumber(),"货物批次",100);
        text(c.goodsName(),"货物名称",160);text(c.specification(),"规格",160);text(c.unit(),"单位",20);
        text(c.number(),"入库或原始库存单号",100);text(c.note(),"验收及差异说明",1000);document(id,c.documentId(),"INBOUND_PROOF");
        var warehouse=warehouse(c.warehouseId());
        if (c.productionDate()!=null) require(!c.productionDate().isAfter(LocalDate.now()),"生产日期不能晚于今天");
        if (c.expiryDate()!=null) {
            require(!c.expiryDate().isBefore(LocalDate.now()),"货物已过期，不能办理质押入库");
            require(c.productionDate()==null||!c.expiryDate().isBefore(c.productionDate()),"保质期日期不能早于生产日期");
        }
        Map<String,Object> instruction=null;
        if ("INBOUND".equals(c.action())) {
            instruction=instruction(id,c.instructionId(),"INBOUND",null,null);
            require(c.warehouseId().equals(str(instruction,"warehouseId"))&&c.goodsName().trim().equals(str(instruction,"goodsName"))
                &&c.specification().trim().equals(str(instruction,"specification"))&&c.batchNumber().trim().equals(str(instruction,"batchNumber"))
                &&c.unit().trim().equals(str(instruction,"unit")),"实际货物、批次、单位或仓库与银行指令不一致");
            consume(instruction,c.quantity());
        } else review(actor);
        require(db.query("select id from pledge_lots where warehouse_id=? and owner_name=? and source_number=? and batch_number=? and goods_name=? and specification=?",
            c.warehouseId(),str(b,"ownerName"),c.number().trim(),c.batchNumber().trim(),c.goodsName().trim(),c.specification().trim()).isEmpty(),"该库存来源已登记，不能重复导入");
        String lotId=uuid();
        db.insert("pledge_lots",row("id",lotId,"business_id",id,"owner_name",str(b,"ownerName"),"warehouse_id",c.warehouseId(),"warehouse_name",str(warehouse,"name"),
            "location",c.location().trim(),"goods_name",c.goodsName().trim(),"specification",c.specification().trim(),"batch_number",c.batchNumber().trim(),"unit",c.unit().trim(),
            "quantity",c.quantity(),"source_number",c.number().trim(),"production_date",c.productionDate(),"expiry_date",c.expiryDate(),"temperature",safe(c.temperature()),"created_by",actor));
        movement(id,null,lotId,instruction==null?null:str(instruction,"id"),c.action(),c.quantity(),c.documentId(),c.note(),actor);
    }
    private void draftReceipt(String id,Map<String,Object> b,Command c,Long actor) {
        agreementValid(b);require(c.lines()!=null&&!c.lines().isEmpty(),"请选择仓单关联批次");
        require(c.lines().size()<=50,"单张仓单最多关联 50 个批次");
        var seen=new HashSet<String>();String unit=null,warehouseId=null,goodsName=null,specification=null;BigDecimal total=BigDecimal.ZERO;
        for (var line:c.lines()) {
            require(seen.add(line.lotId()),"同一仓单不能重复选择相同批次");positive(line.quantity());
            var lot=db.one("pledge_lots",line.lotId());business(str(lot,"businessId"));
            require(str(lot,"ownerName").equals(str(b,"ownerName")),"只能选择同一货主的库存");
            require(!db.frozen(line.lotId()),"货物存在未处理监管异常，不能签发仓单");
            require(str(lot,"expiryDate").isEmpty()||!LocalDate.parse(str(lot,"expiryDate")).isBefore(LocalDate.now()),"货物已过期");
            require(line.quantity().compareTo(num(lot,"quantity").subtract(db.reserved(line.lotId())).subtract(pending("lot_id=?",line.lotId())))<=0,"库存已被其他仓单占用，可用数量不足");
            if(unit==null) { unit=str(lot,"unit");warehouseId=str(lot,"warehouseId");goodsName=str(lot,"goodsName");specification=str(lot,"specification"); }
            require(unit.equals(str(lot,"unit"))&&warehouseId.equals(str(lot,"warehouseId"))&&goodsName.equals(str(lot,"goodsName"))&&specification.equals(str(lot,"specification")),"一张仓单须使用同品名、同规格、同单位、同仓库的货物");
            total=total.add(line.quantity());
        }
        String receiptId=uuid();
        db.insert("pledge_receipts",row("id",receiptId,"business_id",id,"code",code("CD"),"unit",unit,"quantity",total,"status","DRAFT","created_by",actor));
        for(var line:c.lines()) db.insert("pledge_receipt_lines",row("id",uuid(),"receipt_id",receiptId,"lot_id",line.lotId(),"quantity",line.quantity()));
    }
    private void receiptDecision(String id,Command c,Long actor) {
        var item=owned("pledge_receipts",id,c.targetId());
        if ("CANCEL_RECEIPT".equals(c.action())) {
            require(Set.of("DRAFT","ISSUED","SUBMITTED").contains(str(item,"status")),"已质押仓单不能直接作废");
            if (!"DRAFT".equals(str(item,"status"))) review(actor);
            require(db.query("select id from pledge_instructions where receipt_id=? and status in ('PENDING','VERIFIED')",c.targetId()).isEmpty(),"请先处理关联银行指令");
            text(c.note(),"作废原因",1000);
            db.update("update pledge_receipts set status='CANCELLED',review_note=? where id=?",c.note(),c.targetId());return;
        }
        review(actor);state(item,"DRAFT");text(c.note(),"仓单复核说明",1000);
        if("ISSUE_RECEIPT".equals(c.action())) {
            document(id,c.documentId(),"RECEIPT");
            for(var line:lines(c.targetId())) require(!db.frozen(str(line,"lotId")),"货物存在监管异常，不能签发");
        }
        db.update("update pledge_receipts set status=?,reviewed_by=?,review_note=?,issued_at=current_timestamp where id=?",
            "ISSUE_RECEIPT".equals(c.action())?"ISSUED":"REJECTED",actor,c.note(),c.targetId());
    }
    private void release(String id,Command c,Long actor) {
        review(actor);positive(c.quantity());var receipt=owned("pledge_receipts",id,c.receiptId());
        require(Set.of("PLEDGED","PARTIAL_RELEASED").contains(str(receipt,"status")),"仓单当前不能解押");
        var line=requiredLine(c.receiptId(),c.lotId());
        var instruction=instruction(id,c.instructionId(),"RELEASE",c.receiptId(),c.lotId());
        require(c.quantity().compareTo(num(line,"quantity").subtract(num(line,"released")))<=0,"解押数量超出剩余受控数量");
        // Bank-authorized release may reduce encumbrance during incident resolution; outbound remains frozen.
        document(id,c.documentId(),"RELEASE_PROOF");text(c.note(),"解押核验说明",1000);consume(instruction,c.quantity());
        db.update("update pledge_receipt_lines set released=released+? where id=?",c.quantity(),str(line,"id"));
        var remaining=lines(c.receiptId()).stream().map(l->num(l,"quantity").subtract(num(l,"released"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        db.update("update pledge_receipts set status=? where id=?",remaining.signum()==0?"RELEASED":"PARTIAL_RELEASED",c.receiptId());
        movement(id,c.receiptId(),c.lotId(),c.instructionId(),"RELEASE",c.quantity(),c.documentId(),c.note(),actor);
    }
    private void outboundRequest(String id,Command c,Long actor) {
        positive(c.quantity());owned("pledge_receipts",id,c.receiptId());
        var line=requiredLine(c.receiptId(),c.lotId());
        var instruction=instruction(id,c.instructionId(),"OUTBOUND",c.receiptId(),c.lotId());
        document(id,c.documentId(),"OUTBOUND_PROOF");text(c.note(),"提货申请说明",1000);
        require(!db.frozen(c.lotId()),"该批次存在未处理监管异常，暂停出库");
        BigDecimal pendingLine=pending("receipt_id=? and lot_id=?",c.receiptId(),c.lotId());
        require(c.quantity().compareTo(num(line,"released").subtract(num(line,"outbound")).subtract(pendingLine))<=0,"解押数量不足或已有待出库预占");
        require(c.quantity().compareTo(num(instruction,"quantity").subtract(num(instruction,"executedQuantity")).subtract(pending("instruction_id=?",c.instructionId())))<=0,"银行放货指令额度不足或已被其他出库单预占");
        require(c.quantity().compareTo(num(db.one("pledge_lots",c.lotId()),"quantity").subtract(db.reserved(c.lotId())).subtract(pending("lot_id=?",c.lotId())))<=0,"可用实物库存不足或已有其他出库预占");
        db.insert("pledge_outbound_orders",row("id",uuid(),"business_id",id,"number",code("CK"),"receipt_id",c.receiptId(),"lot_id",c.lotId(),
            "instruction_id",c.instructionId(),"quantity",c.quantity(),"status","PENDING","document_id",c.documentId(),"created_by",actor,"review_note",c.note()));
    }
    private BigDecimal pending(String condition,Object...args) {
        return db.query("select coalesce(sum(quantity),0) as total from pledge_outbound_orders where business_id in (select id from pledge_businesses where deleted_at is null) and status in ('PENDING','APPROVED') and "+condition,args).stream().map(r->num(r,"total")).findFirst().orElse(BigDecimal.ZERO);
    }
    private void outboundDecision(String id,Command c,Long actor) {
        var order=owned("pledge_outbound_orders",id,c.targetId());
        if("OUTBOUND_CANCEL".equals(c.action())) {
            require(Set.of("PENDING","APPROVED").contains(str(order,"status")),"出库单不能重复撤销");
            text(c.note(),"撤销原因",1000);db.update("update pledge_outbound_orders set status='CANCELLED',review_note=? where id=?",c.note(),c.targetId());return;
        }
        if(!"OUTBOUND_COMPLETE".equals(c.action())) {
            review(actor);state(order,"PENDING");text(c.note(),"出库复核说明",1000);
            db.update("update pledge_outbound_orders set status=?,reviewed_by=?,review_note=? where id=?",
                "OUTBOUND_APPROVE".equals(c.action())?"APPROVED":"REJECTED",actor,c.note(),c.targetId());return;
        }
        state(order,"APPROVED");String lotId=str(order,"lotId"),receiptId=str(order,"receiptId");
        var line=requiredLine(receiptId,lotId);var lot=db.one("pledge_lots",lotId);BigDecimal quantity=num(order,"quantity");
        require(!db.frozen(lotId),"货物存在监管异常，停止放行");
        var instruction=instruction(id,str(order,"instructionId"),"OUTBOUND",receiptId,lotId);
        require(quantity.compareTo(num(line,"released").subtract(num(line,"outbound")))<=0,"可出库数量不足");
        require(quantity.compareTo(num(lot,"quantity").subtract(db.reserved(lotId)))<=0,"库存不足或货物已被其他业务占用");
        document(id,c.documentId(),"OUTBOUND_PROOF");text(c.note(),"出库交接说明",1000);consume(instruction,quantity);
        db.update("update pledge_lots set quantity=quantity-? where id=?",quantity,lotId);
        db.update("update pledge_receipt_lines set outbound=outbound+? where id=?",quantity,str(line,"id"));
        db.update("update pledge_outbound_orders set status='COMPLETED',completed_by=?,completed_at=current_timestamp where id=?",actor,c.targetId());
        movement(id,receiptId,lotId,str(order,"instructionId"),"OUTBOUND",quantity,c.documentId(),c.note(),actor);
    }
    private void inspect(String id,Command c,Long actor) {
        var lot=businessLot(id,c.lotId());
        require(c.quantity()!=null&&c.quantity().signum()>=0&&c.quantity().scale()<=3,"实盘数量须为非负数，最多三位小数");
        text(c.note(),"盘点与货物状态",1000);document(id,c.documentId(),"INSPECTION");
        boolean abnormal=Boolean.TRUE.equals(c.abnormal())||c.quantity().compareTo(num(lot,"quantity"))!=0;
        db.insert("pledge_inspections",row("id",uuid(),"business_id",id,"lot_id",c.lotId(),"actual_quantity",c.quantity(),"book_quantity",num(lot,"quantity"),
            "condition_text",c.note(),"abnormal",abnormal,"status",abnormal?"OPEN":"NORMAL","document_id",c.documentId(),"created_by",actor));
    }
    private void resolve(String id,Command c,Long actor) {
        review(actor);var inspection=owned("pledge_inspections",id,c.targetId());state(inspection,"OPEN");
        document(id,c.documentId(),"RESOLUTION");text(c.note(),"银行沟通及异常处理结果",1000);
        var lot=db.one("pledge_lots",str(inspection,"lotId"));
        require(c.quantity()!=null&&c.quantity().compareTo(num(lot,"quantity"))==0,"复核实盘须与账面一致；未恢复的货损须持续冻结并另行处置");
        db.update("update pledge_inspections set status='RESOLVED',resolved_by=?,resolution=? where id=?",actor,c.note(),c.targetId());
    }
    private void move(String id,Command c,Long actor) {
        review(actor);var receipt=owned("pledge_receipts",id,c.receiptId());var line=requiredLine(c.receiptId(),c.lotId());
        var lot=businessLot(id,c.lotId());text(c.location(),"目标库位",160);text(c.note(),"移库说明",1000);document(id,c.documentId(),"MOVE_PROOF");
        require(!db.frozen(c.lotId()),"该货物存在监管异常，不能移库");
        require(db.query("select l.id from pledge_receipt_lines l join pledge_receipts r on r.id=l.receipt_id where l.lot_id=? and r.id<>? and r.status not in ('CANCELLED','REJECTED') and l.quantity>l.released",c.lotId(),c.receiptId()).isEmpty(),"该批次涉及其他仓单，请分别协调授权后处理");
        require(num(line,"quantity").subtract(num(line,"outbound")).compareTo(num(lot,"quantity"))==0,"仅支持整批同仓移库，当前批次还有其他库存份额");
        var instruction=instruction(id,c.instructionId(),"MOVE",str(receipt,"id"),c.lotId());
        require(c.location().trim().equals(str(instruction,"specification")),"目标库位与银行授权不一致");
        consume(instruction,num(lot,"quantity"));db.update("update pledge_lots set location=? where id=?",c.location().trim(),c.lotId());
        movement(id,c.receiptId(),c.lotId(),c.instructionId(),"MOVE",num(lot,"quantity"),c.documentId(),c.note(),actor);
    }
    private void adjust(String id,Command c,Long actor) {
        review(actor);positive(c.quantity());owned("pledge_receipts",id,c.receiptId());
        var lot=businessLot(id,c.lotId());requiredLine(c.receiptId(),c.lotId());
        var input=owned("pledge_instructions",id,c.instructionId());
        require(Set.of("ADJUST_IN","ADJUST_OUT").contains(str(input,"instructionType")),"请选择库存调整授权");
        var instruction=instruction(id,c.instructionId(),str(input,"instructionType"),c.receiptId(),c.lotId());
        BigDecimal delta="ADJUST_OUT".equals(str(input,"instructionType"))?c.quantity().negate():c.quantity();
        BigDecimal next=num(lot,"quantity").add(delta);
        require(next.compareTo(db.reserved(c.lotId()).add(pending("lot_id=?",c.lotId())))>=0,"调整后库存不足以覆盖受控与出库预占数量，请先按银行指令解押或撤销出库申请");
        document(id,c.documentId(),"ADJUST_PROOF");text(c.note(),"库存调整依据及银行沟通说明",1000);consume(instruction,c.quantity());
        db.update("update pledge_lots set quantity=? where id=?",next,c.lotId());
        movement(id,c.receiptId(),c.lotId(),c.instructionId(),str(input,"instructionType"),delta,c.documentId(),c.note(),actor);
    }
    private void close(String id) {
        require(db.query("select id from pledge_receipts where business_id=? and status not in ('RELEASED','CANCELLED','REJECTED')",id).isEmpty(),"仍有未完成解押的仓单");
        require(db.query("select id from pledge_instructions where business_id=? and status in ('PENDING','VERIFIED')",id).isEmpty(),"仍有待核验或未撤销的银行指令");
        require(db.query("select id from pledge_outbound_orders where business_id=? and status in ('PENDING','APPROVED')",id).isEmpty(),"仍有待完成出库单");
        require(db.query("select id from pledge_inspections where business_id=? and status='OPEN'",id).isEmpty(),"仍有监管异常未解决");
        db.update("update pledge_businesses set status='CLOSED' where id=?",id);
    }
    public Map<String,Object> file(String id,String documentId,Long actor) { user(actor);business(id);return document(id,documentId,null); }
    @Transactional public Map<String,Object> upload(String id,String category,MultipartFile file,Long actor) {
        write(actor);db.lock();var b=business(id);
        require(!Set.of("CLOSED","CANCELLED").contains(str(b,"status")),"已结束业务不能追加附件");
        require(Set.of("AGREEMENT","BANK_INSTRUCTION","INBOUND_PROOF","RECEIPT","BANK_RECEIPT","RELEASE_PROOF","OUTBOUND_PROOF","INSPECTION","RESOLUTION","MOVE_PROOF","ADJUST_PROOF","OTHER").contains(category),"附件类型不正确");
        require(!file.isEmpty()&&file.getSize()<=20*1024*1024,"附件不能为空且不能超过 20 MB");
        String mime=safe(file.getContentType());
        require(Set.of("application/pdf","image/png","image/jpeg","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").contains(mime),"支持 PDF、PNG、JPG、DOCX、XLSX 格式");
        String name=safe(file.getOriginalFilename()).replaceAll("[\\\\/\\r\\n]","_");require(!name.isBlank()&&name.length()<=240,"文件名称不合法");
        String documentId=uuid();
        try { db.insert("pledge_documents",row("id",documentId,"business_id",id,"category",category,"name",name,"mime_type",mime,"file_size",file.getSize(),"file_base64",Base64.getEncoder().encodeToString(file.getBytes()),"uploaded_by",actor)); }
        catch(java.io.IOException e) { throw new BusinessException(400,"文件读取失败"); }
        event(id,"UPLOAD",documentId,"上传 "+category+"："+name,actor);
        var result=new LinkedHashMap<>(db.one("pledge_documents",documentId));result.remove("fileBase64");return result;
    }
    private Map<String,Object> instruction(String id,String instructionId,String type,String receiptId,String lotId) {
        var item=owned("pledge_instructions",id,instructionId);state(item,"VERIFIED");
        require(type.equals(str(item,"instructionType")),"银行指令类型不匹配");
        require(!LocalDate.parse(str(item,"validUntil")).isBefore(LocalDate.now()),"银行指令已过期，禁止执行");
        if(receiptId!=null) require(receiptId.equals(str(item,"receiptId")),"银行指令不属于当前仓单");
        if(lotId!=null) require(lotId.equals(str(item,"lotId")),"银行指令不属于当前批次");
        return item;
    }
    private void consume(Map<String,Object> item,BigDecimal quantity) {
        require(quantity.compareTo(num(item,"quantity").subtract(num(item,"executedQuantity")))<=0,"银行指令剩余授权数量不足");
        BigDecimal executed=num(item,"executedQuantity").add(quantity);
        db.update("update pledge_instructions set executed_quantity=?,status=? where id=?",executed,executed.compareTo(num(item,"quantity"))==0?"EXECUTED":"VERIFIED",str(item,"id"));
    }
    private Map<String,Object> document(String id,String documentId,String category) {
        require(documentId!=null&&!documentId.isBlank(),"请上传并选择对应凭证");
        var document=owned("pledge_documents",id,documentId);
        if(category!=null) require(category.equals(str(document,"category")),"所选附件类型与当前操作不匹配");return document;
    }
    private Map<String,Object> owned(String table,String businessId,String id) {
        require(id!=null&&!id.isBlank(),"请选择操作对象");var item=db.one(table,id);
        require(businessId.equals(str(item,"businessId")),"操作对象不属于当前业务");return item;
    }
    private Map<String,Object> businessLot(String businessId,String lotId) {
        var lot=db.one("pledge_lots",lotId);
        require(businessId.equals(str(lot,"businessId"))||!db.query("select l.id from pledge_receipt_lines l join pledge_receipts r on r.id=l.receipt_id where l.lot_id=? and r.business_id=?",lotId,businessId).isEmpty(),"该库存不属于当前监管业务");return lot;
    }
    private Map<String,Object> warehouse(String id) {
        require(id!=null&&!id.isBlank(),"请选择仓库");var asset=db.one("assets",id);
        require(Set.of("WAREHOUSE","COLD_STORAGE").contains(str(asset,"kind"))&&"ACTIVE".equals(str(asset,"lifecycleStatus")),"目标仓库不在册或类型不正确");return asset;
    }
    private List<Map<String,Object>> lines(String receiptId) { return db.query("select * from pledge_receipt_lines where receipt_id=?",receiptId); }
    private Map<String,Object> requiredLine(String receiptId,String lotId) {
        return lines(receiptId).stream().filter(l->str(l,"lotId").equals(lotId)).findFirst().orElseThrow(()->new BusinessException(400,"批次不属于当前仓单"));
    }
    private void agreementValid(Map<String,Object> b) {
        require(!LocalDate.parse(str(b,"startDate")).isAfter(LocalDate.now())&&!LocalDate.parse(str(b,"endDate")).isBefore(LocalDate.now()),"监管协议未生效或已到期，请先补充有效协议");
    }
    private org.example.logisticsback.user.UserAccount user(Long actor) {
        return users.findById(actor).orElseThrow(()->new BusinessException(401,"请先登录"));
    }
    private void write(Long actor) { require(user(actor).getRoles().stream().anyMatch(WRITE_ROLES::contains),403,"无质押业务经办权限"); }
    private void review(Long actor) { write(actor); }
    private void event(String id,String action,String target,String description,Long actor) {
        var user=user(actor);String name=user.getRealName().isBlank()?user.getUsername():user.getRealName();
        db.insert("pledge_events",row("id",uuid(),"business_id",id,"action",action,"target_id",target,"description",description,"operator_id",actor,"operator_name",name));
    }
    private void movement(String id,String receiptId,String lotId,String instructionId,String type,BigDecimal quantity,String doc,String note,Long actor) {
        var user=user(actor);
        db.insert("pledge_movements",row("id",uuid(),"business_id",id,"receipt_id",receiptId,"lot_id",lotId,"instruction_id",instructionId,"movement_type",type,"quantity",quantity,
            "document_id",doc,"note",note,"operator_id",actor,"operator_name",user.getRealName().isBlank()?user.getUsername():user.getRealName()));
    }
    private String description(Command c) {
        return c.action()+"；对象："+safe(c.targetId())+"；银行指令："+safe(c.instructionId())+"；数量："+(c.quantity()==null?"":c.quantity().toPlainString())+"；凭证："+safe(c.documentId())+"；"+safe(c.note());
    }
    private static void state(Map<String,Object> item,String expected) { require(expected.equals(str(item,"status")),"记录状态已变化，请刷新后操作"); }
    private static void positive(BigDecimal q) { require(q!=null&&q.signum()>0&&q.scale()<=3&&q.precision()<=18,"数量须大于 0，最多三位小数及十五位整数"); }
    private static void text(String value,String label,int max) { require(value!=null&&!value.isBlank()&&value.trim().length()<=max,"请填写"+label+"（不超过 "+max+" 字）"); }
    private static void require(boolean ok,String message) { require(ok,400,message); }
    private static void require(boolean ok,int code,String message) { if(!ok) throw new BusinessException(code,message); }
    static String str(Map<String,Object> row,String key) { return row.get(key)==null?"":row.get(key).toString(); }
    static BigDecimal num(Map<String,Object> row,String key) { return new BigDecimal(str(row,key).isEmpty()?"0":str(row,key)); }
    static String safe(String value) { return value==null?"":value.trim(); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String code(String prefix) { return prefix+"-"+LocalDate.now().toString().replace("-","")+"-"+uuid().substring(0,8).toUpperCase(); }
    static Map<String,Object> row(Object...values) { var row=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)row.put(values[i].toString(),values[i+1]);return row; }
}
