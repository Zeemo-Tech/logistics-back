-- Configurable transport intention details and contract template bindings.
alter table customer_intentions add column business_detail varchar(80) not null default '';
alter table contracts add column business_detail varchar(80) not null default '';
alter table contract_templates add column intention_business varchar(80) not null default '';
alter table contract_templates add column business_detail varchar(80) not null default '';

insert into contract_categories (id, name, status, sort_order)
select 'CC-011', '水路运输合同', 'ACTIVE', 80 where not exists (select 1 from contract_categories where name = '水路运输合同');
insert into contract_categories (id, name, status, sort_order)
select 'CC-012', '货车运输合同', 'ACTIVE', 81 where not exists (select 1 from contract_categories where name = '货车运输合同');
insert into contract_categories (id, name, status, sort_order)
select 'CC-013', '火车运输合同', 'ACTIVE', 82 where not exists (select 1 from contract_categories where name = '火车运输合同');

insert into contract_templates (id, title, resource_type, intention_business, business_detail, category, subject, purpose, content, status, sort_order)
select 'CT-013', '水路运输服务合同模板', '', '运输服务', '水路运输', '水路运输合同', '跨境水路运输服务', '约定船舶航次、集装箱、进港审核、装船、运输及到港交接等服务事项',
       '水路运输服务合同\n\n甲方：{{customerName}}\n乙方：{{companyName}}\n运输方式：{{businessDetail}}\n服务期限：{{startDate}} 至 {{endDate}}\n合同金额：{{amount}}\n\n具体航次、集装箱清单、温度要求及资料审核以双方确认的业务单据为准。\n\n合同负责人：{{owner}}', 'ACTIVE', 80
where not exists (select 1 from contract_templates where id = 'CT-013');
insert into contract_templates (id, title, resource_type, intention_business, business_detail, category, subject, purpose, content, status, sort_order)
select 'CT-014', '货车运输服务合同模板', '', '运输服务', '货车运输', '货车运输合同', '公路货运运输服务', '约定车辆、货物、运输路线、交付及异常处理等服务事项',
       '货车运输服务合同\n\n甲方：{{customerName}}\n乙方：{{companyName}}\n运输方式：{{businessDetail}}\n服务期限：{{startDate}} 至 {{endDate}}\n合同金额：{{amount}}\n\n具体车辆、货物及运输轨迹以双方确认的业务单据为准。\n\n合同负责人：{{owner}}', 'ACTIVE', 81
where not exists (select 1 from contract_templates where id = 'CT-014');
insert into contract_templates (id, title, resource_type, intention_business, business_detail, category, subject, purpose, content, status, sort_order)
select 'CT-015', '火车运输服务合同模板', '', '运输服务', '火车运输', '火车运输合同', '铁路运输服务', '约定班列、货物、站点、装卸及到站交接等服务事项',
       '火车运输服务合同\n\n甲方：{{customerName}}\n乙方：{{companyName}}\n运输方式：{{businessDetail}}\n服务期限：{{startDate}} 至 {{endDate}}\n合同金额：{{amount}}\n\n具体班列、货物及到站交接以双方确认的业务单据为准。\n\n合同负责人：{{owner}}', 'ACTIVE', 82
where not exists (select 1 from contract_templates where id = 'CT-015');

update customer_intentions set business_detail = '水路运输'
where business = '运输服务' and business_detail = '' and notes like '%水路%';
