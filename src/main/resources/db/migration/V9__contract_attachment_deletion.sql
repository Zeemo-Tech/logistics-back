-- Keep attachment bytes for traceability while removing deleted files from all read endpoints.
alter table contract_attachments add column deleted_at timestamp;
create index idx_contract_attachments_active on contract_attachments(deleted_at, uploaded_at);
alter table contract_attachments add column remark varchar(1000) default '' not null;
