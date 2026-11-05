create table maintenance_work_orders (
    id varchar(36) primary key,
    code varchar(40) not null unique,
    asset_id varchar(36) not null,
    active_asset_id varchar(36) unique,
    title varchar(120) not null,
    description varchar(1000) not null,
    priority varchar(16) not null,
    status varchar(32) not null,
    assignee varchar(80) not null default '',
    repair_notes varchar(1000) not null default '',
    acceptance_notes varchar(1000) not null default '',
    previous_availability varchar(24) not null,
    resolution_availability varchar(24),
    reporter_id bigint not null,
    version integer not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    completed_at timestamp,
    constraint fk_maintenance_asset foreign key (asset_id) references assets(id),
    constraint fk_maintenance_active_asset foreign key (active_asset_id) references assets(id)
);
create index idx_maintenance_status on maintenance_work_orders(status, created_at);
create index idx_maintenance_asset on maintenance_work_orders(asset_id);

create table maintenance_work_order_events (
    id varchar(36) primary key,
    work_order_id varchar(36) not null,
    action varchar(24) not null,
    description varchar(1500) not null,
    operator_id bigint not null,
    step_version integer not null,
    created_at timestamp not null default current_timestamp,
    constraint fk_maintenance_event_order foreign key (work_order_id) references maintenance_work_orders(id)
);
create index idx_maintenance_events_order on maintenance_work_order_events(work_order_id, created_at);
