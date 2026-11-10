alter table waterway_bookings add column released_count int not null default 0;
alter table waterway_voyages add column capacity_reason varchar(500) not null default '';
alter table waterway_containers add column booking_id varchar(36) references waterway_bookings(id);
alter table waterway_containers add column gate_status varchar(24) not null default '待进港';
alter table waterway_containers add column review_status varchar(24) not null default '待审核';
alter table waterway_containers add column review_note varchar(1000) not null default '';

create table waterway_booking_releases (
    id varchar(36) primary key,
    booking_id varchar(36) not null references waterway_bookings(id),
    released_count int not null,
    reason varchar(500) not null,
    released_at varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    check (released_count > 0)
);

create table waterway_empty_returns (
    id varchar(36) primary key,
    container_id varchar(36) not null references waterway_containers(id),
    customer_id varchar(36) references customers(id),
    current_port varchar(80) not null default '',
    source_voyage_id varchar(36) references waterway_voyages(id),
    return_voyage_id varchar(36) references waterway_voyages(id),
    confirmed_at varchar(32) not null default '',
    status varchar(24) not null default '待确认',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (container_id, return_voyage_id)
);

create index idx_waterway_container_booking on waterway_containers(booking_id, gate_status, review_status);
create index idx_waterway_empty_return_voyage on waterway_empty_returns(return_voyage_id, status);

update waterway_containers set gate_status = case
    when transport_status in ('已进场', '待吊柜', '吊柜中', '已装船', '运输中', '已到港', '已交接') then '已确认到港'
    else '待进港' end;
update waterway_containers set review_status = case
    when bill_of_lading <> '' and cargo <> '' then '已审核'
    else '待审核' end;
update waterway_containers set booking_id = case id
    when 'WC-001' then 'WB-001'
    when 'WC-002' then 'WB-002'
    when 'WC-003' then 'WB-001'
    when 'WC-004' then 'WB-002'
    when 'WC-005' then 'WB-004'
    else null end;
