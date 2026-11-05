create table if not exists management_records (
    id varchar(32) primary key,
    record_type varchar(64) not null,
    name varchar(200) not null,
    category varchar(64) not null,
    operator varchar(100) not null,
    region varchar(32) not null,
    status varchar(32) not null,
    metric varchar(32) not null,
    contact varchar(64) not null,
    updated_at varchar(32) not null
);

create index if not exists idx_management_records_type on management_records (record_type);
