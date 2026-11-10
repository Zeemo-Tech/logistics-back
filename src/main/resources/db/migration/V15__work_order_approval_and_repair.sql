-- Work-order approval workflow, external repair, parts and cost records.

alter table maintenance_work_orders add column approval_id varchar(36);
alter table maintenance_work_orders add column approval_status varchar(24) not null default 'NONE';
alter table maintenance_work_orders add column fault_type varchar(32) not null default '';
alter table maintenance_work_orders add column fault_location varchar(160) not null default '';
alter table maintenance_work_orders add column impact_operations boolean not null default false;
alter table maintenance_work_orders add column reporter_name varchar(80) not null default '';
alter table maintenance_work_orders add column reporter_department_id varchar(36);
alter table maintenance_work_orders add column emergency boolean not null default false;
alter table maintenance_work_orders add column external_repair boolean not null default false;
alter table maintenance_work_orders add column vendor_type varchar(32) not null default '';
alter table maintenance_work_orders add column vendor_name varchar(160) not null default '';
alter table maintenance_work_orders add column vendor_contact varchar(80) not null default '';
alter table maintenance_work_orders add column vendor_phone varchar(32) not null default '';
alter table maintenance_work_orders add column technician varchar(80) not null default '';
alter table maintenance_work_orders add column specialty varchar(64) not null default '';
alter table maintenance_work_orders add column qualification varchar(160) not null default '';
alter table maintenance_work_orders add column repair_plan varchar(1000) not null default '';
alter table maintenance_work_orders add column expected_arrival timestamp;
alter table maintenance_work_orders add column expected_completion timestamp;
alter table maintenance_work_orders add column quoted_amount decimal(14, 2);
alter table maintenance_work_orders add column labor_cost decimal(14, 2);
alter table maintenance_work_orders add column parts_cost decimal(14, 2);
alter table maintenance_work_orders add column total_cost decimal(14, 2);
alter table maintenance_work_orders add column downtime_minutes integer;
alter table maintenance_work_orders add column paused_reason varchar(500) not null default '';
alter table maintenance_work_orders add column secondary_of varchar(36);
create index idx_work_orders_approval on maintenance_work_orders(approval_status, status);
create index idx_work_orders_completed on maintenance_work_orders(completed_at);

create table maintenance_work_order_parts (
    id varchar(36) primary key,
    work_order_id varchar(36) not null,
    name varchar(120) not null,
    spec varchar(160) not null default '',
    quantity decimal(12, 2) not null default 1,
    unit varchar(24) not null default '件',
    unit_price decimal(14, 2) not null default 0,
    amount decimal(14, 2) not null default 0,
    supplier varchar(160) not null default '',
    replaced_part_code varchar(80) not null default '',
    remark varchar(500) not null default '',
    created_at timestamp not null default current_timestamp,
    constraint fk_work_order_part_order foreign key (work_order_id) references maintenance_work_orders(id)
);
create index idx_work_order_parts_order on maintenance_work_order_parts(work_order_id);

create table maintenance_approvals (
    id varchar(36) primary key,
    code varchar(40) not null unique,
    business_type varchar(32) not null,
    business_id varchar(36),
    title varchar(160) not null,
    applicant_id bigint,
    applicant_name varchar(80) not null default '',
    department_id varchar(36),
    asset_id varchar(36),
    reason varchar(1500) not null,
    repair_plan varchar(1500) not null default '',
    estimated_cost decimal(14, 2),
    needs_shutdown boolean not null default false,
    needs_outsourcing boolean not null default false,
    emergency boolean not null default false,
    status varchar(24) not null default 'PENDING',
    approver_id bigint,
    approver_name varchar(80) not null default '',
    decision_note varchar(1000) not null default '',
    version integer not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    decided_at timestamp,
    constraint fk_approval_asset foreign key (asset_id) references assets(id),
    constraint fk_approval_department foreign key (department_id) references departments(id)
);
create index idx_approvals_status on maintenance_approvals(status, created_at desc);
create index idx_approvals_business on maintenance_approvals(business_type, business_id);

create table maintenance_approval_records (
    id varchar(36) primary key,
    approval_id varchar(36) not null,
    action varchar(24) not null,
    operator_id bigint,
    operator_name varchar(80) not null default '',
    note varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    constraint fk_approval_record foreign key (approval_id) references maintenance_approvals(id)
);
create index idx_approval_records_approval on maintenance_approval_records(approval_id, created_at);
