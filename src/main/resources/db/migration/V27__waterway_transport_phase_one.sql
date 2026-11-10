-- Waterway transport phase one schema and demo records.
create table waterway_customer_profiles (
    customer_id varchar(36) primary key references customers(id),
    customer_type varchar(32) not null default '货主',
    cooperation_status varchar(24) not null default '合作中',
    framework_contract varchar(300) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

insert into waterway_customer_profiles (customer_id, customer_type, cooperation_status, framework_contract)
select id,
       case when type like '%代理%' then '代理公司' when type like '%运输%' then '合作企业' else '货主' end,
       case when status in ('暂停合作', '已终止') then '暂停合作' else '合作中' end,
       case when type = '运输客户' then '冷链运输框架协议' else '' end
from customers
where id in ('KH-2026-018', 'KH-2026-015', 'KH-2026-023', 'KH-2026-024', 'KH-2026-025', 'KH-2026-020', 'KH-2026-021');

create table waterway_vessels (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    name varchar(160) not null,
    vessel_type varchar(48) not null default '集装箱船',
    design_capacity int not null,
    available_capacity int not null,
    captain varchar(80) not null default '',
    phone varchar(40) not null default '',
    status varchar(24) not null default '可安排',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (design_capacity > 0),
    check (available_capacity >= 0 and available_capacity <= design_capacity)
);

create table waterway_voyages (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    voyage_no varchar(48) not null unique,
    vessel_id varchar(36) not null references waterway_vessels(id),
    direction varchar(16) not null default '下水',
    origin_port varchar(80) not null default '',
    destination_port varchar(80) not null default '',
    planned_departure varchar(32) not null,
    actual_departure varchar(32) not null default '',
    estimated_arrival varchar(32) not null default '',
    actual_arrival varchar(32) not null default '',
    available_capacity int not null,
    status varchar(24) not null default '计划中',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (available_capacity >= 0)
);

create table waterway_bookings (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    customer_id varchar(36) not null references customers(id),
    direction varchar(16) not null,
    planned_date varchar(16) not null,
    preferred_vessel_id varchar(36) references waterway_vessels(id),
    voyage_id varchar(36) references waterway_voyages(id),
    container_count int not null,
    heavy_count int not null default 0,
    empty_count int not null default 0,
    container_no varchar(24) not null default '',
    cargo_type varchar(120) not null default '',
    estimated_weight decimal(12, 3) not null default 0,
    status varchar(24) not null default '待确认',
    notes varchar(1000) not null default '',
    booked_at varchar(32) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (container_count > 0),
    check (heavy_count >= 0 and empty_count >= 0 and heavy_count + empty_count <= container_count)
);

create table waterway_vehicles (
    id varchar(36) primary key,
    plate varchar(24) not null,
    trailer varchar(24) not null default '',
    vehicle_type varchar(48) not null default '集装箱牵引车',
    fleet varchar(120) not null default '',
    driver varchar(80) not null default '',
    phone varchar(40) not null default '',
    container_id varchar(36),
    voyage_id varchar(36) references waterway_voyages(id),
    expected_arrival varchar(32) not null default '',
    actual_gate_in varchar(32) not null default '',
    gate_status varchar(24) not null default '待进场',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table waterway_containers (
    id varchar(36) primary key,
    container_no varchar(24) not null unique,
    container_type varchar(48) not null default '冷藏柜',
    container_state varchar(16) not null default '重柜',
    cargo varchar(160) not null default '',
    weight decimal(12, 3) not null default 0,
    bill_of_lading varchar(80) not null default '',
    customer_id varchar(36) references customers(id),
    voyage_id varchar(36) references waterway_voyages(id),
    vehicle_id varchar(36) references waterway_vehicles(id),
    set_temperature decimal(6, 2),
    sensor_id varchar(48) not null default '',
    transport_status varchar(24) not null default '待进场',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (weight >= 0)
);

alter table waterway_containers add constraint fk_waterway_container_vehicle
    foreign key (vehicle_id) references waterway_vehicles(id);

alter table waterway_vehicles add constraint fk_waterway_vehicle_container
    foreign key (container_id) references waterway_containers(id);

create table waterway_loading_operations (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    voyage_id varchar(36) not null references waterway_voyages(id),
    container_id varchar(36) not null references waterway_containers(id),
    vehicle_id varchar(36) references waterway_vehicles(id),
    container_state varchar(16) not null default '重柜',
    started_at varchar(32) not null default '',
    completed_at varchar(32) not null default '',
    operator varchar(80) not null default '',
    status varchar(24) not null default '待吊柜',
    loaded_at varchar(32) not null default '',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (voyage_id, container_id)
);

create index idx_waterway_booking_customer on waterway_bookings(customer_id, planned_date);
create index idx_waterway_booking_voyage on waterway_bookings(voyage_id, status);
create index idx_waterway_voyage_departure on waterway_voyages(planned_departure, status);
create index idx_waterway_container_voyage on waterway_containers(voyage_id, transport_status);
create index idx_waterway_vehicle_arrival on waterway_vehicles(expected_arrival, gate_status);
create index idx_waterway_loading_voyage on waterway_loading_operations(voyage_id, status);

insert into waterway_vessels (id, code, name, vessel_type, design_capacity, available_capacity, captain, phone, status) values
('WV-001', 'WV-001', '云南交投1201', '集装箱船', 15, 10, '李船长', '13887010001', '可安排'),
('WV-002', 'WV-002', '云南交投1202', '集装箱船', 15, 10, '赵船长', '13887010002', '执行航次'),
('WV-003', 'WV-003', '澜湄号', '货船', 15, 9, '周船长', '13887010003', '可安排'),
('WV-004', 'WV-004', '关累号', '集装箱船', 15, 10, '杨船长', '13887010004', '可安排');

insert into waterway_voyages (id, code, voyage_no, vessel_id, direction, origin_port, destination_port, planned_departure, estimated_arrival, available_capacity, status) values
('WVO-050', 'WVO-050', '0050', 'WV-001', '下水', '关累', '清盛', '2026-11-10 16:00', '2026-11-12 08:00', 10, '装船中'),
('WVO-051', 'WVO-051', '0051', 'WV-002', '上水', '清盛', '关累', '2026-11-11 09:00', '2026-11-13 07:00', 9, '订舱中'),
('WVO-052', 'WVO-052', '0052', 'WV-003', '下水', '关累', '清盛', '2026-11-12 14:00', '2026-11-14 08:00', 8, '计划中');

insert into waterway_bookings (id, code, customer_id, direction, planned_date, preferred_vessel_id, voyage_id, container_count, heavy_count, empty_count, cargo_type, estimated_weight, status, booked_at) values
('WB-001', 'WB-2026-001', 'KH-2026-018', '下水', '2026-11-10', 'WV-001', 'WVO-050', 3, 3, 0, '香蕉', 54.500, '已分配航次', '2026-11-08 10:30'),
('WB-002', 'WB-2026-002', 'KH-2026-023', '下水', '2026-11-10', 'WV-001', 'WVO-050', 2, 1, 1, '葡萄', 18.200, '已确认', '2026-11-09 14:20'),
('WB-003', 'WB-2026-003', 'KH-2026-021', '上水', '2026-11-11', 'WV-002', 'WVO-051', 2, 1, 1, '空柜返运', 12.000, '已分配航次', '2026-11-09 15:10'),
('WB-004', 'WB-2026-004', 'KH-2026-015', '下水', '2026-11-12', 'WV-003', 'WVO-052', 3, 3, 0, '冷冻食品', 60.000, '已确认', '2026-11-10 09:00'),
('WB-005', 'WB-2026-005', 'KH-2026-018', '下水', '2026-11-13', null, null, 2, 2, 0, '香蕉', 36.000, '待确认', '2026-11-10 10:15');

insert into waterway_containers (id, container_no, container_type, container_state, cargo, weight, bill_of_lading, customer_id, voyage_id, set_temperature, sensor_id, transport_status) values
('WC-001', 'BTUU8585091', '冷藏柜', '重柜', '香蕉', 18.200, 'BL-2026-0501', 'KH-2026-018', 'WVO-050', 13.00, 'TEMP-W-001', '已装船'),
('WC-002', 'GHIU4002526', '冷藏柜', '重柜', '葡萄', 18.200, 'BL-2026-0502', 'KH-2026-023', 'WVO-050', 0.00, 'TEMP-W-002', '吊柜中'),
('WC-003', 'TGBU7213045', '冷藏柜', '重柜', '香蕉', 18.100, 'BL-2026-0503', 'KH-2026-018', 'WVO-050', 13.00, 'TEMP-W-003', '待吊柜'),
('WC-004', 'MSCU2048190', '普通柜', '空柜', '', 0, '', 'KH-2026-023', 'WVO-050', null, '', '已进场'),
('WC-005', 'CAIU6500721', '冷藏柜', '重柜', '冷冻食品', 20.000, 'BL-2026-0510', 'KH-2026-015', 'WVO-052', -18.00, 'TEMP-W-005', '待进场');

insert into waterway_vehicles (id, plate, trailer, vehicle_type, fleet, driver, phone, container_id, voyage_id, expected_arrival, actual_gate_in, gate_status) values
('WVT-001', '云K·BT1088', '云K·GT908', '集装箱牵引车', '百川接驳车队', '岩温', '13887001001', 'WC-001', 'WVO-050', '2026-11-10 09:00', '2026-11-10 08:48', '已完成接驳'),
('WVT-002', '云K·BT2036', '云K·GT936', '冷藏车', '西双版纳运通', '玉罕', '13887001002', 'WC-002', 'WVO-050', '2026-11-10 12:00', '2026-11-10 11:46', '已进场'),
('WVT-003', '云K·BT3185', '云K·GT951', '集装箱牵引车', '百川接驳车队', '赵明', '13887001003', 'WC-003', 'WVO-050', '2026-11-10 15:00', '', '待进场'),
('WVT-004', '云A·BT4962', '', '集装箱牵引车', '湄公河物流', '周强', '13887001004', 'WC-005', 'WVO-052', '2026-11-12 10:00', '', '待进场');

update waterway_containers set vehicle_id = 'WVT-001' where id = 'WC-001';
update waterway_containers set vehicle_id = 'WVT-002' where id = 'WC-002';
update waterway_containers set vehicle_id = 'WVT-003' where id = 'WC-003';
update waterway_containers set vehicle_id = 'WVT-004' where id = 'WC-005';

insert into waterway_loading_operations (id, code, voyage_id, container_id, vehicle_id, container_state, started_at, completed_at, operator, status, loaded_at) values
('WLO-001', 'WLO-2026-001', 'WVO-050', 'WC-001', 'WVT-001', '重柜', '2026-11-10 10:00', '2026-11-10 10:22', '李海', '已装船', '2026-11-10 10:40'),
('WLO-002', 'WLO-2026-002', 'WVO-050', 'WC-002', 'WVT-002', '重柜', '2026-11-10 12:15', '', '王超', '吊柜中', ''),
('WLO-003', 'WLO-2026-003', 'WVO-050', 'WC-003', 'WVT-003', '重柜', '', '', '李海', '待吊柜', '');
