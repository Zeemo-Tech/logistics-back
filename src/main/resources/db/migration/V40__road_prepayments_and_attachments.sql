-- Road prepayment ledger and document attachments.

create table road_prepayments (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    customer_id varchar(36) not null references customers(id),
    amount decimal(12, 2) not null,
    used_amount decimal(12, 2) not null default 0,
    balance decimal(12, 2) not null,
    paid_at varchar(20) not null default '',
    bank_ref varchar(80) not null default '',
    status varchar(16) not null default '有效',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (amount > 0),
    check (used_amount >= 0 and used_amount <= amount),
    check (balance >= 0)
);

create table road_attachments (
    id varchar(36) primary key,
    entity varchar(32) not null,
    record_id varchar(36) not null,
    doc_type varchar(40) not null default '业务单据',
    file_name varchar(200) not null,
    mime_type varchar(120) not null default 'application/octet-stream',
    file_bytes blob not null,
    uploaded_by varchar(80) not null default '',
    uploaded_at timestamp not null default current_timestamp
);

create index idx_road_prepayments_customer on road_prepayments(customer_id);
create index idx_road_attachments_record on road_attachments(entity, record_id);

insert into road_prepayments (id, code, customer_id, amount, used_amount, balance, paid_at, bank_ref, status, notes) values
('ROAD-PREPAY-001', 'RPP-20261101-001', 'KH-2026-030', 22000, 0, 22000, '2026-11-01', 'PRE-20261101-001', '有效', '四川木头源预付款余额'),
('ROAD-PREPAY-002', 'RPP-20261102-002', 'KH-2026-027', 10000, 0, 10000, '2026-11-02', 'PRE-20261102-002', '有效', '兴华预付款'),
('ROAD-PREPAY-003', 'RPP-20261103-003', 'KH-2026-028', 5000, 5000, 0, '2026-11-03', 'PRE-20261103-003', '已用完', '云南英茂糖业预付款已抵扣');
