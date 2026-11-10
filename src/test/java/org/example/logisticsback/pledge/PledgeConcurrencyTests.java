package org.example.logisticsback.pledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.example.logisticsback.pledge.PledgeService.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:pledge-concurrency-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class PledgeConcurrencyTests {
    @Autowired PledgeService service; @Autowired PledgeRepository db; @Autowired ObjectMapper mapper;
    Map<String,Object> command(String id,String action,Object...fields) {
        var data=row("action",action,"version",((Map<?,?>)service.detail(id,1L).get("business")).get("version"));data.putAll(row(fields));
        return service.command(id,mapper.convertValue(data,PledgeRequests.Command.class),1L);
    }
    String document(String id,String category) {return str(service.upload(id,category,new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-test".getBytes()),1L),"id");}
    String customer(String id,String name) {
        if(db.query("select id from customers where id=?",id).isEmpty())
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
    String business() {
        var borrower=customer("KH-TEST-BORROWER","企业");
        var owner=customer("KH-TEST-OWNER","同一货主");
        var d=service.create(new PledgeRequests.Business("并发测试","企业","同一货主","银行","仓储","监管","经办","XY",LocalDate.now().minusDays(1),LocalDate.now().plusYears(1),"监管规则",borrower,owner,contract(borrower,"仓储服务"),contract(owner,"监管协议")),1L);
        String id=str((Map<String,Object>)d.get("business"),"id");command(id,"ACTIVATE","documentId",document(id,"AGREEMENT"));return id;
    }
    @Test void competingBusinessesCannotOverReserveTheSamePhysicalStock() throws Exception {
        String first=business(),second=business();String warehouse=str(db.query("select id from assets where kind='WAREHOUSE'").get(0),"id");
        command(first,"REGISTER_STOCK","quantity","100","goodsName","冻品","specification","A级","unit","吨","batchNumber","B1","warehouseId",warehouse,"location","A1","number","SOURCE","documentId",document(first,"INBOUND_PROOF"),"note","在库核验");
        String lot=str(((List<Map<String,Object>>)service.detail(first,1L).get("lots")).get(0),"id");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> a=()->{start.await();try {command(first,"DRAFT_RECEIPT","lines",List.of(row("lotId",lot,"quantity",80)));return true;}catch(BusinessException e){return false;}};
        Callable<Boolean> b=()->{start.await();try {command(second,"DRAFT_RECEIPT","lines",List.of(row("lotId",lot,"quantity",80)));return true;}catch(BusinessException e){return false;}};
        try {var fa=pool.submit(a);var fb=pool.submit(b);start.countDown();assertNotEquals(fa.get(10,TimeUnit.SECONDS),fb.get(10,TimeUnit.SECONDS));assertEquals(0,new BigDecimal("80").compareTo(db.reserved(lot)));}
        finally {pool.shutdownNow();}
    }
}
