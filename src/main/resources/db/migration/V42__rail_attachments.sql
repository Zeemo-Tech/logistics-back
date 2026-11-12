-- Rail transport document attachments.

create table rail_attachments (
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

create index idx_rail_attachments_record on rail_attachments(entity, record_id);
