-- Rail arrival delivery and consignee sign-off.

create table rail_deliveries (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    demand_id varchar(36) not null references rail_demands(id),
    wagon_no varchar(32) not null default '',
    consignee_name varchar(80) not null,
    consignee_phone varchar(40) not null default '',
    address varchar(300) not null default '',
    destination varchar(80) not null default '',
    tons decimal(10, 2) not null default 0,
    pieces int not null default 0,
    short_haul_company varchar(160) not null default '',
    distance_km decimal(10, 2) not null default 0,
    short_haul_fee decimal(12, 2) not null default 0,
    delivered_at varchar(32) not null default '',
    signed_by varchar(80) not null default '',
    status varchar(24) not null default '待配送',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_rail_deliveries_demand on rail_deliveries(demand_id);

insert into rail_deliveries (id, code, demand_id, wagon_no, consignee_name, consignee_phone, address, destination, tons, pieces, short_haul_company, distance_km, short_haul_fee, delivered_at, signed_by, status, notes) values
('RAIL-DELIVERY-001', 'RDL-20261109-001', 'RAIL-DEMAND-001', 'XN-70-001', '胡总', '18244259550', '成都市双流区九江镇元瑞材料市场（鑫鸿达木业）', '元瑞市场', 70, 24, '顺达', 20, 650, '', '', '待配送', '到站后由当地倒短公司配送'),
('RAIL-DELIVERY-002', 'RDL-20261109-002', 'RAIL-DEMAND-002', '', '彭清勇', '13648134230', '元瑞市场', '元瑞市场', 70, 24, '顺达', 18, 600, '', '', '待配送', '等待到站后安排配送');
