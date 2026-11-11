-- Preserve separate port calls and cold-chain observations for each voyage.
create table waterway_transport_checkpoints (
    id varchar(36) primary key,
    code varchar(48) not null unique,
    voyage_id varchar(36) not null references waterway_voyages(id),
    container_id varchar(36) references waterway_containers(id),
    port_name varchar(160) not null,
    event_type varchar(32) not null default '到港',
    occurred_at varchar(32) not null,
    location varchar(240) not null default '',
    temperature decimal(6, 2),
    status varchar(24) not null default '正常',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_waterway_checkpoint_voyage on waterway_transport_checkpoints(voyage_id, occurred_at);
create index idx_waterway_checkpoint_container on waterway_transport_checkpoints(container_id, occurred_at);
