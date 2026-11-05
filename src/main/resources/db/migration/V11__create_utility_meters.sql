create table utility_meters (
    id varchar(36) primary key,
    code varchar(40) not null unique,
    asset_id varchar(36) not null,
    name varchar(120) not null,
    kind varchar(16) not null,
    location varchar(160) not null,
    current_reading decimal(16, 3) not null,
    previous_reading decimal(16, 3) not null,
    unit varchar(16) not null,
    read_date date not null,
    next_read_due_date date not null,
    status varchar(24) not null,
    constraint fk_utility_meter_asset foreign key (asset_id) references assets(id)
);

insert into utility_meters (id, code, asset_id, name, kind, location, current_reading,
                            previous_reading, unit, read_date, next_read_due_date, status)
values
    ('MTR-001', 'DL-A-001', 'FAC-BC-008', 'A 区总配电房总电表', '电表', 'A 区总配电房', 82416.000, 79891.000, 'kWh', '2026-11-04', '2026-11-05', 'NORMAL'),
    ('MTR-002', 'DL-B-003', 'FAC-BC-008', 'B 区冷库配电房电表', '电表', 'B 区冷库配电房', 35680.000, 37801.000, 'kWh', '2026-11-04', '2026-11-05', 'NORMAL'),
    ('MTR-003', 'SL-A-002', 'FAC-BC-007', 'A 区公共水表', '水表', 'A 区公共给水主管', 3240.000, 2733.000, 'm³', '2026-11-03', '2026-11-04', 'ALERT'),
    ('MTR-004', 'SL-C-001', 'FAC-BC-007', 'C 区生活水表', '水表', 'C 区生活用水', 1860.000, 1819.000, 'm³', '2026-11-04', '2026-11-05', 'NORMAL'),
    ('MTR-005', 'DL-C-002', 'FAC-BC-008', 'C 区冷库动力电表', '电表', 'C 区冷库配电房', 18250.000, 17341.000, 'kWh', '2026-11-02', '2026-11-04', 'PENDING'),
    ('MTR-006', 'SL-B-004', 'FAC-BC-007', 'B 区公共水表', '水表', 'B 区装卸区', 2890.000, 2764.000, 'm³', '2026-11-04', '2026-11-06', 'NORMAL');
