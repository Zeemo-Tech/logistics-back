-- Preventive maintenance workflow: due detection -> task confirmation -> approval -> work order -> archive.

create table maintenance_plan_tasks (
    id varchar(36) primary key,
    plan_id varchar(36) not null,
    asset_id varchar(36) not null,
    scheduled_date date not null,
    title varchar(160) not null,
    content varchar(1000) not null default '',
    planned_date date,
    estimated_cost decimal(14, 2),
    status varchar(32) not null default 'PENDING_CONFIRMATION',
    approval_id varchar(36),
    work_order_id varchar(36),
    confirmed_by bigint,
    confirmed_at timestamp,
    version integer not null default 0,
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_plan_task_plan foreign key (plan_id) references maintenance_plans(id),
    constraint fk_plan_task_asset foreign key (asset_id) references assets(id)
);
create unique index ux_plan_task_plan_date on maintenance_plan_tasks(plan_id, scheduled_date);
create index idx_plan_task_status on maintenance_plan_tasks(status, scheduled_date);
create index idx_plan_task_work_order on maintenance_plan_tasks(work_order_id);
