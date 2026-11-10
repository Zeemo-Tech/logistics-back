-- Persist customer intentions, follow-up visits and contract intention links.
create table customer_intentions (
    id varchar(36) primary key,
    customer_id varchar(36) not null references customers(id),
    business varchar(80) not null,
    amount decimal(18,2),
    stage varchar(40) not null default '新来访',
    owner varchar(80) not null default '',
    source varchar(80) not null default '',
    next_follow_up_at varchar(32) not null default '',
    notes varchar(2000) not null default '',
    updated_at timestamp not null default current_timestamp,
    created_at timestamp not null default current_timestamp
);

create table customer_visits (
    id varchar(36) primary key,
    customer_id varchar(36) not null references customers(id),
    intention_id varchar(36) references customer_intentions(id),
    visited_at varchar(32) not null default '',
    visitor varchar(80) not null default '',
    visitor_phone varchar(40) not null default '',
    interest varchar(80) not null default '',
    requirement varchar(1000) not null default '',
    expected_start varchar(32) not null default '',
    budget varchar(120) not null default '',
    host varchar(80) not null default '',
    note varchar(2000) not null default '',
    next_action varchar(200) not null default '',
    next_follow_up_at varchar(32) not null default '',
    stage varchar(40) not null default '新来访',
    created_at timestamp not null default current_timestamp
);

alter table customers add column last_follow_up_at varchar(32) not null default '';
alter table customers add column next_follow_up_at varchar(32) not null default '';

alter table contracts add column intention_ids varchar(2000) not null default '';
alter table contracts add column intention_businesses varchar(2000) not null default '';
alter table contract_templates add column content text;

create index idx_customer_intentions_customer on customer_intentions(customer_id, updated_at);
create index idx_customer_intentions_stage on customer_intentions(stage);
create index idx_customer_visits_customer on customer_visits(customer_id, visited_at);
create index idx_customer_visits_intention on customer_visits(intention_id);

insert into customer_intentions (id, customer_id, business, amount, stage, owner, source, next_follow_up_at, notes) values
('CI-018-01', 'KH-2026-018', '仓储服务', 120000, '合作中', '张敏', '园区推荐', '', '果蔬冷藏、干线运输'),
('CI-018-02', 'KH-2026-018', '运输服务', 85000, '方案报价', '张敏', '园区推荐', '', '干线运输线路报价中'),
('CI-015-01', 'KH-2026-015', '冷库租赁', 85000, '合作中', '李娜', '主动拓展', '', '综合冷库分区租赁'),
('CI-023-01', 'KH-2026-023', '运输服务', 168000, '合作中', '张伟', '业务合作', '', '冷链干线运输'),
('CI-024-01', 'KH-2026-024', '设备维保', 65000, '合作中', '陈晨', '业务合作', '', '制冷设备维保'),
('CI-025-01', 'KH-2026-025', '采购合作', 50000, '新来访', '刘洋', '主动拓展', '2026-08-22', '托盘与周转箱采购'),
('CI-021-01', 'KH-2026-021', '仓储服务', 150000, '待签约', '王强', '客户来访', '2026-08-20', '短期周转仓，旺季可扩容'),
('CI-021-02', 'KH-2026-021', '运输服务', 280000, '方案报价', '王强', '客户来访', '2026-08-18', '昆明至滇南冷链配送'),
('CI-020-01', 'KH-2026-020', '综合服务', 462000, '合作中', '周敏', '业务合作', '', '园区保洁与秩序维护');
