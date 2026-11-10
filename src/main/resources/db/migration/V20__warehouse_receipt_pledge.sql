-- Serialize inventory reservations across businesses and receipt lines.
create table pledge_inventory_lock (id integer primary key);
insert into pledge_inventory_lock(id) values (1);
create table pledge_businesses (
 id varchar(36) primary key, code varchar(60) not null unique, title varchar(160) not null,
 borrower varchar(160) not null, owner_name varchar(160) not null, bank_name varchar(160) not null,
 issuer varchar(160) not null, supervisor varchar(160) not null, manager varchar(100) not null,
 agreement_number varchar(100) not null, start_date date not null, end_date date not null,
 rules_text text not null, status varchar(24) not null, version integer not null default 0,
 created_by bigint not null, created_at timestamp not null default current_timestamp,
 updated_at timestamp not null default current_timestamp
);
create table pledge_documents (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 category varchar(30) not null, name varchar(240) not null, mime_type varchar(100) not null,
 file_size bigint not null, file_base64 text not null, uploaded_by bigint not null,
 created_at timestamp not null default current_timestamp
);
create table pledge_lots (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 owner_name varchar(160) not null, warehouse_id varchar(36) not null references assets(id),
 warehouse_name varchar(160) not null, location varchar(160) not null, goods_name varchar(160) not null,
 specification varchar(160) not null, batch_number varchar(100) not null, unit varchar(20) not null,
 quantity decimal(18,3) not null check (quantity >= 0), source_number varchar(100) not null,
 production_date date, expiry_date date, temperature varchar(80) not null default '',
 created_by bigint not null, created_at timestamp not null default current_timestamp,
 constraint ux_pledge_stock_source unique(warehouse_id, owner_name, source_number, batch_number, goods_name, specification)
);
create table pledge_receipts (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 code varchar(60) not null unique, unit varchar(20) not null, quantity decimal(18,3) not null check (quantity > 0),
 status varchar(24) not null, created_by bigint not null, reviewed_by bigint, review_note varchar(1000),
 issued_at timestamp, submitted_at timestamp, pledged_at timestamp,
 submission_document_id varchar(36) references pledge_documents(id),
 created_at timestamp not null default current_timestamp
);
create table pledge_receipt_lines (
 id varchar(36) primary key, receipt_id varchar(36) not null references pledge_receipts(id),
 lot_id varchar(36) not null references pledge_lots(id), quantity decimal(18,3) not null check (quantity > 0),
 released decimal(18,3) not null default 0, outbound decimal(18,3) not null default 0,
 constraint ck_pledge_line_bounds check (outbound >= 0 and released >= outbound and quantity >= released),
 constraint ux_pledge_line unique(receipt_id, lot_id)
);
create table pledge_instructions (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 number varchar(100) not null, instruction_type varchar(24) not null,
 receipt_id varchar(36) references pledge_receipts(id), lot_id varchar(36) references pledge_lots(id),
 goods_name varchar(160) not null default '', specification varchar(160) not null default '', batch_number varchar(100) not null default '',
 warehouse_id varchar(36) references assets(id), quantity decimal(18,3) not null check (quantity > 0),
 executed_quantity decimal(18,3) not null default 0, unit varchar(20) not null,
 valid_until date not null, document_id varchar(36) not null references pledge_documents(id),
 status varchar(24) not null, created_by bigint not null, verified_by bigint, verify_note varchar(1000),
 created_at timestamp not null default current_timestamp,
 constraint ux_pledge_bank_instruction unique(business_id, number),
 constraint ck_pledge_instruction_bounds check (executed_quantity >= 0 and executed_quantity <= quantity)
);
create table pledge_movements (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 receipt_id varchar(36) references pledge_receipts(id), lot_id varchar(36) not null references pledge_lots(id),
 instruction_id varchar(36) references pledge_instructions(id), movement_type varchar(24) not null,
 quantity decimal(18,3) not null, document_id varchar(36) not null references pledge_documents(id),
 note varchar(1000) not null, operator_id bigint not null, operator_name varchar(100) not null,
 created_at timestamp not null default current_timestamp
);
create table pledge_inspections (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 lot_id varchar(36) not null references pledge_lots(id), actual_quantity decimal(18,3) not null,
 book_quantity decimal(18,3) not null, condition_text varchar(1000) not null,
 abnormal boolean not null, status varchar(24) not null, document_id varchar(36) not null references pledge_documents(id),
 created_by bigint not null, resolved_by bigint, resolution varchar(1000),
 created_at timestamp not null default current_timestamp
);
create table pledge_events (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 action varchar(40) not null, target_id varchar(36), description varchar(2000) not null,
 operator_id bigint not null, operator_name varchar(100) not null,
 created_at timestamp not null default current_timestamp
);
create index idx_pledge_business_status on pledge_businesses(status, updated_at);
create index idx_pledge_receipt_business on pledge_receipts(business_id, status);
create index idx_pledge_line_lot on pledge_receipt_lines(lot_id);
create index idx_pledge_instruction_business on pledge_instructions(business_id, status);
create index idx_pledge_lot_owner on pledge_lots(owner_name, warehouse_id);
create index idx_pledge_inspection_lot on pledge_inspections(lot_id, status);
create index idx_pledge_events_business on pledge_events(business_id, created_at);
create table pledge_outbound_orders (
 id varchar(36) primary key, business_id varchar(36) not null references pledge_businesses(id),
 number varchar(100) not null unique, receipt_id varchar(36) not null references pledge_receipts(id),
 lot_id varchar(36) not null references pledge_lots(id), instruction_id varchar(36) not null references pledge_instructions(id),
 quantity decimal(18,3) not null check(quantity>0), status varchar(24) not null,
 document_id varchar(36) not null references pledge_documents(id), created_by bigint not null,
 reviewed_by bigint, completed_by bigint, review_note varchar(1000), completed_at timestamp,
 created_at timestamp not null default current_timestamp
);
create index idx_pledge_outbound_business on pledge_outbound_orders(business_id,status);
