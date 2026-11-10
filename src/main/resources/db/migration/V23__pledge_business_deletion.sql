alter table pledge_businesses add column deleted_at timestamp;
alter table pledge_businesses add column deleted_by bigint;
create index idx_pledge_business_visible on pledge_businesses(deleted_at,updated_at);
