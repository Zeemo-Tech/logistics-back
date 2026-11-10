package org.example.logisticsback.pledge;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.customer.*;
import org.example.logisticsback.contract.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class PledgeIntentionIntegrationTests {
    @Autowired CustomerService customers;
    @Autowired CustomerActivityService activities;
    @Autowired ContractService contracts;
    @Autowired PledgeService pledges;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    CustomerRecord customer() {
        return customers.create(new CustomerRequest(null,"意向联动测试"+UUID.randomUUID(),"企业","","","","","","","","","","","经办","新来访","","","",""));
    }
    PledgeDemand demand(String owner,String quantity) {
        return new PledgeDemand(owner,"意向银行","NEW_INBOUND","PURCHASED","","-18℃以下",
            List.of(new PledgeDemand.Goods("冷冻牛肉","冻品","A级",quantity==null?null:new BigDecimal(quantity),quantity==null?"":"吨",new BigDecimal("3000000"))));
    }
    CustomerActivityModels.IntentionRequest request(String customer,PledgeDemand demand) {
        return new CustomerActivityModels.IntentionRequest(null,customer,"仓单质押",new BigDecimal("999"),"意向跟进","园区经办","客户来访","","客户申报",demand);
    }
    ContractRequest agreementRequest(String customer,String intention,String status) {
        return new ContractRequest("三方监管协议","监管协议",customer,null,null,null,
            LocalDate.now().minusDays(1).toString(),LocalDate.now().plusYears(1).toString(),"",null,null,null,null,
            "园区经办","凭银行指令办理",status,List.of(intention));
    }
    PledgeRequests.Business business(String borrower,String owner,String agreement,String intention) {
        return new PledgeRequests.Business("来源意向监管业务","融资人","货主","合作银行","园区","园区","经办","XY-TEST",
            LocalDate.now().minusDays(1),LocalDate.now().plusYears(1),"凭银行指令监管",borrower,owner,null,agreement,intention);
    }
    String doc(String business,String category) {
        return pledges.upload(business,category,new MockMultipartFile("file","test.pdf","application/pdf","%PDF-test".getBytes()),1L).get("id").toString();
    }
    Map<String,Object> command(String id,String action,Object...fields) {
        var detail=pledges.detail(id,1L);
        var data=PledgeService.row("action",action,"version",((Map<?,?>)detail.get("business")).get("version"));
        data.putAll(PledgeService.row(fields));
        return pledges.command(id,mapper.convertValue(data,PledgeRequests.Command.class),1L);
    }
    String id(Map<String,Object> detail) {return ((Map<?,?>)detail.get("business")).get("id").toString();}
    @Test void demandIsValidatedAndStoredSeparatelyFromServiceBudget() {
        var c=customer();var first=activities.createIntention(request(c.id(),demand("",null)));
        assertNull(first.amount());assertNull(first.pledgeDemand().goods().get(0).quantity());
        assertEquals("冷冻牛肉",activities.getIntention(first.id()).pledgeDemand().goods().get(0).name());
        assertThrows(BusinessException.class,()->activities.createIntention(request(c.id(),null)));
        assertThrows(BusinessException.class,()->activities.createIntention(request(c.id(),demand("","-1"))));
        assertThrows(BusinessException.class,()->activities.createIntention(request(c.id(),demand("","1.0001"))));
        assertThrows(BusinessException.class,()->activities.createIntention(request(c.id(),demand("missing","1"))));
        var a=contracts.create(agreementRequest(c.id(),first.id(),"草稿"));
        assertThrows(BusinessException.class,()->pledges.create(business(c.id(),c.id(),a.id(),first.id()),1L));
        assertEquals(0,jdbc.queryForObject("select count(*) from pledge_businesses where source_intention_id=?",Integer.class,first.id()));
    }
    @Test void contractEffectivenessSnapshotAndActualStockFormOneTraceableWorkflow() {
        var c=customer();var intention=activities.createIntention(request(c.id(),demand("","100")));
        var a=contracts.create(agreementRequest(c.id(),intention.id(),"草稿"));
        assertEquals("待签约",activities.getIntention(intention.id()).stage());
        assertEquals(a.id(),activities.getIntention(intention.id()).linkedContracts().get(0).id());
        String business=id(pledges.create(business(c.id(),c.id(),a.id(),intention.id()),1L));
        assertEquals(0,jdbc.queryForObject("select count(*) from pledge_lots where business_id=?",Integer.class,business));
        assertThrows(BusinessException.class,()->command(business,"ACTIVATE","documentId",doc(business,"AGREEMENT")));
        contracts.update(a.id(),agreementRequest(c.id(),intention.id(),"履约中"));
        assertEquals("合作中",activities.getIntention(intention.id()).stage());
        command(business,"ACTIVATE","documentId",doc(business,"AGREEMENT"));
        var warehouse=jdbc.queryForObject("select id from assets where kind='WAREHOUSE' and lifecycle_status='ACTIVE' fetch first 1 row only",String.class);
        command(business,"INSTRUCTION","type","INBOUND","number","IN-"+UUID.randomUUID(),"goodsName","冷冻牛肉","specification","A级","batchNumber","B01","warehouseId",warehouse,"unit","吨","quantity","100","validUntil",LocalDate.now().plusDays(30),"documentId",doc(business,"BANK_INSTRUCTION"),"note","原件核对");
        var instruction=((List<Map<String,Object>>)pledges.detail(business,1L).get("instructions")).get(0).get("id").toString();
        command(business,"VERIFY_INSTRUCTION","targetId",instruction,"note","核对银行来源及签章");
        command(business,"INBOUND","instructionId",instruction,"number","RK-"+UUID.randomUUID(),"goodsName","冷冻牛肉","specification","A级","batchNumber","B01","warehouseId",warehouse,"location","A01","unit","吨","quantity","98","documentId",doc(business,"INBOUND_PROOF"),"note","分次实收98吨");
        assertEquals(0,new BigDecimal("98").compareTo(jdbc.queryForObject("select quantity from pledge_lots where business_id=?",BigDecimal.class,business)));
        assertEquals(0,new BigDecimal("98").compareTo(jdbc.queryForObject("select executed_quantity from pledge_instructions where id=?",BigDecimal.class,instruction)));
        activities.updateIntention(intention.id(),new CustomerActivityModels.IntentionRequest(null,c.id(),"仓单质押",null,"合作中","经办","","","更新需求",demand("","120")));
        var snapshot=(CustomerActivityModels.Intention)pledges.detail(business,1L).get("sourceIntention");
        assertEquals(0,new BigDecimal("100").compareTo(snapshot.pledgeDemand().goods().get(0).quantity()));
        assertEquals(business,activities.getIntention(intention.id()).linkedBusinesses().get(0).id());
        assertThrows(BusinessException.class,()->pledges.create(business(c.id(),c.id(),a.id(),intention.id()),1L));
        assertThrows(BusinessException.class,()->activities.deleteIntention(intention.id()));
        assertThrows(BusinessException.class,()->contracts.update(a.id(),agreementRequest(c.id(),intention.id(),"已归档")));
    }
    @Test void borrowerOwnerAndAgreementCannotBeMixedAcrossIntentions() {
        var borrower=customer();var owner=customer();var other=customer();
        var intention=activities.createIntention(request(borrower.id(),demand(owner.id(),"10")));
        var a=contracts.create(agreementRequest(borrower.id(),intention.id(),"履约中"));
        assertThrows(BusinessException.class,()->pledges.create(business(other.id(),owner.id(),a.id(),intention.id()),1L));
        assertThrows(BusinessException.class,()->pledges.create(business(borrower.id(),borrower.id(),a.id(),intention.id()),1L));
        assertThrows(BusinessException.class,()->customers.delete(owner.id()));
        String b=id(pledges.create(business(borrower.id(),owner.id(),a.id(),intention.id()),1L));
        assertNotNull(b);
        var nonPledge=activities.createIntention(new CustomerActivityModels.IntentionRequest(null,borrower.id(),"仓储服务",new BigDecimal("100"),"意向跟进","","","",""));
        assertThrows(BusinessException.class,()->pledges.create(business(borrower.id(),owner.id(),a.id(),nonPledge.id()),1L));
    }
    @Test void directBusinessNeedsOnlySupervisionAgreementAndNoIntentionOrServiceContract() {
        var c=customer();var a=contracts.create(new ContractRequest("直接签约","监管协议",c.id(),null,null,null,
            LocalDate.now().minusDays(1).toString(),LocalDate.now().plusYears(1).toString(),"",null,null,null,null,"经办","条款","履约中",List.of()));
        var result=pledges.create(business(c.id(),c.id(),a.id(),null),1L);
        assertNull(result.get("sourceIntention"));assertNull(((Map<?,?>)result.get("business")).get("contractId"));
        command(id(result),"ACTIVATE","documentId",doc(id(result),"AGREEMENT"));
    }
    @Test void futureAndPendingContractsDoNotMarkIntentionAsCooperating() {
        var c=customer();var i=activities.createIntention(request(c.id(),demand("","10")));
        var a=contracts.create(agreementRequest(c.id(),i.id(),"待生效"));
        assertEquals("待签约",activities.getIntention(i.id()).stage());
        var future=new ContractRequest(a.title(),a.category(),c.id(),null,null,null,LocalDate.now().plusDays(2).toString(),LocalDate.now().plusYears(1).toString(),"",null,null,null,null,"经办","条款","履约中",List.of(i.id()));
        contracts.update(a.id(),future);
        assertEquals("待签约",activities.getIntention(i.id()).stage());
        assertNotEquals("已签约",customers.get(c.id()).status());
    }
}
