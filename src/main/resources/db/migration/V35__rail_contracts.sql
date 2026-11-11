-- Rail transport contract support: structured railway terms on contract archives.

alter table contracts add column rail_terms varchar(2000) not null default '';

insert into contract_categories (id, name, status, sort_order)
select 'CC-014', '铁路运价合同', 'ACTIVE', 83
where not exists (select 1 from contract_categories where name = '铁路运价合同');

insert into contract_templates (id, title, resource_type, intention_business, business_detail, category, subject, purpose, content, status, sort_order)
select 'CT-016', '铁路运价合同模板', '', '运输服务', '火车运输', '铁路运价合同', '铁路货物运输价格', '约定项目号、价差系数、车皮价、税点与费用结算',
       '铁路货物运输价格合同

甲方（承运方）：中国铁路昆明局集团有限公司昆明物流中心
乙方（托运方）：勐腊百川国际物流有限公司

运输方式：{{businessDetail}}
服务期限：{{startDate}} 至 {{endDate}}
合同金额：{{amount}}

铁路运输条款：
{{railTerms}}

具体发运车皮、箱型、项目号、价差系数及结算金额以双方确认的铁路运单和月度对账单为准。

合同负责人：{{owner}}', 'ACTIVE', 83
where not exists (select 1 from contract_templates where id = 'CT-016');

update contract_templates
set content = concat(content, '

铁路运输条款：
{{railTerms}}')
where id = 'CT-015';

update contract_templates
set content = concat(content, '

铁路运输条款：
{{railTerms}}')
where id = 'CT-014';

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-026', 'KH-2026-026', '中国铁路昆明局集团有限公司昆明物流中心', '企业', '', '', '铁路客服', '', '', '', '', '运输客户', '铁路运价与车皮计划', '业务合作', '小九', '合作中', '已签约', '铁路运价合同相对方'
where not exists (select 1 from customers where id = 'KH-2026-026');

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-027', 'KH-2026-027', '兴华', '企业', '', '', '兴华负责人', '', '勐腊县', '', '', '运输客户', '木材铁路运输', '业务合作', '小九', '合作中', '已签约', '勐腊/岔路口至元瑞、崇州街子木材客户'
where not exists (select 1 from customers where id = 'KH-2026-027');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-026-01', 'KH-2026-026', '运输服务', '火车运输', 0, '合作中', '小九', '业务合作', '铁路运价补充合同与车皮计划'
where not exists (select 1 from customer_intentions where id = 'CI-026-01');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-027-01', 'KH-2026-027', '运输服务', '火车运输', 188000, '合作中', '小九', '业务合作', '勐腊/岔路口至元瑞、崇州街子木材铁路运输'
where not exists (select 1 from customer_intentions where id = 'CI-027-01');

insert into contracts (id, title, category, counterparty, resource, start_date, end_date, amount, owner, document_count, status,
    customer_id, resource_id, resource_type, business_detail, template_id, content, deposit, unit_price, quantity, area,
    intention_ids, intention_businesses, rail_terms)
select 'HT-2026-020', '兴华木材铁路运输服务合同', '火车运输合同', '兴华', '', '2026-11-01', '2027-10-31', '188000', '小九', 0, '履约中',
    'KH-2026-027', null, null, '火车运输', 'CT-015',
    '兴华木材铁路运输服务合同

甲方：兴华
乙方：勐腊百川国际物流有限公司
运输方式：火车运输
服务期限：2026-11-01 至 2027-10-31
合同金额：188000

铁路运输条款：
发站：勐腊
到站：元瑞市场 / 双流
箱型：70（项目号 KM2601076d）
价差系数：-35.6%
车皮价：17400 元/车（含打木架、含 9% 税）
费用构成：铁路运费 13000 + 发站倒短 2200 + 到站倒短 1920 + 打木架 200

合同负责人：小九', null, null, null, null, 'CI-027-01', '运输服务',
    '发站：勐腊
到站：元瑞市场 / 双流
箱型：70（项目号 KM2601076d）
价差系数：-35.6%
车皮价：17400 元/车（含打木架、含 9% 税）
费用构成：铁路运费 13000 + 发站倒短 2200 + 到站倒短 1920 + 打木架 200'
where not exists (select 1 from contracts where id = 'HT-2026-020');

insert into contracts (id, title, category, counterparty, resource, start_date, end_date, amount, owner, document_count, status,
    customer_id, resource_id, resource_type, business_detail, template_id, content, deposit, unit_price, quantity, area,
    intention_ids, intention_businesses, rail_terms)
select 'HT-2026-021', '铁路货物运输价格补充合同', '铁路运价合同', '中国铁路昆明局集团有限公司昆明物流中心', '', '2026-09-01', '2027-12-31', '按实际发运车皮结算', '小九', 0, '履约中',
    'KH-2026-026', null, null, '火车运输', 'CT-016',
    '铁路货物运输价格补充合同

合同编号：KWLBH-2026-103-B2
原合同编号：KWLBH-2026-103
甲方：中国铁路昆明局集团有限公司昆明物流中心
乙方：勐腊百川国际物流有限公司
服务期限：2026-09-01 至 2027-12-31

铁路运输条款：
项目号：KM2601076c / KM2601076d / KM2601076e / KM2601076f
价差系数：-27% / -35.6% / 34.1% / -33.5%
执行时间：2026-09-01 起（2026-01-01 至 08-31 执行 KM2601076a -17.1%、KM2601076b -13.5%）
计价示例：宁洱至白市驿，标重 60 吨，含保价费 30 元/车，发接送达 960 元/车，门到站 11886.80 元/车（含税）
其中：铁路运输服务费不含税 8812.24 元/车，税率 9%，增值税 648.96 元/车；铁路建设基金 0 元/车；其他服务费不含税 1352.26 元/车，税率 6%；印花税 2.20 元/车

合同负责人：小九', null, null, null, null, 'CI-026-01', '运输服务',
    '合同编号：KWLBH-2026-103-B2
原合同编号：KWLBH-2026-103
甲方：中国铁路昆明局集团有限公司昆明物流中心
乙方：勐腊百川国际物流有限公司
项目号：KM2601076c / KM2601076d / KM2601076e / KM2601076f
价差系数：-27% / -35.6% / 34.1% / -33.5%
执行时间：2026-09-01 起（1-8 月按 a -17.1%、b -13.5%）
计价示例：宁洱至白市驿，标重 60 吨，发接送达 960 元/车，门到站 11886.80 元/车（含税）
铁路运输服务费不含税 8812.24 元/车，税率 9%，增值税 648.96 元/车'
where not exists (select 1 from contracts where id = 'HT-2026-021');
