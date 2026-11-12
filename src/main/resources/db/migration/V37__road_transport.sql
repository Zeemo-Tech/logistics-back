-- Road transport module: vehicles, pickups, station appointments, weighing, delivery, pricing and settlement.

create table road_vehicles (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    plate varchar(32) not null,
    trailer varchar(32) not null default '',
    vehicle_type varchar(40) not null default '高栏车',
    driver varchar(80) not null default '',
    phone varchar(40) not null default '',
    id_card varchar(32) not null default '',
    fleet varchar(120) not null default '',
    status varchar(24) not null default '可用',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (length(plate) > 0)
);

create table road_pickups (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    entrust_no varchar(48) not null default '',
    customer_name varchar(160) not null,
    factory varchar(160) not null default '',
    cargo varchar(80) not null default '',
    tons decimal(10, 2) not null,
    pieces int not null default 0,
    consignee_name varchar(80) not null default '',
    consignee_phone varchar(40) not null default '',
    destination varchar(160) not null default '',
    pickup_date varchar(20) not null default '',
    vehicle_id varchar(36) references road_vehicles(id),
    plate varchar(32) not null default '',
    driver varchar(80) not null default '',
    status varchar(24) not null default '待派车',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (tons > 0)
);

create table road_appointments (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    appointment_no varchar(48) not null default '',
    pickup_id varchar(36) references road_pickups(id),
    station varchar(80) not null default '勐腊',
    yard varchar(80) not null default '勐腊货运',
    cargo varchar(80) not null default '胶合板',
    tons decimal(10, 2) not null default 0,
    entry_date varchar(20) not null default '',
    cutoff_at varchar(32) not null default '',
    to_station varchar(80) not null default '双流',
    driver_accepted varchar(24) not null default '未接单',
    sent_tons decimal(10, 2) not null default 0,
    status varchar(24) not null default '待受理',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table road_weighings (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    weigh_time varchar(32) not null default '',
    plate varchar(32) not null,
    cargo varchar(80) not null default '胶合板',
    shipper varchar(160) not null default '百川国际物流',
    transporter varchar(160) not null default '百川国际物流',
    gross_weight decimal(10, 2) not null,
    tare_weight decimal(10, 2) not null,
    net_weight decimal(10, 2) not null,
    flow_no varchar(48) not null default '',
    wagon_no varchar(32) not null default '',
    operator varchar(80) not null default '',
    status varchar(16) not null default '有效',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (gross_weight > 0 and tare_weight >= 0 and gross_weight > tare_weight),
    check (net_weight > 0)
);

create table road_deliveries (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    pickup_id varchar(36) references road_pickups(id),
    consignee_name varchar(80) not null,
    consignee_phone varchar(40) not null default '',
    address varchar(300) not null default '',
    destination varchar(80) not null default '',
    wagon_no varchar(32) not null default '',
    tons decimal(10, 2) not null default 0,
    pieces int not null default 0,
    short_haul_company varchar(160) not null default '',
    distance_km decimal(10, 2) not null default 0,
    short_haul_fee decimal(12, 2) not null default 0,
    delivery_at varchar(32) not null default '',
    signed_by varchar(80) not null default '',
    status varchar(24) not null default '待配送',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table road_quotes (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    customer_name varchar(160) not null,
    from_point varchar(80) not null default '勐腊',
    to_station varchar(120) not null,
    cargo varchar(80) not null default '胶合板',
    mode varchar(24) not null default '铁路门到门',
    actual_tons decimal(10, 2) not null,
    rail_freight decimal(12, 2) not null default 0,
    fixed_fee decimal(12, 2) not null default 60,
    short_haul_per_ton decimal(12, 2) not null default 0,
    profit_per_ton decimal(12, 2) not null default 15,
    cost_per_ton decimal(12, 2) not null default 0,
    quote_per_ton decimal(12, 2) not null default 0,
    total_price decimal(14, 2) not null default 0,
    status varchar(24) not null default '草稿',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (actual_tons > 0)
);

create table road_settlements (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    customer_name varchar(160) not null,
    pickup_id varchar(36) references road_pickups(id),
    freight_amount decimal(12, 2) not null default 0,
    tax_rate decimal(5, 2) not null default 9,
    invoice_no varchar(80) not null default '',
    invoice_date varchar(20) not null default '',
    payment_status varchar(24) not null default '待收款',
    received_amount decimal(12, 2) not null default 0,
    received_at varchar(20) not null default '',
    bank_ref varchar(80) not null default '',
    prepayment_deducted decimal(12, 2) not null default 0,
    status varchar(24) not null default '待对账',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_road_pickups_vehicle on road_pickups(vehicle_id);
create index idx_road_appointments_pickup on road_appointments(pickup_id);
create index idx_road_deliveries_pickup on road_deliveries(pickup_id);
create index idx_road_settlements_pickup on road_settlements(pickup_id);

insert into road_vehicles (id, code, plate, trailer, vehicle_type, driver, phone, id_card, fleet, status, notes) values
('ROAD-VEHICLE-001', 'RV-20261103-001', '云K28626', '云J3980挂', '高栏车', '李玉雄', '13887291861', '533001197706110338', '个人', '在途', '常跑勐腊货场提货送站'),
('ROAD-VEHICLE-002', 'RV-20261103-002', '云A12345', '云A6789挂', '平板车', '张师傅', '13800000003', '', '顺达车队', '可用', '');

insert into road_pickups (id, code, entrust_no, customer_name, factory, cargo, tons, pieces, consignee_name, consignee_phone, destination, pickup_date, vehicle_id, plate, driver, status, notes) values
('ROAD-PICKUP-001', 'PU-20261103-001', 'TL-20261103-01', '云南英茂糖业有限公司', '新糖原生物工程(勐腊)有限公司', '白糖', 32, 0, '李玉雄', '13887291861', '勐腊货场', '2026-11-03', 'ROAD-VEHICLE-001', '云K28626', '李玉雄', '已送站', '1000吨白糖中本次提32吨'),
('ROAD-PICKUP-002', 'PU-20261105-002', 'TL-20261105-02', '兴华', '瑞祥 / 海发', '胶合板', 58, 60, '胡总', '18244259550', '元瑞市场', '2026-11-05', 'ROAD-VEHICLE-001', '云K28626', '李玉雄', '已送站', '瑞祥18包 + 海发6包，过磅净重57.54'),
('ROAD-PICKUP-003', 'PU-20261108-003', 'TL-20261108-03', '顺达', '顺达', '胶合板', 46.9, 24, '聂其良', '13551230178', '崇州街子', '2026-11-08', 'ROAD-VEHICLE-001', '云K28626', '李玉雄', '已提货', '车皮3401228，24件');

insert into road_appointments (id, code, appointment_no, pickup_id, station, yard, cargo, tons, entry_date, cutoff_at, to_station, driver_accepted, sent_tons, status, notes) values
('ROAD-APPOINTMENT-001', 'RA-20261105-001', 'MWMG003737', 'ROAD-PICKUP-002', '勐腊', '勐腊货运', '胶合板', 58, '2026-11-05', '2026-11-07 23:59:59', '双流', '已接单', 58, '已进站', '1位司机已接单'),
('ROAD-APPOINTMENT-002', 'RA-20261108-002', 'MWMG003740', 'ROAD-PICKUP-003', '勐腊', '勐腊货运', '胶合板', 58, '2026-11-08', '2026-11-10 23:59:59', '双流', '未接单', 0, '受理通过', '已分配1位司机，尚未接单');

insert into road_weighings (id, code, weigh_time, plate, cargo, shipper, transporter, gross_weight, tare_weight, net_weight, flow_no, wagon_no, operator, status, notes) values
('ROAD-WEIGHING-001', 'RW-20261104-001', '2026-11-04 15:20:11', '云K28626', '胶合板', '百川国际物流', '百川国际物流', 73.96, 16.42, 57.54, 'A202611040001', '3465352', '范赛峰', '有效', ''),
('ROAD-WEIGHING-002', 'RW-20261104-002', '2026-11-04 15:21:13', '云K28626', '胶合板', '百川国际物流', '百川国际物流', 71.62, 16.42, 55.20, 'A202611040002', '3800537', '范赛峰', '有效', ''),
('ROAD-WEIGHING-003', 'RW-20261108-003', '2026-11-08 10:07:23', '云K28626', '胶合板', '百川国际物流', '百川国际物流', 63.20, 16.30, 46.90, 'A202611080001', '3401228', '朱春剑', '有效', '备注净重46.9');

insert into road_deliveries (id, code, pickup_id, consignee_name, consignee_phone, address, destination, wagon_no, tons, pieces, short_haul_company, distance_km, short_haul_fee, delivery_at, signed_by, status, notes) values
('ROAD-DELIVERY-001', 'RD-20261109-001', 'ROAD-PICKUP-002', '彭清勇', '13648134230', '元瑞市场', '元瑞市场', '3465352', 57.54, 24, '顺达', 18, 600, '2026-11-09 14:34', '彭清勇', '已签收', '结算运费17400'),
('ROAD-DELIVERY-002', 'RD-20261109-002', 'ROAD-PICKUP-002', '胡泽东', '13551806077', '元瑞', '元瑞市场', '3805609', 57.50, 24, '顺达', 18, 600, '2026-11-09 14:34', '胡泽东', '已签收', '结算运费17400'),
('ROAD-DELIVERY-003', 'RD-20261109-003', 'ROAD-PICKUP-002', '胡总', '18244259550', '成都市双流区九江镇元瑞材料市场（鑫鸿达木业）', '元瑞市场', '3800537', 55.20, 24, '瑞祥 / 海发', 20, 650, '2026-11-09 16:55', '胡总', '已签收', '结算运费18700'),
('ROAD-DELIVERY-004', 'RD-20261109-004', 'ROAD-PICKUP-003', '聂其良', '13551230178', '崇州街子', '崇州街子', '3401228', 46.90, 24, '顺达', 65, 1800, '', '', '待配送', '待客户确认后配送');

insert into road_quotes (id, code, customer_name, from_point, to_station, cargo, mode, actual_tons, rail_freight, fixed_fee, short_haul_per_ton, profit_per_ton, cost_per_ton, quote_per_ton, total_price, status, notes) values
('ROAD-QUOTE-001', 'RQ-20261103-001', '铭禧', '勐腊', '漳州东站', '白糖', '铁路门到门', 55, 36297.70, 60, 80, 15, 799.96, 815, 44825, '已报价', '铁路运费36297.7÷55=659.9582，加60固定费、到站倒短待确认'),
('ROAD-QUOTE-002', 'RQ-20261103-002', '四川木头源商贸有限公司', '勐腊', '元瑞市场', '胶合板', '铁路门到门', 55.20, 13000, 60, 0, 15, 295.51, 311, 17167.20, '已成交', '门到门结算按车皮18700'),
('ROAD-QUOTE-003', 'RQ-20261103-003', '广西客户', '勐腊', '南宁', '木材', '公路直发', 50, 0, 0, 500, 15, 500, 515, 25750, '已报价', '公路直发对比价，约500多元/吨');

insert into road_settlements (id, code, customer_name, pickup_id, freight_amount, tax_rate, invoice_no, invoice_date, payment_status, received_amount, received_at, bank_ref, prepayment_deducted, status, notes) values
('ROAD-SETTLEMENT-001', 'RB-20261110-001', '四川木头源商贸有限公司', 'ROAD-PICKUP-002', 18700, 9, 'FP-20261110-001', '2026-11-10', '已收款', 18700, '2026-11-10', '51420782597947129973', 0, '已收款', '客户收付款入账通知已回单'),
('ROAD-SETTLEMENT-002', 'RB-20261110-002', '兴华', 'ROAD-PICKUP-002', 17400, 9, '', '', '待收款', 0, '', '', 0, '已对账', '已对账，待开票'),
('ROAD-SETTLEMENT-003', 'RB-20261110-003', '彭清勇', 'ROAD-PICKUP-002', 17400, 9, '', '', '部分收款', 10000, '2026-11-10', '', 0, '已收款', '预付款抵扣2.2万元后补收');
