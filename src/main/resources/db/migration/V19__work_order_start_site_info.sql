alter table maintenance_work_orders add column actual_arrival timestamp;
alter table maintenance_work_orders add column site_technician varchar(80) not null default '';
alter table maintenance_work_orders add column site_phone varchar(32) not null default '';
alter table maintenance_work_orders add column site_notes varchar(1000) not null default '';
