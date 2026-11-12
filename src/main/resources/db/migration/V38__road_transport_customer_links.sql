-- Link road transport to customer archives, transport intentions and road contracts.

alter table road_pickups add column customer_id varchar(36) references customers(id);
alter table road_pickups add column intention_id varchar(36) references customer_intentions(id);
alter table road_pickups add column contract_id varchar(32) references contracts(id);
alter table road_quotes add column customer_id varchar(36) references customers(id);
alter table road_settlements add column customer_id varchar(36) references customers(id);

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-028', 'KH-2026-028', '云南英茂糖业有限公司', '企业', '', '', '李玉雄', '13887291861', '云南省西双版纳州勐腊县', '', '', '运输客户', '白糖公路运输', '业务合作', '小九', '合作中', '已签约', '白糖提货与送站'
where not exists (select 1 from customers where id = 'KH-2026-028');

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-029', 'KH-2026-029', '顺达', '企业', '', '', '顺达负责人', '13800000003', '勐腊县', '', '', '运输客户', '胶合板公路运输', '业务合作', '小九', '合作中', '已签约', '工厂提货与到站配送'
where not exists (select 1 from customers where id = 'KH-2026-029');

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-030', 'KH-2026-030', '四川木头源商贸有限公司', '企业', '91510116MA7KU7QF9B', '', '胡总', '18244259550', '成都市双流区九江街道万泉路172号31栋附14号', '中国农业银行股份有限公司成都簇桥支行', '22821601040020770', '运输客户', '木材公路运输与门到门配送', '业务合作', '小九', '合作中', '已签约', '元瑞材料市场收货与付款方'
where not exists (select 1 from customers where id = 'KH-2026-030');

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-031', 'KH-2026-031', '铭禧', '企业', '', '', '铭禧负责人', '', '勐腊县', '', '', '运输客户', '白糖铁路门到门', '业务合作', '小九', '合作中', '已签约', '漳州东站门到门运价咨询'
where not exists (select 1 from customers where id = 'KH-2026-031');

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-032', 'KH-2026-032', '广西客户', '企业', '', '', '广西客户负责人', '', '广西', '', '', '运输客户', '木材公路直发', '业务合作', '小九', '合作中', '已签约', '广西方向公路直发比价'
where not exists (select 1 from customers where id = 'KH-2026-032');

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-033', 'KH-2026-033', '彭清勇', '个人', '', '', '彭清勇', '13648134230', '成都市双流区', '', '', '运输客户', '胶合板到站配送', '业务合作', '小九', '合作中', '已签约', '元瑞市场收货人'
where not exists (select 1 from customers where id = 'KH-2026-033');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-028-01', 'KH-2026-028', '运输服务', '公路运输', 18800, '合作中', '小九', '业务合作', '白糖提货送站与到站配送'
where not exists (select 1 from customer_intentions where id = 'CI-028-01');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-027-02', 'KH-2026-027', '运输服务', '公路运输', 18700, '合作中', '小九', '业务合作', '兴华木材公路门到门'
where not exists (select 1 from customer_intentions where id = 'CI-027-02');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-029-01', 'KH-2026-029', '运输服务', '公路运输', 17400, '合作中', '小九', '业务合作', '顺达胶合板公路运输'
where not exists (select 1 from customer_intentions where id = 'CI-029-01');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-030-01', 'KH-2026-030', '运输服务', '公路运输', 18700, '合作中', '小九', '业务合作', '四川木头源木材公路门到门'
where not exists (select 1 from customer_intentions where id = 'CI-030-01');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-031-01', 'KH-2026-031', '运输服务', '公路运输', 44825, '合作中', '小九', '业务合作', '漳州东门到门运价'
where not exists (select 1 from customer_intentions where id = 'CI-031-01');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-032-01', 'KH-2026-032', '运输服务', '公路运输', 25750, '合作中', '小九', '业务合作', '广西公路直发'
where not exists (select 1 from customer_intentions where id = 'CI-032-01');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-033-01', 'KH-2026-033', '运输服务', '公路运输', 17400, '合作中', '小九', '业务合作', '元瑞市场到站配送'
where not exists (select 1 from customer_intentions where id = 'CI-033-01');

insert into contracts (id, title, category, counterparty, resource, start_date, end_date, amount, owner, document_count, status,
    customer_id, resource_id, resource_type, business_detail, template_id, content, deposit, unit_price, quantity, area,
    intention_ids, intention_businesses)
select 'HT-2026-022', '云南英茂糖业公路运输合同', '公路运输合同', '云南英茂糖业有限公司', '', '2026-11-03', '2027-11-02', '18800', '小九', 0, '履约中',
    'KH-2026-028', null, null, '公路运输', 'CT-014',
    '云南英茂糖业公路运输合同

甲方：云南英茂糖业有限公司
乙方：勐腊百川国际物流有限公司
运输方式：公路运输
服务期限：2026-11-03 至 2027-11-02
合同金额：18800

提货与配送条款：工厂提货、勐腊货场过磅、进站预约、到站配送以双方确认的提货单、过磅单和配送签收单为准。

合同负责人：小九', null, null, null, null, 'CI-028-01', '运输服务'
where not exists (select 1 from contracts where id = 'HT-2026-022');

insert into contracts (id, title, category, counterparty, resource, start_date, end_date, amount, owner, document_count, status,
    customer_id, resource_id, resource_type, business_detail, template_id, content, deposit, unit_price, quantity, area,
    intention_ids, intention_businesses)
select 'HT-2026-023', '兴华木材公路运输合同', '公路运输合同', '兴华', '', '2026-11-05', '2027-11-04', '18700', '小九', 0, '履约中',
    'KH-2026-027', null, null, '公路运输', 'CT-014',
    '兴华木材公路运输合同

甲方：兴华
乙方：勐腊百川国际物流有限公司
运输方式：公路运输
服务期限：2026-11-05 至 2027-11-04
合同金额：18700

门到门条款：瑞祥/海发提货、勐腊货场过磅、进站装车、到元瑞市场配送签收，按车皮结算。

合同负责人：小九', null, null, null, null, 'CI-027-02', '运输服务'
where not exists (select 1 from contracts where id = 'HT-2026-023');

insert into contracts (id, title, category, counterparty, resource, start_date, end_date, amount, owner, document_count, status,
    customer_id, resource_id, resource_type, business_detail, template_id, content, deposit, unit_price, quantity, area,
    intention_ids, intention_businesses)
select 'HT-2026-024', '顺达胶合板公路运输合同', '公路运输合同', '顺达', '', '2026-11-08', '2027-11-07', '17400', '小九', 0, '履约中',
    'KH-2026-029', null, null, '公路运输', 'CT-014',
    '顺达胶合板公路运输合同

甲方：顺达
乙方：勐腊百川国际物流有限公司
运输方式：公路运输
服务期限：2026-11-08 至 2027-11-07
合同金额：17400

配送条款：顺达提货至勐腊货场，按车皮号交接并完成到站配送。

合同负责人：小九', null, null, null, null, 'CI-029-01', '运输服务'
where not exists (select 1 from contracts where id = 'HT-2026-024');

insert into contracts (id, title, category, counterparty, resource, start_date, end_date, amount, owner, document_count, status,
    customer_id, resource_id, resource_type, business_detail, template_id, content, deposit, unit_price, quantity, area,
    intention_ids, intention_businesses)
select 'HT-2026-025', '四川木头源木材门到门运输合同', '公路运输合同', '四川木头源商贸有限公司', '', '2026-11-01', '2027-10-31', '18700', '小九', 0, '履约中',
    'KH-2026-030', null, null, '公路运输', 'CT-014',
    '四川木头源木材门到门运输合同

甲方：四川木头源商贸有限公司
乙方：勐腊百川国际物流有限公司
运输方式：公路运输
服务期限：2026-11-01 至 2027-10-31
合同金额：18700

门到门条款：勐腊提货、过磅、进站，双流/元瑞/崇州街子到站配送并签收。

合同负责人：小九', null, null, null, null, 'CI-030-01', '运输服务'
where not exists (select 1 from contracts where id = 'HT-2026-025');

update road_pickups set customer_id = 'KH-2026-028', intention_id = 'CI-028-01', contract_id = 'HT-2026-022' where code = 'PU-20261103-001';
update road_pickups set customer_id = 'KH-2026-027', intention_id = 'CI-027-02', contract_id = 'HT-2026-023' where code = 'PU-20261105-002';
update road_pickups set customer_id = 'KH-2026-029', intention_id = 'CI-029-01', contract_id = 'HT-2026-024' where code = 'PU-20261108-003';

update road_quotes set customer_id = 'KH-2026-031' where code = 'RQ-20261103-001';
update road_quotes set customer_id = 'KH-2026-030' where code = 'RQ-20261103-002';
update road_quotes set customer_id = 'KH-2026-032' where code = 'RQ-20261103-003';

update road_settlements set customer_id = 'KH-2026-030' where code = 'RB-20261110-001';
update road_settlements set customer_id = 'KH-2026-027' where code = 'RB-20261110-002';
update road_settlements set customer_id = 'KH-2026-033' where code = 'RB-20261110-003';
