-- Rail (train) transport module: route rates, quotes, daily demands, dispatch, tracking and settlement.

create table rail_rates (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    from_point varchar(80) not null,
    to_station varchar(80) not null,
    box_type int not null,
    project_code varchar(32) not null default '',
    price_per_wagon decimal(12, 2) not null,
    cost_rail decimal(12, 2) not null default 0,
    cost_short_haul_from decimal(12, 2) not null default 0,
    cost_short_haul_to decimal(12, 2) not null default 0,
    cost_wooden_frame decimal(12, 2) not null default 0,
    tax_rate decimal(5, 2) not null default 9,
    price_coeff varchar(16) not null default '',
    effective_from varchar(20) not null default '',
    status varchar(16) not null default '生效',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (box_type in (58, 60, 70)),
    check (price_per_wagon >= 0)
);

create table rail_quotes (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    customer_name varchar(160) not null,
    rate_id varchar(36) references rail_rates(id),
    box_type int not null,
    tons decimal(10, 2) not null default 0,
    quote_price decimal(12, 2) not null default 0,
    cost_total decimal(12, 2) not null default 0,
    tax_rate decimal(5, 2) not null default 9,
    unit_price decimal(12, 2) not null default 0,
    margin_target decimal(5, 2) not null default 8,
    gross_profit decimal(12, 2) not null default 0,
    status varchar(16) not null default '草稿',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table rail_demands (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    load_date varchar(20) not null,
    from_point varchar(80) not null,
    to_station varchar(80) not null,
    customer_name varchar(160) not null default '',
    cargo varchar(80) not null default '胶合板',
    category varchar(40) not null default '木材',
    consignor varchar(160) not null default '',
    consignee varchar(160) not null default '',
    wagon_type varchar(24) not null default '棚车',
    box_type int not null,
    project_code varchar(32) not null default '',
    tons decimal(10, 2) not null,
    bales int not null default 0,
    volume decimal(10, 2) not null default 0,
    station_fee_paid varchar(8) not null default '是',
    stack_height decimal(5, 2) not null default 2.6,
    status varchar(16) not null default '草稿',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (box_type in (58, 60, 70)),
    check (tons > 0),
    check (stack_height > 0)
);

create table rail_dispatches (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    demand_id varchar(36) not null references rail_demands(id),
    pickup_point varchar(120) not null default '',
    bales int not null default 0,
    box_type int not null default 70,
    wagon_no varchar(32) not null default '',
    driver varchar(80) not null default '',
    phone varchar(40) not null default '',
    arrive_at varchar(32) not null default '',
    actual_tons decimal(10, 2) not null default 0,
    actual_bales int not null default 0,
    status varchar(16) not null default '待派车',
    exception_type varchar(32) not null default '无',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table rail_shipments (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    demand_id varchar(36) not null references rail_demands(id),
    wagon_no varchar(32) not null,
    depart_at varchar(32) not null default '',
    arrive_at varchar(32) not null default '',
    delivered_at varchar(32) not null default '',
    status varchar(16) not null default '待发运',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table rail_settlements (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    customer_name varchar(160) not null default '',
    quote_id varchar(36) references rail_quotes(id),
    quote_price decimal(12, 2) not null default 0,
    cost_total decimal(12, 2) not null default 0,
    tax_rate decimal(5, 2) not null default 9,
    gross_profit decimal(12, 2) not null default 0,
    status varchar(16) not null default '待对账',
    settled_at varchar(20) not null default '',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_rail_quotes_rate on rail_quotes(rate_id);
create index idx_rail_demands_load_date on rail_demands(load_date);
create index idx_rail_dispatches_demand on rail_dispatches(demand_id);
create index idx_rail_shipments_demand on rail_shipments(demand_id);
create index idx_rail_settlements_quote on rail_settlements(quote_id);

insert into rail_rates (id, code, from_point, to_station, box_type, project_code, price_per_wagon, cost_rail, cost_short_haul_from, cost_short_haul_to, cost_wooden_frame, tax_rate, price_coeff, effective_from, status, notes) values
('RAIL-RATE-001', 'RA-20261102-001', '勐腊', '元瑞市场', 70, 'KM2601076d', 17400, 13000, 2200, 1920, 200, 9, '-35.6%', '2026-11-02', '生效', '勐腊发元瑞，含打木架与9个点税'),
('RAIL-RATE-002', 'RA-20261102-002', '勐捧', '元瑞市场', 70, 'KM2601076d', 18800, 13000, 2200, 1952, 200, 9, '-35.6%', '2026-11-02', '生效', '成本 1952+2200+13000+200=17352'),
('RAIL-RATE-003', 'RA-20261102-003', '勐满', '元瑞市场', 70, 'KM2601076d', 19000, 13000, 2200, 1952, 200, 9, '-35.6%', '2026-11-02', '生效', ''),
('RAIL-RATE-004', 'RA-20261102-004', '岔路口', '崇州街子', 70, 'KM2601076d', 18800, 13000, 2200, 1920, 200, 9, '-35.6%', '2026-11-02', '生效', '兴华/新华收货'),
('RAIL-RATE-005', 'RA-20261102-005', '勐捧', '元瑞市场', 60, 'KM2601076c', 18000, 13000, 2200, 1952, 200, 9, '-27%', '2026-11-02', '生效', '60箱项目号与70箱分开提报');

insert into rail_quotes (id, code, customer_name, rate_id, box_type, tons, quote_price, cost_total, tax_rate, unit_price, margin_target, gross_profit, status, notes) values
('RAIL-QUOTE-001', 'RQ-20261102-001', '兴华', 'RAIL-RATE-002', 70, 48, 18800, 17352, 9, 391.67, 8, 1448, '已通过', '报价含利润，成本 17320/17352 两种口径待统一'),
('RAIL-QUOTE-002', 'RQ-20261102-002', '吴总', 'RAIL-RATE-001', 70, 70, 17400, 17320, 9, 248.57, 8, 80, '待审批', '勐腊发元瑞');

insert into rail_demands (id, code, load_date, from_point, to_station, customer_name, cargo, category, consignor, consignee, wagon_type, box_type, project_code, tons, bales, volume, station_fee_paid, stack_height, status, notes) values
('RAIL-DEMAND-001', 'RD-20261103-001', '2026-11-04', '勐腊', '双流', '兴华', '胶合板', '木材', '勐腊百川国际物流有限公司', '成都市战虎农资储运有限公司', '棚车', 70, 'KM2601076d', 70, 70, 80, '是', 2.6, '已装车', '本批无58/60箱，只能装70箱'),
('RAIL-DEMAND-002', 'RD-20261103-002', '2026-11-04', '勐腊', '双流', '兴华', '胶合板', '木材', '勐腊百川国际物流有限公司', '成都市战虎农资储运有限公司', '棚车', 70, 'KM2601076d', 70, 70, 80, '是', 2.6, '已受理', ''),
('RAIL-DEMAND-003', 'RD-20261103-003', '2026-11-04', '勐腊', '双流', '吴总', '胶合板', '木材', '勐腊百川国际物流有限公司', '成都市战虎农资储运有限公司', '棚车', 60, 'KM2601076c', 60, 60, 80, '是', 2.6, '已提报', '60箱使用 KM2601076c 项目号'),
('RAIL-DEMAND-004', 'RD-20261102-004', '2026-11-03', '岔路口', '崇州街子', '兴华', '木材', '木材', '勐腊百川国际物流有限公司', '成都市战虎农资储运有限公司', '棚车', 70, 'KM2601076d', 48, 24, 70, '是', 2.5, '草稿', '实测仅48吨，低于55吨经济装载线');

insert into rail_dispatches (id, code, demand_id, pickup_point, bales, box_type, wagon_no, driver, phone, arrive_at, actual_tons, actual_bales, status, exception_type, notes) values
('RAIL-DISPATCH-001', 'RP-20261102-001', 'RAIL-DEMAND-001', '瑞祥 / 海发', 24, 70, 'XN-70-001', '李师傅', '13800000001', '2026-11-03 12:00', 70, 24, '已装完', '无', '瑞祥18包 + 海发6包'),
('RAIL-DISPATCH-002', 'RP-20261102-002', 'RAIL-DEMAND-002', '新华', 18, 70, '', '王师傅', '13800000002', '2026-11-03 14:00', 0, 0, '待派车', '无', '');

insert into rail_shipments (id, code, demand_id, wagon_no, depart_at, arrive_at, delivered_at, status, notes) values
('RAIL-SHIPMENT-001', 'RS-20261103-001', 'RAIL-DEMAND-001', 'XN-70-001', '2026-11-04 08:00', '', '', '运输中', '勐腊→双流，胶合板120吨批次中的第一车');

insert into rail_settlements (id, code, customer_name, quote_id, quote_price, cost_total, tax_rate, gross_profit, status, settled_at, notes) values
('RAIL-SETTLEMENT-001', 'RB-20261103-001', '兴华', 'RAIL-QUOTE-001', 18800, 17352, 9, 1448, '待对账', '', '待客户确认实际吨数后结算');
