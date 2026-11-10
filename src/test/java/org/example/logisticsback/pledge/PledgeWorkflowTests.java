package org.example.logisticsback.pledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.example.logisticsback.pledge.PledgeService.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class PledgeWorkflowTests {
    @Autowired PledgeService service;
    @Autowired PledgeRepository db;
    @Autowired ObjectMapper mapper;
    String businessId,warehouseId;
    final Long maker=203L,checker=202L;
    final String borrowerId="KH-TEST-BORROWER";
    @BeforeEach void setup() {
        db.insert("users",row("id",checker,"username","pledge_test_checker","password_hash","test","nickname","复核员","real_name","复核员","phone","","roles","pledge_reviewer"));
        db.insert("users",row("id",203L,"username","pledge_test_operator","password_hash","test","nickname","经办","real_name","经办","phone","","roles","pledge_operator"));
        if(db.query("select id from customers where id=?",borrowerId).isEmpty())
            db.insert("customers",row("id",borrowerId,"code",borrowerId,"name","融资企业"));
        db.insert("users",row("id",204L,"username","pledge_test_reader","password_hash","test","nickname","只读","real_name","只读","phone","","roles","viewer"));
        warehouseId=str(db.query("select id from assets where kind='WAREHOUSE' and lifecycle_status='ACTIVE'").get(0),"id");
        businessId=createBusiness("测试货主");activate(businessId);
    }
    String customer(String name) {
        var rows=db.query("select id from customers where name=?",name);
        if(!rows.isEmpty()) return str(rows.get(0),"id");
        var id="KH-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        db.insert("customers",row("id",id,"code",id,"name",name));
        return id;
    }
    String contract(String customerId,String category) {
        var id="HT-TEST-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        db.insert("contracts",row("id",id,"title","测试合同","category",category,"counterparty","融资企业",
            "resource","","start_date",LocalDate.now().minusDays(10).toString(),"end_date",LocalDate.now().plusYears(2).toString(),
            "amount","","owner","经办","document_count",0,"status","履约中","customer_id",customerId));
        return id;
    }
    String createBusiness(String owner) {
        var ownerId=customer(owner);
        var contractId=contract(borrowerId,"仓储服务");
        var agreementId=contract(ownerId,"监管协议");
        var result=service.create(new PledgeRequests.Business("测试监管业务","融资企业",owner,"测试银行","签发企业","监管企业","经办人","XY-001",LocalDate.now().minusDays(1),LocalDate.now().plusYears(1),"双人复核，定期盘点，凭银行授权解押及出库",borrowerId,ownerId,contractId,agreementId),maker);
        return str((Map<String,Object>)result.get("business"),"id");
    }
    void activate(String id) {command(id,"ACTIVATE",maker,"documentId",doc(id,"AGREEMENT"));}
    String doc(String id,String category) {
        return str(service.upload(id,category,new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-test".getBytes()),maker),"id");
    }
    Map<String,Object> command(String id,String action,Long actor,Object...fields) {
        var data=row("action",action,"version",((Map<String,Object>)service.detail(id,actor).get("business")).get("version"));
        data.putAll(row(fields));return service.command(id,mapper.convertValue(data,PledgeRequests.Command.class),actor);
    }
    List<Map<String,Object>> list(String id,String key) {return (List<Map<String,Object>>)service.detail(id,maker).get(key);}
    String stock(String id,BigDecimal qty,String source) {
        command(id,"REGISTER_STOCK",maker,"quantity",qty,"goodsName","冻品","specification","A级","unit","吨","batchNumber","BATCH-01","warehouseId",warehouseId,"location","A-01","number",source,"documentId",doc(id,"INBOUND_PROOF"),"note","核对在库原始凭证");
        return str(list(id,"lots").stream().filter(l->source.equals(str(l,"sourceNumber"))).findFirst().orElseThrow(),"id");
    }
    String receipt(String id,String lotId,String qty) {
        command(id,"DRAFT_RECEIPT",maker,"lines",List.of(row("lotId",lotId,"quantity",qty)));
        return str(list(id,"receipts").get(0),"id");
    }
    void issueAndSubmit(String id,String receiptId) {
        command(id,"ISSUE_RECEIPT",maker,"targetId",receiptId,"documentId",doc(id,"RECEIPT"),"note","核验账实及签章仓单一致");
        command(id,"SUBMIT_RECEIPT",maker,"targetId",receiptId,"documentId",doc(id,"BANK_RECEIPT"));
    }
    String instruction(String id,String type,String receiptId,String lotId,String qty) {
        command(id,"INSTRUCTION",maker,"type",type,"receiptId",receiptId,"lotId",lotId,"quantity",qty,"unit","吨","number",UUID.randomUUID().toString(),"validUntil",LocalDate.now().plusDays(30).toString(),"documentId",doc(id,"BANK_INSTRUCTION"));
        String instr=str(list(id,"instructions").stream().filter(item->"PENDING".equals(str(item,"status"))).findFirst().orElseThrow(),"id");
        command(id,"VERIFY_INSTRUCTION",maker,"targetId",instr,"note","核验银行来源、签章、范围和有效期");return instr;
    }
    String pledged(String id,String lotId,String qty) {
        String receipt=receipt(id,lotId,qty);issueAndSubmit(id,receipt);
        String instruction=instruction(id,"PLEDGE",receipt,null,qty);
        command(id,"CONFIRM_PLEDGE",maker,"targetId",receipt,"instructionId",instruction);return receipt;
    }
    void release(String id,String receipt,String lot,String instruction,String qty) {
        command(id,"RELEASE",maker,"receiptId",receipt,"lotId",lot,"instructionId",instruction,"quantity",qty,"documentId",doc(id,"RELEASE_PROOF"),"note","依银行指令办理部分解押");
    }
    @Test void fullWorkflowSupportsPartialReleaseAndActualOutboundWithoutConflatingBalances() {
        String lot=stock(businessId,new BigDecimal("100"),"STOCK-01");
        String receipt=pledged(businessId,lot,"80");
        assertEquals(0,new BigDecimal("80").compareTo(db.reserved(lot)));
        String release=instruction(businessId,"RELEASE",receipt,lot,"20");release(businessId,receipt,lot,release,"20");
        assertEquals("PARTIAL_RELEASED",str(db.one("pledge_receipts",receipt),"status"));
        assertEquals(0,new BigDecimal("100").compareTo(num(db.one("pledge_lots",lot),"quantity")));
        assertEquals(0,new BigDecimal("60").compareTo(db.reserved(lot)));
        String outbound=instruction(businessId,"OUTBOUND",receipt,lot,"20");
        command(businessId,"OUTBOUND_REQUEST",maker,"receiptId",receipt,"lotId",lot,"instructionId",outbound,"quantity","15","documentId",doc(businessId,"OUTBOUND_PROOF"),"note","货主提货申请");
        var order=list(businessId,"outboundOrders").get(0);String orderId=str(order,"id");
        command(businessId,"OUTBOUND_APPROVE",maker,"targetId",orderId,"note","复核银行授权及提货材料");
        command(businessId,"OUTBOUND_COMPLETE",maker,"targetId",orderId,"documentId",doc(businessId,"OUTBOUND_PROOF"),"note","完成装车交接");
        assertEquals(0,new BigDecimal("85").compareTo(num(db.one("pledge_lots",lot),"quantity")));
        assertEquals(0,new BigDecimal("60").compareTo(db.reserved(lot)));
        assertEquals(0,new BigDecimal("15").compareTo(num(db.one("pledge_instructions",outbound),"executedQuantity")));
        assertThrows(BusinessException.class,()->command(businessId,"OUTBOUND_COMPLETE",maker,"targetId",orderId,"documentId",doc(businessId,"OUTBOUND_PROOF"),"note","重复放行"));
        assertEquals(0,new BigDecimal("85").compareTo(num(db.one("pledge_lots",lot),"quantity")));
        assertTrue(list(businessId,"events").size()>10);
    }
    @Test void pendingReservationsPreventDuplicatePledgeAcrossBusinessesAndSourceImports() {
        String lot=stock(businessId,new BigDecimal("100"),"STOCK-02");String receipt=receipt(businessId,lot,"80");
        String other=createBusiness("测试货主");activate(other);
        assertThrows(BusinessException.class,()->receipt(other,lot,"30"));
        assertThrows(BusinessException.class,()->stock(other,new BigDecimal("100"),"STOCK-02"));
        command(businessId,"REJECT_RECEIPT",checker,"targetId",receipt,"note","仓单信息不符，退回重制");
        assertEquals(0,db.reserved(lot).signum());
        receipt(other,lot,"100");assertEquals(0,new BigDecimal("100").compareTo(db.reserved(lot)));
    }
    @Test void bankLimitsExpiryAndPendingOutboundCannotBeBypassed() {
        String lot=stock(businessId,new BigDecimal("100"),"STOCK-03");String receipt=pledged(businessId,lot,"80");
        String release=instruction(businessId,"RELEASE",receipt,lot,"20");
        assertThrows(BusinessException.class,()->release(businessId,receipt,lot,release,"21"));
        release(businessId,receipt,lot,release,"20");
        assertThrows(BusinessException.class,()->release(businessId,receipt,lot,release,"1"));
        String out=instruction(businessId,"OUTBOUND",receipt,lot,"20");
        command(businessId,"OUTBOUND_REQUEST",maker,"receiptId",receipt,"lotId",lot,"instructionId",out,"quantity","15","documentId",doc(businessId,"OUTBOUND_PROOF"),"note","提货申请");
        assertThrows(BusinessException.class,()->command(businessId,"OUTBOUND_REQUEST",maker,"receiptId",receipt,"lotId",lot,"instructionId",out,"quantity","10","documentId",doc(businessId,"OUTBOUND_PROOF"),"note","超额预占"));
        String order=str(list(businessId,"outboundOrders").get(0),"id");
        command(businessId,"OUTBOUND_APPROVE",maker,"targetId",order,"note","复核通过");
        db.update("update pledge_instructions set valid_until=? where id=?",LocalDate.now().minusDays(1),out);
        assertThrows(BusinessException.class,()->command(businessId,"OUTBOUND_COMPLETE",maker,"targetId",order,"documentId",doc(businessId,"OUTBOUND_PROOF"),"note","过期指令不得放行"));
        assertEquals(0,new BigDecimal("100").compareTo(num(db.one("pledge_lots",lot),"quantity")));
    }
    @Test void singleOperatorPermissionsOwnershipAndOptimisticVersionAreEnforced() {
        var detail=service.detail(businessId,maker);int stale=num((Map<String,Object>)detail.get("business"),"version").intValue();
        String lot=stock(businessId,new BigDecimal("100"),"STOCK-04");String receipt=receipt(businessId,lot,"80");
        assertThrows(BusinessException.class,()->command(businessId,"ISSUE_RECEIPT",204L,"targetId",receipt,"documentId",doc(businessId,"RECEIPT"),"note","只读账号不能签发"));
        command(businessId,"ISSUE_RECEIPT",203L,"targetId",receipt,"documentId",doc(businessId,"RECEIPT"),"note","经办账号可完成核验签发");
        assertEquals("ISSUED",str(db.one("pledge_receipts",receipt),"status"));
        var command=mapper.convertValue(row("action","CLOSE","version",stale),PledgeRequests.Command.class);
        assertThrows(BusinessException.class,()->service.command(businessId,command,checker));
        String other=createBusiness("其他货主");activate(other);
        assertThrows(BusinessException.class,()->receipt(other,lot,"10"));
        assertThrows(BusinessException.class,()->command(businessId,"CLOSE",checker));
    }
    @Test void abnormalInspectionFreezesStockAndAuthorizedLossAdjustmentPreservesEncumbrance() {
        String lot=stock(businessId,new BigDecimal("100"),"STOCK-05");String receipt=pledged(businessId,lot,"100");
        command(businessId,"INSPECT",maker,"lotId",lot,"quantity","90","documentId",doc(businessId,"INSPECTION"),"note","盘点发现10吨货损");
        assertTrue(db.frozen(lot));
        String issue=str(list(businessId,"inspections").get(0),"id");
        String adjust=instruction(businessId,"ADJUST_OUT",receipt,lot,"10");
        assertThrows(BusinessException.class,()->command(businessId,"ADJUST_STOCK",checker,"receiptId",receipt,"lotId",lot,"instructionId",adjust,"quantity","10","documentId",doc(businessId,"ADJUST_PROOF"),"note","未解押不能减少受控实物"));
        String release=instruction(businessId,"RELEASE",receipt,lot,"10");release(businessId,receipt,lot,release,"10");
        command(businessId,"ADJUST_STOCK",checker,"receiptId",receipt,"lotId",lot,"instructionId",adjust,"quantity","10","documentId",doc(businessId,"ADJUST_PROOF"),"note","银行已确认货损及调整处理");
        assertEquals(0,new BigDecimal("90").compareTo(num(db.one("pledge_lots",lot),"quantity")));
        command(businessId,"RESOLVE_INSPECTION",checker,"targetId",issue,"quantity","90","documentId",doc(businessId,"RESOLUTION"),"note","复核货损处置和银行授权，账实一致");
        assertFalse(db.frozen(lot));
    }
    @Test void freshInboundMustMatchBankInstructionAndIsNotPledgedBeforeConfirmation() {
        command(businessId,"INSTRUCTION",maker,"type","INBOUND","quantity","30","unit","吨","goodsName","冻品","specification","A级","batchNumber","NEW-BATCH","warehouseId",warehouseId,"number","BANK-IN-1","validUntil",LocalDate.now().plusDays(1).toString(),"documentId",doc(businessId,"BANK_INSTRUCTION"));
        String in=str(list(businessId,"instructions").get(0),"id");
        command(businessId,"VERIFY_INSTRUCTION",maker,"targetId",in,"note","核验原件");
        assertThrows(BusinessException.class,()->command(businessId,"INBOUND",maker,"instructionId",in,"quantity","30","unit","吨","goodsName","其他货物","specification","A级","batchNumber","NEW-BATCH","warehouseId",warehouseId,"location","A-02","number","IN-1","documentId",doc(businessId,"INBOUND_PROOF"),"note","品名不匹配"));
        command(businessId,"INBOUND",maker,"instructionId",in,"quantity","30","unit","吨","goodsName","冻品","specification","A级","batchNumber","NEW-BATCH","warehouseId",warehouseId,"location","A-02","number","IN-1","documentId",doc(businessId,"INBOUND_PROOF"),"note","实收与计划一致");
        var lot=list(businessId,"lots").get(0);assertEquals(0,db.reserved(str(lot,"id")).signum());
        assertEquals("EXECUTED",str(db.one("pledge_instructions",in),"status"));
    }
    @Test void exceptionAfterSubmissionBlocksPledgeConfirmation() {
        String lot=stock(businessId,new BigDecimal("100"),"STOCK-FROZEN");
        String receipt=receipt(businessId,lot,"80");issueAndSubmit(businessId,receipt);
        String instruction=instruction(businessId,"PLEDGE",receipt,null,"80");
        command(businessId,"INSPECT",maker,"lotId",lot,"quantity","90","documentId",doc(businessId,"INSPECTION"),"note","提交后出现盘点差异");
        assertThrows(BusinessException.class,()->command(businessId,"CONFIRM_PLEDGE",maker,"targetId",receipt,"instructionId",instruction));
        assertEquals("SUBMITTED",str(db.one("pledge_receipts",receipt),"status"));
        assertEquals(0,num(db.one("pledge_instructions",instruction),"executedQuantity").signum());
    }
    @Test void expiredAgreementBlocksAdditionalStock() {
        db.update("update pledge_businesses set end_date=? where id=?",LocalDate.now().minusDays(1),businessId);
        assertThrows(BusinessException.class,()->stock(businessId,new BigDecimal("100"),"STOCK-EXPIRED"));
        assertTrue(list(businessId,"lots").isEmpty());
    }
    @Test void fullyReleasedGoodsCanRemainInWarehouseAfterBusinessClosure() {
        String lot=stock(businessId,new BigDecimal("100"),"STOCK-06");String receipt=pledged(businessId,lot,"100");
        String release=instruction(businessId,"RELEASE",receipt,lot,"100");release(businessId,receipt,lot,release,"100");
        command(businessId,"CLOSE",checker);
        assertEquals("CLOSED",str(db.one("pledge_businesses",businessId),"status"));
        assertEquals(0,new BigDecimal("100").compareTo(num(db.one("pledge_lots",lot),"quantity")));
        assertEquals(0,db.reserved(lot).signum());
        assertThrows(BusinessException.class,()->command(businessId,"INSPECT",maker,"lotId",lot,"quantity","100","documentId",doc(businessId,"INSPECTION"),"note","结案后不可变更"));
    }
    int version(String id) { return num(db.one("pledge_businesses",id),"version").intValue(); }
    @Test void deletionArchivesAllBusinessDataAndRejectsFurtherAccess() {
        String lot=stock(businessId,new BigDecimal("100"),"DELETE-STOCK");
        String receipt=receipt(businessId,lot,"80");
        int oldVersion=version(businessId);
        assertThrows(BusinessException.class,()->service.remove(businessId,oldVersion,204L));
        assertThrows(BusinessException.class,()->service.remove(businessId,oldVersion-1,maker));
        String document=doc(businessId,"OTHER");
        service.remove(businessId,version(businessId),maker);
        assertNotNull(db.one("pledge_businesses",businessId).get("deletedAt"));
        assertEquals(0,db.reserved(lot).signum());
        assertTrue(((List<Map<String,Object>>)service.workspace(maker).get("businesses")).isEmpty());
        assertTrue(((List<Map<String,Object>>)service.workspace(maker).get("lots")).isEmpty());
        assertTrue(((List<Map<String,Object>>)service.workspace(maker).get("receipts")).isEmpty());
        assertEquals("DRAFT",str(db.one("pledge_receipts",receipt),"status"));
        assertFalse(db.query("select id from pledge_events where business_id=? and action='DELETE'",businessId).isEmpty());
        assertThrows(BusinessException.class,()->service.detail(businessId,maker));
        assertThrows(BusinessException.class,()->service.file(businessId,document,maker));
        assertThrows(BusinessException.class,()->service.remove(businessId,version(businessId),maker));
        assertThrows(BusinessException.class,()->service.command(businessId,mapper.convertValue(row("action","CLOSE","version",version(businessId)),PledgeRequests.Command.class),maker));
    }
    @Test void deletionCannotBypassPledgeOrPendingOutbound() {
        String lot=stock(businessId,new BigDecimal("100"),"DELETE-PLEDGED");
        String receipt=pledged(businessId,lot,"80");
        assertThrows(BusinessException.class,()->service.remove(businessId,version(businessId),maker));
        String release=instruction(businessId,"RELEASE",receipt,lot,"80");release(businessId,receipt,lot,release,"80");
        String out=instruction(businessId,"OUTBOUND",receipt,lot,"20");
        command(businessId,"OUTBOUND_REQUEST",maker,"receiptId",receipt,"lotId",lot,"instructionId",out,"quantity","15","documentId",doc(businessId,"OUTBOUND_PROOF"),"note","提货申请");
        assertThrows(BusinessException.class,()->service.remove(businessId,version(businessId),maker));
        String order=str(list(businessId,"outboundOrders").get(0),"id");
        command(businessId,"OUTBOUND_CANCEL",maker,"targetId",order,"note","取消测试申请");
        service.remove(businessId,version(businessId),maker);
        assertEquals(0,new BigDecimal("100").compareTo(num(db.one("pledge_lots",lot),"quantity")));
    }
    @Test void deletingSourceBusinessCannotBreakAnotherBusinessAndRemovingBorrowerReleasesOnlyItsReservation() {
        String lot=stock(businessId,new BigDecimal("100"),"DELETE-SHARED");
        receipt(businessId,lot,"20");
        String other=createBusiness("测试货主");activate(other);receipt(other,lot,"50");
        assertThrows(BusinessException.class,()->service.remove(businessId,version(businessId),maker));
        assertEquals(0,new BigDecimal("70").compareTo(db.reserved(lot)));
        service.remove(other,version(other),maker);
        assertEquals(0,new BigDecimal("20").compareTo(db.reserved(lot)));
        assertEquals(0,new BigDecimal("100").compareTo(num(db.one("pledge_lots",lot),"quantity")));
        service.remove(businessId,version(businessId),maker);
    }

}
