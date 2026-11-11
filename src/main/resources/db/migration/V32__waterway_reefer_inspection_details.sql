-- Keep the original unclassified temperature; do not reinterpret it as supply/return air.
alter table waterway_transport_checkpoints add column set_temperature decimal(6, 2);
alter table waterway_transport_checkpoints add column supply_air decimal(6, 2);
alter table waterway_transport_checkpoints add column return_air decimal(6, 2);
alter table waterway_transport_checkpoints add column latitude decimal(9, 6);
alter table waterway_transport_checkpoints add column longitude decimal(9, 6);
alter table waterway_transport_checkpoints add column customer_name varchar(240) not null default '';
alter table waterway_transport_checkpoints add column product varchar(240) not null default '';

update waterway_transport_checkpoints p set customer_name = coalesce((
    select customer.name from waterway_containers c join customers customer on customer.id = c.customer_id
    where c.id = p.container_id), ''),
    product = coalesce((select c.cargo from waterway_containers c where c.id = p.container_id), '');
