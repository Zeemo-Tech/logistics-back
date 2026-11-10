-- Equipment lifecycle, organization structure and three-level category dictionary.

create table departments (
    id varchar(36) primary key,
    parent_id varchar(36),
    code varchar(40) not null unique,
    name varchar(120) not null,
    type varchar(24) not null,
    leader varchar(80) not null default '',
    phone varchar(32) not null default '',
    sort_order integer not null default 0,
    status varchar(16) not null default 'ACTIVE',
    notes varchar(500) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_department_parent foreign key (parent_id) references departments(id)
);
create index idx_departments_parent on departments(parent_id, sort_order);

insert into departments (id, parent_id, code, name, type, leader, phone, sort_order) values
    ('DEPT-COMPANY', null, 'BC', '百川物流智慧冷链中心', 'COMPANY', '总经理', '0871-6600100', 1),
    ('DEPT-PARK-OPS', 'DEPT-COMPANY', 'YYZX', '园区运营中心', 'CENTER', '张敏', '0871-6600110', 10),
    ('DEPT-PARK-MGMT', 'DEPT-COMPANY', 'GLZX', '园区管理中心', 'CENTER', '李强', '0871-6600120', 20),
    ('DEPT-WH-OPS', 'DEPT-COMPANY', 'CCZX', '仓储运营中心', 'CENTER', '王芳', '0871-6600130', 30),
    ('DEPT-TRANSPORT', 'DEPT-COMPANY', 'YSZX', '运输调度中心', 'CENTER', '赵强', '0871-6600140', 40),
    ('DEPT-CUSTOMER', 'DEPT-PARK-OPS', 'KFZX', '客户服务部', 'DEPARTMENT', '刘洋', '0871-6600111', 11),
    ('DEPT-EQUIPMENT', 'DEPT-PARK-MGMT', 'SBGL', '设备管理部', 'DEPARTMENT', '陈晓', '0871-6600121', 21),
    ('DEPT-SAFETY', 'DEPT-PARK-MGMT', 'AQHB', '安全环保部', 'DEPARTMENT', '周敏', '0871-6600122', 22),
    ('DEPT-ENGINEERING', 'DEPT-PARK-MGMT', 'GCWX', '工程维修组', 'TEAM', '王建国', '0871-6600123', 23),
    ('DEPT-FIRE', 'DEPT-SAFETY', 'XFZS', '消防值守组', 'TEAM', '李宁', '0871-6600124', 24),
    ('DEPT-WH-A', 'DEPT-WH-OPS', 'CKA', 'A 区仓储组', 'TEAM', '刘海波', '0871-6600131', 31),
    ('DEPT-WH-B', 'DEPT-WH-OPS', 'CKB', 'B 区仓储组', 'TEAM', '孙丽', '0871-6600132', 32),
    ('DEPT-TRANSPORT-TEAM', 'DEPT-TRANSPORT', 'YSDD', '运输调度组', 'TEAM', '赵强', '0871-6600141', 41);

create table equipment_categories (
    id varchar(36) primary key,
    parent_id varchar(36),
    level integer not null,
    code varchar(40) not null unique,
    name varchar(120) not null,
    sort_order integer not null default 0,
    status varchar(16) not null default 'ACTIVE',
    constraint fk_equipment_category_parent foreign key (parent_id) references equipment_categories(id)
);
create index idx_equipment_categories_parent on equipment_categories(parent_id, sort_order);

insert into equipment_categories (id, parent_id, level, code, name, sort_order) values
    ('CAT-ELEVATOR', null, 1, 'ELEVATOR', '电梯', 10),
    ('CAT-ELEVATOR-PASSENGER', 'CAT-ELEVATOR', 2, 'ELEVATOR_PASSENGER', '客梯', 11),
    ('CAT-ELEVATOR-FREIGHT', 'CAT-ELEVATOR', 2, 'ELEVATOR_FREIGHT', '货梯', 12),
    ('CAT-WEIGHBRIDGE', null, 1, 'WEIGHBRIDGE', '地磅秤', 20),
    ('CAT-WEIGHBRIDGE-TRUCK', 'CAT-WEIGHBRIDGE', 2, 'WEIGHBRIDGE_TRUCK', '汽车衡', 21),
    ('CAT-WEIGHBRIDGE-ELECTRONIC', 'CAT-WEIGHBRIDGE', 2, 'WEIGHBRIDGE_ELECTRONIC', '电子地磅', 22),
    ('CAT-COLD-EQUIPMENT', null, 1, 'COLD_EQUIPMENT', '冷库设备', 30),
    ('CAT-COLD-UNIT', 'CAT-COLD-EQUIPMENT', 2, 'COLD_UNIT', '制冷机组', 31),
    ('CAT-COLD-FAN', 'CAT-COLD-EQUIPMENT', 2, 'COLD_FAN', '冷风机', 32),
    ('CAT-COLD-COMPRESSOR', 'CAT-COLD-EQUIPMENT', 2, 'COLD_COMPRESSOR', '压缩机', 33),
    ('CAT-COLD-CONDENSER', 'CAT-COLD-EQUIPMENT', 2, 'COLD_CONDENSER', '冷凝器', 34),
    ('CAT-COLD-EVAPORATOR', 'CAT-COLD-EQUIPMENT', 2, 'COLD_EVAPORATOR', '蒸发器', 35),
    ('CAT-FIRE', null, 1, 'FIRE_FACILITY', '消防设施', 40),
    ('CAT-FIRE-PUMP', 'CAT-FIRE', 2, 'FIRE_PUMP', '消防泵', 41),
    ('CAT-FIRE-EXTINGUISHER', 'CAT-FIRE', 2, 'FIRE_EXTINGUISHER', '灭火器', 42),
    ('CAT-FIRE-SPRINKLER', 'CAT-FIRE', 2, 'FIRE_SPRINKLER', '喷淋系统', 43),
    ('CAT-UTILITY', null, 1, 'UTILITY_FACILITY', '水电设施', 50),
    ('CAT-UTILITY-CABINET', 'CAT-UTILITY', 2, 'UTILITY_CABINET', '配电柜', 51),
    ('CAT-UTILITY-PUMP', 'CAT-UTILITY', 2, 'UTILITY_PUMP', '水泵', 52),
    ('CAT-UTILITY-LINE', 'CAT-UTILITY', 2, 'UTILITY_LINE', '线路', 53),
    ('CAT-PUBLIC', null, 1, 'PUBLIC_FACILITY', '公共设施', 60),
    ('CAT-PUBLIC-LIGHT', 'CAT-PUBLIC', 2, 'PUBLIC_LIGHT', '路灯', 61),
    ('CAT-PUBLIC-FENCE', 'CAT-PUBLIC', 2, 'PUBLIC_FENCE', '护栏', 62),
    ('CAT-PUBLIC-DRAIN', 'CAT-PUBLIC', 2, 'PUBLIC_DRAIN', '排水设施', 63),
    ('CAT-LOADING', null, 1, 'LOADING_FACILITY', '装卸设施', 70),
    ('CAT-LOADING-PLATFORM', 'CAT-LOADING', 2, 'LOADING_PLATFORM', '装卸平台', 71),
    ('CAT-LOADING-LIFT', 'CAT-LOADING', 2, 'LOADING_LIFT', '升降平台', 72),
    ('CAT-LOADING-CONVEYOR', 'CAT-LOADING', 2, 'LOADING_CONVEYOR', '输送设备', 73);

alter table assets add column department_id varchar(36);
alter table assets add column category_id varchar(36);
alter table assets add column purchase_date date;
alter table assets add column install_date date;
alter table assets add column service_life_years integer;
alter table assets add column expected_retirement_date date;
alter table assets add column maintenance_contract_expiry date;
alter table assets add column run_status varchar(24) not null default 'RUNNING';
alter table assets add column warning_days integer not null default 30;
alter table assets add column critical_warning_days integer not null default 7;
alter table assets add column responsible_person varchar(80) not null default '';
alter table assets add column maintenance_contact varchar(80) not null default '';
alter table assets add column maintenance_phone varchar(32) not null default '';
alter table assets add constraint fk_asset_department foreign key (department_id) references departments(id);
alter table assets add constraint fk_asset_category foreign key (category_id) references equipment_categories(id);
create index idx_assets_department on assets(department_id, kind);
create index idx_assets_category on assets(category_id, kind);

update assets set category_id = (
    select c.id from equipment_categories c
    where c.level = 2 and c.name = assets.subcategory
    limit 1
);
update assets set category_id = (
    select c.id from equipment_categories c
    where c.level = 1 and c.name = assets.category
    limit 1
) where category_id is null;

update assets set department_id = case
    when category in ('电梯', '地磅秤', '冷库设备', '装卸设施') then 'DEPT-EQUIPMENT'
    when category = '消防设施' then 'DEPT-SAFETY'
    when category in ('水电设施', '公共设施') then 'DEPT-ENGINEERING'
    when kind = 'VEHICLE' then 'DEPT-TRANSPORT'
    when kind in ('WAREHOUSE', 'COLD_STORAGE') then 'DEPT-WH-OPS'
    when kind = 'MOBILE_CABINET' then 'DEPT-TRANSPORT-TEAM'
    else 'DEPT-PARK-OPS'
end;

update assets set run_status = case
    when lifecycle_status = 'RETIRED' then 'RETIRED'
    when lifecycle_status = 'INACTIVE' then 'SUSPENDED'
    when availability_status = 'MAINTENANCE' then 'REPAIRING'
    when availability_status = 'UNAVAILABLE' then 'FAULT'
    when condition_status in ('OFFLINE', 'ALERT') then 'ABNORMAL'
    else 'RUNNING'
end;
update assets set responsible_person = custodian where responsible_person = '';
update assets set maintenance_contact = coalesce(nullif(service_provider, ''), custodian),
    maintenance_phone = '' where maintenance_contact = '';

create table equipment_attachments (
    id varchar(36) primary key,
    asset_id varchar(36) not null,
    category varchar(32) not null,
    name varchar(255) not null,
    mime_type varchar(128) not null,
    file_size bigint not null,
    file_base64 text,
    uploaded_by bigint,
    uploaded_at timestamp not null default current_timestamp,
    remark varchar(500) not null default '',
    constraint fk_equipment_attachment_asset foreign key (asset_id) references assets(id)
);
create index idx_equipment_attachment_asset on equipment_attachments(asset_id, uploaded_at desc);
