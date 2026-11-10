-- Complete the equipment management product: location hierarchy, maintenance history,
-- plan approval rules, work-order attachments and meter inspection fields.

alter table assets add column warehouse varchar(120) not null default '';
alter table assets add column building varchar(120) not null default '';
alter table assets add column floor varchar(64) not null default '';
alter table assets add column last_maintenance_date date;
alter table assets add column last_inspection_date date;
update assets set warehouse = site where warehouse = '';

alter table maintenance_plans add column reminder_days integer not null default 30;
alter table maintenance_plans add column maintenance_method varchar(24) not null default 'INTERNAL';
alter table maintenance_plans add column needs_approval boolean not null default false;
alter table maintenance_plans add column estimated_cost decimal(14, 2);
update maintenance_plans set reminder_days = coalesce((select warning_days from assets where assets.id = maintenance_plans.asset_id), 30);

create table maintenance_work_order_attachments (
    id varchar(36) primary key,
    work_order_id varchar(36) not null,
    category varchar(24) not null,
    name varchar(255) not null,
    mime_type varchar(128) not null,
    file_size bigint not null,
    file_base64 text,
    uploaded_by bigint,
    uploaded_at timestamp not null default current_timestamp,
    remark varchar(500) not null default '',
    constraint fk_work_order_attachment foreign key (work_order_id) references maintenance_work_orders(id)
);
create index idx_work_order_attachments_order on maintenance_work_order_attachments(work_order_id, uploaded_at desc);

alter table utility_meters add column area varchar(120) not null default '';
alter table utility_meters add column model varchar(160) not null default '';
alter table utility_meters add column install_date date;
alter table utility_meters add column last_inspection_date date;
alter table utility_meters add column next_inspection_date date;
update utility_meters set area = location where area = '';

alter table utility_meter_readings add column photo_name varchar(255) not null default '';
alter table utility_meter_readings add column photo_mime varchar(128) not null default '';
alter table utility_meter_readings add column photo_base64 text;
