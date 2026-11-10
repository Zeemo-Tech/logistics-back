package org.example.logisticsback.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.example.logisticsback.common.BusinessException;

/** Customer-declared plans; never an inventory or valuation record. */
public record PledgeDemand(String ownerCustomerId, String bankName, String storageSituation,
                           String goodsSource, String expectedStartDate, String storageRequirements,
                           List<Goods> goods) {
    public record Goods(String name, String category, String specification, BigDecimal quantity,
                        String unit, BigDecimal estimatedValue) {}

    public static void validate(PledgeDemand demand, boolean complete) {
        if (demand == null) throw new BusinessException(400, "请填写拟质押货物需求");
        length(demand.ownerCustomerId(),48,"货主");length(demand.bankName(),160,"意向银行");
        length(demand.storageRequirements(),1000,"保管要求");
        if (demand.storageSituation() != null && !demand.storageSituation().isBlank()
            && !Set.of("PARK_STOCK","NEW_INBOUND","OTHER_WAREHOUSE","UNKNOWN").contains(demand.storageSituation()))
            throw new BusinessException(400,"货物存放情况不正确");
        if (demand.goodsSource() != null && !demand.goodsSource().isBlank()
            && !Set.of("PURCHASED","SELF_PRODUCED","OTHER","UNKNOWN").contains(demand.goodsSource()))
            throw new BusinessException(400,"货物取得来源不正确");
        if (demand.expectedStartDate()!=null && !demand.expectedStartDate().isBlank()) {
            try {LocalDate.parse(demand.expectedStartDate());}
            catch (Exception e) {throw new BusinessException(400,"预计启动日期不正确");}
        }
        if (demand.goods()==null || demand.goods().isEmpty() || demand.goods().size()>30)
            throw new BusinessException(400,"请填写 1 至 30 条拟质押货物");
        for (var goods:demand.goods()) {
            if(goods==null || goods.name()==null || goods.name().isBlank()
                || goods.category()==null || goods.category().isBlank())
                throw new BusinessException(400,"请填写货物名称和类别");
            length(goods.name(),160,"货物名称");length(goods.category(),80,"货物类别");
            length(goods.specification(),160,"规格");length(goods.unit(),20,"计量单位");
            if (goods.quantity()!=null && (goods.quantity().signum()<=0
                || goods.quantity().stripTrailingZeros().scale()>3
                || goods.quantity().compareTo(new BigDecimal("999999999999.999"))>0))
                throw new BusinessException(400,"预计数量须大于零，最多三位小数");
            if ((complete || goods.quantity()!=null) && (goods.unit()==null || goods.unit().isBlank()))
                throw new BusinessException(400,"请选择货物计量单位");
            if (complete && goods.quantity()==null)
                throw new BusinessException(400,"转为监管业务前请补全预计数量和单位");
            if (goods.estimatedValue()!=null && (goods.estimatedValue().signum()<0
                || goods.estimatedValue().stripTrailingZeros().scale()>2
                || goods.estimatedValue().compareTo(new BigDecimal("9999999999999999.99"))>0))
                throw new BusinessException(400,"预计货值不能为负数，最多两位小数");
        }
    }
    private static void length(String value,int max,String label) {
        if(value!=null && value.length()>max) throw new BusinessException(400,label+"过长");
    }
}
