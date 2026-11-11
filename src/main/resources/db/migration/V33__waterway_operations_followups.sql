alter table waterway_vessels add column customs_code varchar(64) not null default '';
alter table waterway_vessels add column english_name varchar(160) not null default '';

alter table waterway_loading_operations add column queue_no int;
alter table waterway_loading_operations add column planned_at varchar(32) not null default '';
alter table waterway_loading_operations add column rescheduled_at varchar(32) not null default '';
alter table waterway_loading_operations add column delay_reason varchar(500) not null default '';
alter table waterway_loading_operations add column customer_notified boolean not null default false;

create table waterway_empty_return_plans (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    customer_id varchar(36) references customers(id),
    current_port varchar(80) not null default '',
    source_voyage_id varchar(36) references waterway_voyages(id),
    return_voyage_id varchar(36) not null references waterway_voyages(id),
    planned_date varchar(32) not null default '',
    planned_count int not null,
    status varchar(24) not null default '待补充',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (planned_count > 0)
);

alter table waterway_empty_returns add column plan_id varchar(36) references waterway_empty_return_plans(id);

create table waterway_fees (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    voyage_id varchar(36) not null references waterway_voyages(id),
    container_id varchar(36) references waterway_containers(id),
    category varchar(80) not null,
    amount decimal(14, 2) not null,
    currency varchar(8) not null default 'CNY',
    payer varchar(160) not null default '',
    payee varchar(160) not null default '',
    status varchar(24) not null default '待对账',
    incurred_at varchar(32) not null default '',
    invoice_no varchar(80) not null default '',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (amount >= 0)
);

create index idx_waterway_empty_return_plan_voyage on waterway_empty_return_plans(return_voyage_id, status);
create index idx_waterway_fee_voyage on waterway_fees(voyage_id, status);

create table waterway_voyage_documents (
    id varchar(36) primary key,
    voyage_id varchar(36) not null references waterway_voyages(id),
    document_type varchar(80) not null,
    file_name varchar(255) not null,
    mime_type varchar(160) not null,
    version_no int not null,
    uploaded_by varchar(120) not null default '',
    uploaded_at varchar(32) not null,
    review_status varchar(32) not null default '待审核',
    submit_status varchar(32) not null default '待提交',
    file_bytes blob not null,
    created_at timestamp not null default current_timestamp,
    check (version_no > 0)
);

create index idx_waterway_document_voyage on waterway_voyage_documents(voyage_id, document_type, version_no);
