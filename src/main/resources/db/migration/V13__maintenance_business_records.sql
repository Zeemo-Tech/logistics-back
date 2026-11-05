alter table utility_meters add column multiplier decimal(12, 4) not null default 1.0000;
alter table utility_meters add column owner varchar(80) not null default '';
alter table utility_meters add column lifecycle_status varchar(16) not null default 'ACTIVE';
alter table utility_meters add column version integer not null default 0;
alter table utility_meters add column cycle_days integer not null default 30;
alter table utility_meters add column initial_reading decimal(16,3) not null default 0;
update utility_meters set initial_reading = previous_reading,
    owner = (select custodian from assets where id = utility_meters.asset_id);
create unique index ux_utility_meters_asset on utility_meters(asset_id);

create table maintenance_audit (
    id varchar(36) primary key,
    entity_type varchar(24) not null,
    entity_id varchar(36) not null,
    action varchar(24) not null,
    description varchar(1500) not null,
    operator_id bigint,
    created_at timestamp not null default current_timestamp
);

create table utility_meter_readings (
    id varchar(36) primary key,
    meter_id varchar(36) not null,
    previous_reading decimal(16, 3) not null,
    current_reading decimal(16, 3) not null,
    multiplier decimal(12, 4) not null,
    consumption decimal(18, 3) not null,
    read_date date not null,
    next_read_due_date date not null,
    status varchar(16) not null,
    note varchar(500) not null default '',
    correction_of varchar(36),
    created_at timestamp not null default current_timestamp,
    constraint fk_meter_reading_meter foreign key (meter_id) references utility_meters(id),
    constraint fk_meter_reading_correction foreign key (correction_of) references utility_meter_readings(id)
);
create index idx_meter_readings_meter_date on utility_meter_readings(meter_id, read_date desc);

insert into utility_meter_readings (id, meter_id, previous_reading, current_reading, multiplier,
    consumption, read_date, next_read_due_date, status, note)
select lower(replace(id, 'MTR-', 'READ-')), id, previous_reading, current_reading, 1,
    greatest(current_reading - previous_reading, 0), read_date, next_read_due_date,
    case when status = 'PENDING' then 'NORMAL' else status end, '系统初始化台账读数'
from utility_meters;

create table maintenance_plans (
    id varchar(36) primary key,
    code varchar(40) not null unique,
    name varchar(120) not null,
    asset_id varchar(36) not null,
    plan_type varchar(24) not null,
    frequency varchar(80) not null,
    interval_days integer,
    next_due_date date not null,
    owner varchar(80) not null,
    checklist varchar(1000) not null,
    status varchar(16) not null,
    version integer not null default 0,
    notes varchar(500) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_maintenance_plan_asset foreign key (asset_id) references assets(id)
);
create index idx_maintenance_plans_due on maintenance_plans(status, next_due_date);

create table maintenance_plan_executions (
    id varchar(36) primary key,
    plan_id varchar(36) not null,
    scheduled_date date not null,
    executed_at timestamp not null default current_timestamp,
    executor varchar(80) not null,
    result varchar(16) not null,
    checklist_result varchar(1000) not null,
    notes varchar(1000) not null,
    work_order_id varchar(36),
    constraint fk_plan_execution_plan foreign key (plan_id) references maintenance_plans(id),
    constraint fk_plan_execution_order foreign key (work_order_id) references maintenance_work_orders(id)
);

create table safety_compliance_items (
    id varchar(36) primary key,
    code varchar(40) not null unique,
    name varchar(120) not null,
    item_type varchar(32) not null,
    asset_id varchar(36),
    location varchar(160) not null,
    authority varchar(120) not null,
    owner varchar(80) not null,
    due_date date not null,
    status varchar(16) not null,
    report_number varchar(80),
    last_inspection_date date,
    next_due_date date,
    version integer not null default 0,
    notes varchar(500) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_compliance_asset foreign key (asset_id) references assets(id)
);
create index idx_compliance_due on safety_compliance_items(status, due_date);

create table safety_compliance_events (
    id varchar(36) primary key,
    item_id varchar(36) not null,
    action varchar(24) not null,
    event_date date not null,
    authority varchar(120) not null,
    report_number varchar(80),
    result varchar(24) not null,
    next_due_date date,
    notes varchar(1000) not null,
    created_at timestamp not null default current_timestamp,
    constraint fk_compliance_event_item foreign key (item_id) references safety_compliance_items(id)
);

insert into maintenance_plans (id, code, name, asset_id, plan_type, frequency, interval_days,
    next_due_date, owner, checklist, status, notes)
select 'PLAN-' || lower(replace(a.id, '-', '')), 'MP-' || substring(replace(a.code, '-', ''), 1, 24),
    a.name || '周期维保', a.id, 'PREVENTIVE', coalesce(nullif(a.maintenance_cycle, ''), '按设备维保要求'),
    case when a.maintenance_cycle like '%半月%' then 15 when a.maintenance_cycle like '%季度%' then 90 when a.maintenance_cycle like '%月%' then 30 else 180 end,
    coalesce(a.maintenance_due_date, current_date), a.custodian, '外观与运行状态检查；安全保护装置检查；清洁润滑并记录结果',
    'ACTIVE', '由现有设备维保日期生成的初始计划'
from assets a where a.kind in ('DEVICE', 'FACILITY') and a.lifecycle_status = 'ACTIVE'
    and a.maintenance_due_date is not null;

insert into safety_compliance_items (id, code, name, item_type, asset_id, location, authority,
    owner, due_date, status, notes)
select 'COMP-' || lower(replace(a.id, '-', '')), 'SC-' || substring(replace(a.code, '-', ''), 1, 24),
    a.name || '检验 / 校准', case when a.category in ('地磅秤','温湿度设备','水电设施') then 'CALIBRATION' when a.category = '消防设施' then 'FIRE_SAFETY' else 'STATUTORY_INSPECTION' end, a.id, a.location, coalesce(nullif(a.service_provider,''),'待确认检验机构'),
    a.custodian, a.inspection_due_date, 'ACTIVE', '由现有设备检验日期生成的初始合规事项'
from assets a where a.kind in ('DEVICE', 'FACILITY') and a.lifecycle_status = 'ACTIVE'
    and a.inspection_due_date is not null;

alter table maintenance_work_orders add column deleted_at timestamp;
alter table maintenance_audit add column sequence_id bigint generated always as identity;
alter table utility_meter_readings add column sequence_id bigint generated always as identity;
alter table maintenance_plan_executions add column sequence_id bigint generated always as identity;
alter table safety_compliance_events add column sequence_id bigint generated always as identity;
