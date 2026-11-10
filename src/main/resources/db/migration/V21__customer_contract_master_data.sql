-- Customer master data, contract configuration and pledge linkage.
create table customers (
    id varchar(36) primary key,
    code varchar(60) not null unique,
    name varchar(200) not null,
    customer_nature varchar(20) not null default '企业',
    credit_code varchar(64) not null default '',
    legal_person varchar(80) not null default '',
    contact_person varchar(80) not null default '',
    contact_phone varchar(40) not null default '',
    address varchar(300) not null default '',
    bank_name varchar(160) not null default '',
    bank_account varchar(80) not null default '',
    type varchar(160) not null default '',
    service varchar(300) not null default '',
    source varchar(80) not null default '',
    owner varchar(80) not null default '',
    stage varchar(40) not null default '合作中',
    status varchar(40) not null default '已签约',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status) values
('KH-2026-018', 'KH-2026-018', '云岭生鲜供应链有限公司', '企业', '91532823MA6K3X8A2R', '李永昌', '李经理', '13887032168', '云南省西双版纳州勐腊县勐腊镇曼它拉路 18 号', '中国工商银行勐腊支行', '6222021200018436219', '综合客户', '果蔬冷藏、干线运输', '园区推荐', '张敏', '合作中', '已签约'),
('KH-2026-015', 'KH-2026-015', '华南餐饮供应链有限公司', '企业', '91440101MA5CP2BX8T', '王建华', '王经理', '13987026721', '广东省广州市白云区机场路 156 号', '中国农业银行广州白云支行', '6212260800023651728', '冷库租户', '综合冷库分区租赁', '主动拓展', '李娜', '合作中', '已签约'),
('KH-2026-023', 'KH-2026-023', '西南捷运物流有限公司', '企业', '', '', '张经理', '13800000016', '', '', '', '运输客户', '冷链干线运输', '业务合作', '张伟', '合作中', '已签约'),
('KH-2026-024', 'KH-2026-024', '昆明恒温制冷工程有限公司', '企业', '91530102MA6K2L7T6X', '赵建国', '赵经理', '13708456219', '云南省昆明市五华区学府路 45 号', '中国建设银行昆明五华支行', '6217003210012685362', '综合客户', '制冷设备维保', '业务合作', '陈晨', '合作中', '已签约'),
('KH-2026-025', 'KH-2026-025', '滇中物流器具有限公司', '企业', '91530103MA6K9M4C8W', '陈晓东', '陈经理', '13577091283', '云南省昆明市盘龙区北京路 668 号', '', '', '仓储客户', '托盘与周转箱采购', '主动拓展', '刘洋', '合作中', '已签约'),
('KH-2026-020', 'KH-2026-020', '百安物业服务有限公司', '企业', '', '', '周经理', '13800000020', '', '', '', '园区服务', '保洁与秩序维护', '业务合作', '周敏', '合作中', '已签约'),
('KH-2026-021', 'KH-2026-021', '西南商贸配送有限公司', '企业', '91530100MA6K5R2P3N', '周明远', '周经理', '18687033086', '云南省昆明市官渡区广福路 88 号', '', '', '运输客户', '待确认运输线路', '客户来访', '王强', '待签约', '待跟进');

create table park_resource_types (
    id varchar(36) primary key,
    code varchar(40) not null unique,
    name varchar(80) not null unique,
    status varchar(20) not null default 'ACTIVE',
    sort_order int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

insert into park_resource_types (id, code, name, status, sort_order) values
('PRT-001', 'OFFICE', '办公', 'ACTIVE', 10),
('PRT-002', 'FACTORY', '厂房', 'ACTIVE', 20),
('PRT-003', 'COLD_STORAGE', '冷库', 'ACTIVE', 30),
('PRT-004', 'HOUSING', '住房', 'ACTIVE', 40),
('PRT-005', 'SHOP', '商铺', 'ACTIVE', 50),
('PRT-006', 'WAREHOUSE', '仓库', 'ACTIVE', 60);

create table park_resources (
    id varchar(36) primary key,
    code varchar(60) not null unique,
    name varchar(200) not null unique,
    resource_type varchar(80) not null,
    address varchar(300) not null default '',
    note varchar(500) not null default '',
    status varchar(20) not null default 'ACTIVE',
    sort_order int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

insert into park_resources (id, code, name, resource_type, address, note, status, sort_order) values
('RES-001', 'RES-001', '百川物流智慧冷链中心', '仓库', '', '', 'ACTIVE', 10),
('RES-002', 'RES-002', '百川一号常温仓', '仓库', '', '', 'ACTIVE', 20),
('RES-003', 'RES-003', '百川二号常温仓', '仓库', '', '', 'ACTIVE', 30),
('RES-004', 'RES-004', '百川果蔬冷库', '冷库', '', '', 'ACTIVE', 40),
('RES-005', 'RES-005', '百川综合冷库', '冷库', '', '', 'ACTIVE', 50);

create table contract_categories (
    id varchar(36) primary key,
    name varchar(80) not null unique,
    status varchar(20) not null default 'ACTIVE',
    sort_order int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

insert into contract_categories (id, name, status, sort_order) values
('CC-001', '仓储服务', 'ACTIVE', 10),
('CC-002', '冷库租赁', 'ACTIVE', 20),
('CC-003', '运输服务', 'ACTIVE', 30),
('CC-004', '设备维保', 'ACTIVE', 40),
('CC-005', '采购合同', 'ACTIVE', 50),
('CC-006', '园区服务', 'ACTIVE', 60),
('CC-007', '监管协议', 'ACTIVE', 70);

create table contract_templates (
    id varchar(36) primary key,
    title varchar(200) not null,
    resource_type varchar(80) not null default '',
    category varchar(80) not null default '',
    subject varchar(300) not null default '',
    purpose varchar(500) not null default '',
    status varchar(20) not null default 'ACTIVE',
    sort_order int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

insert into contract_templates (id, title, resource_type, category, subject, purpose, status, sort_order) values
('CT-001', '办公用房租赁合同', '办公', '园区服务', '办公用房及配套工位', '作为办公及商务洽谈使用', 'ACTIVE', 10),
('CT-002', '厂房租赁合同', '厂房', '仓储服务', '标准厂房及配套附属设施', '作为生产加工及仓储使用', 'ACTIVE', 20),
('CT-003', '冷库租赁合同', '冷库', '冷库租赁', '冷链仓储库房及温控设备', '作为食品、农产品的冷藏冷冻储存使用，并遵守约定的温湿度控制要求', 'ACTIVE', 30),
('CT-004', '住房租赁合同', '住房', '园区服务', '园区配套住房', '仅作为乙方员工住宿使用', 'ACTIVE', 40),
('CT-005', '商铺租赁合同', '商铺', '园区服务', '临街商铺及经营场所', '作为商业零售及经营服务使用', 'ACTIVE', 50),
('CT-006', '仓库租赁合同', '仓库', '仓储服务', '仓储库房及装卸月台', '作为一般货物仓储使用', 'ACTIVE', 60),
('CT-007', '仓单质押监管协议', '仓库', '监管协议', '仓单质押监管服务', '对质押货物进行监管并按银行指令放货', 'ACTIVE', 70);

alter table contracts add column customer_id varchar(36);
alter table contracts add column resource_id varchar(36);
alter table contracts add column resource_type varchar(80);
alter table contracts add column template_id varchar(36);
alter table contracts add column content text;
alter table contracts add column deposit decimal(18,2);
alter table contracts add column unit_price decimal(18,2);
alter table contracts add column quantity decimal(18,3);
alter table contracts add column area decimal(18,3);
alter table contracts add column created_at timestamp not null default current_timestamp;
alter table contracts add column updated_at timestamp not null default current_timestamp;

update contracts set customer_id = (select c.id from customers c where c.name = contracts.counterparty) where customer_id is null;
update contracts set resource_id = (select r.id from park_resources r where r.name = contracts.resource) where resource_id is null;
update contracts set resource_type = (select r.resource_type from park_resources r where r.id = contracts.resource_id) where resource_type is null;

alter table contracts add constraint fk_contract_customer foreign key (customer_id) references customers(id);
alter table contracts add constraint fk_contract_resource foreign key (resource_id) references park_resources(id);
alter table contracts add constraint fk_contract_template foreign key (template_id) references contract_templates(id);

alter table pledge_businesses add column borrower_customer_id varchar(36);
alter table pledge_businesses add column owner_customer_id varchar(36);
alter table pledge_businesses add column contract_id varchar(32);
alter table pledge_businesses add column agreement_contract_id varchar(32);

alter table pledge_businesses add constraint fk_pledge_borrower_customer foreign key (borrower_customer_id) references customers(id);
alter table pledge_businesses add constraint fk_pledge_owner_customer foreign key (owner_customer_id) references customers(id);
alter table pledge_businesses add constraint fk_pledge_contract foreign key (contract_id) references contracts(id);
alter table pledge_businesses add constraint fk_pledge_agreement foreign key (agreement_contract_id) references contracts(id);

create index idx_customers_name on customers(name);
create index idx_customers_status on customers(status);
create index idx_park_resources_type on park_resources(resource_type, status);
create index idx_contract_templates_type on contract_templates(resource_type, status);
create index idx_contracts_customer on contracts(customer_id);
create index idx_contracts_resource on contracts(resource_id);
create index idx_pledge_business_customer on pledge_businesses(borrower_customer_id, owner_customer_id);
