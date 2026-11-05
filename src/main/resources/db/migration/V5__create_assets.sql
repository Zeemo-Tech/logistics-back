create table assets (
    id varchar(36) primary key,
    code varchar(40) not null unique,
    kind varchar(32) not null,
    name varchar(120) not null,
    category varchar(64) not null,
    ownership varchar(24) not null,
    owner_name varchar(120) not null,
    site varchar(120) not null,
    location varchar(160) not null,
    custodian varchar(80) not null,
    lifecycle_status varchar(24) not null,
    availability_status varchar(24) not null,
    condition_status varchar(24),
    linked_asset_id varchar(36),
    capacity_total decimal(14, 2),
    capacity_used decimal(14, 2),
    capacity_unit varchar(16),
    temperature_min decimal(7, 2),
    temperature_max decimal(7, 2),
    battery_percent decimal(5, 2),
    rated_load_tons decimal(10, 2),
    insurance_expiry date,
    last_seen varchar(32),
    notes varchar(500) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_assets_linked_asset foreign key (linked_asset_id) references assets(id)
);

create index idx_assets_kind on assets(kind);
create index idx_assets_status on assets(lifecycle_status, availability_status);

create table asset_events (
    id varchar(36) primary key,
    asset_id varchar(36) not null,
    event_type varchar(24) not null,
    description varchar(500) not null,
    operator_id bigint,
    created_at timestamp not null default current_timestamp,
    constraint fk_asset_events_asset foreign key (asset_id) references assets(id)
);

create index idx_asset_events_asset on asset_events(asset_id, created_at);

insert into assets (id, code, kind, name, category, ownership, owner_name, site, location, custodian, lifecycle_status, availability_status, capacity_total, capacity_used, capacity_unit, temperature_min, temperature_max) values
    ('WH-BC-001', 'WH-BC-001', 'WAREHOUSE', '百川一号常温仓', '常温仓', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区东区', '陈晓', 'ACTIVE', 'IN_USE', 8600, 6708, 'm²', null, null),
    ('WH-BC-002', 'WH-BC-002', 'WAREHOUSE', '百川二号常温仓', '常温仓', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区西区', '李明', 'ACTIVE', 'IN_USE', 6200, 3968, 'm²', null, null),
    ('CS-BC-001', 'CS-BC-001', 'COLD_STORAGE', '百川果蔬冷库', '果蔬冷库', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区东区 A 区', '陈晓', 'ACTIVE', 'IN_USE', 3200, 2496, '吨', 0, 8),
    ('CS-BC-002', 'CS-BC-002', 'COLD_STORAGE', '百川综合冷库', '综合冷库', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区西区 B 区', '李明', 'ACTIVE', 'IN_USE', 2400, 1536, '吨', -18, 4);

insert into assets (id, code, kind, name, category, ownership, owner_name, site, location, custodian, lifecycle_status, availability_status, capacity_total, capacity_unit, rated_load_tons, insurance_expiry) values
    ('VEH-001', 'VEH-001', 'VEHICLE', '云K·CL618', '冷藏运输车', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '运输线路途中', '李国强', 'ACTIVE', 'IN_USE', 20, '吨', 20, '2027-06-30'),
    ('VEH-002', 'VEH-002', 'VEHICLE', '云K·CL527', '冷藏运输车', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '运输线路途中', '岩温罕', 'ACTIVE', 'IN_USE', 18, '吨', 18, '2027-04-30'),
    ('VEH-003', 'VEH-003', 'VEHICLE', '云K·ML369', '普通厢式货车', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '运输线路途中', '张建军', 'ACTIVE', 'IN_USE', 12, '吨', 12, '2027-08-31'),
    ('VEH-004', 'VEH-004', 'VEHICLE', '云K·BC808', '冷藏运输车', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区停车区', '设备组', 'ACTIVE', 'AVAILABLE', 16, '吨', 16, '2027-09-30');

insert into assets (id, code, kind, name, category, ownership, owner_name, site, location, custodian, lifecycle_status, availability_status, linked_asset_id, temperature_min, temperature_max, battery_percent) values
    ('MC-001', 'MC-001', 'MOBILE_CABINET', '百川1号移动冷柜', '冷冻柜', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '运输线路途中', '设备组', 'ACTIVE', 'IN_USE', 'VEH-001', -22, -18, 86),
    ('MC-002', 'MC-002', 'MOBILE_CABINET', '百川2号移动冷柜', '冷藏柜', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '运输线路途中', '设备组', 'ACTIVE', 'IN_USE', 'VEH-002', 2, 8, 72),
    ('MC-003', 'MC-003', 'MOBILE_CABINET', '百川3号移动冷柜', '多温区冷柜', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '运输线路途中', '设备组', 'ACTIVE', 'IN_USE', 'VEH-003', -18, 8, 58),
    ('MC-004', 'MC-004', 'MOBILE_CABINET', '百川4号移动冷柜', '冷冻柜', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区充电区', '设备组', 'ACTIVE', 'AVAILABLE', null, -22, -18, 100);

insert into assets (id, code, kind, name, category, ownership, owner_name, site, location, custodian, lifecycle_status, availability_status, condition_status, linked_asset_id, last_seen) values
    ('TH-88421', 'TH-88421', 'DEVICE', '库内温湿度探头 01', '温湿度设备', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '果蔬冷库 A 区', '设备组', 'ACTIVE', 'IN_USE', 'ONLINE', 'CS-BC-001', '2026-08-16 10:24'),
    ('TH-88391', 'TH-88391', 'DEVICE', '库外环境探头', '温湿度设备', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '综合冷库东侧', '设备组', 'ACTIVE', 'IN_USE', 'ALERT', 'CS-BC-002', '2026-08-16 09:58'),
    ('CAM-3318', 'CAM-3318', 'DEVICE', '一号库东门摄像头', '视频监控设备', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '东门装卸区', '设备组', 'ACTIVE', 'IN_USE', 'ONLINE', 'CS-BC-001', '2026-08-16 10:20'),
    ('GPS-BC-VEH-001', 'GPS-BC-VEH-001', 'DEVICE', '云K·CL618 GPS终端', 'GPS定位设备', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '车辆驾驶室', '设备组', 'ACTIVE', 'IN_USE', 'ONLINE', 'VEH-001', '2026-08-16 10:18'),
    ('DOOR-2088', 'DOOR-2088', 'DEVICE', '一号库东门门磁', '门磁设备', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '果蔬冷库东门', '设备组', 'ACTIVE', 'IN_USE', 'ONLINE', 'CS-BC-001', '2026-08-16 09:51');

insert into assets (id, code, kind, name, category, ownership, owner_name, site, location, custodian, lifecycle_status, availability_status) values
    ('FAC-BC-001', 'FAC-BC-001', 'FACILITY', '园区综合服务中心', '配套设施', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区东区', '园区运营部', 'ACTIVE', 'AVAILABLE');
