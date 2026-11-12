-- Link rail demands to existing customers with rail transport intentions.

alter table rail_demands add column customer_id varchar(36) references customers(id);

update rail_demands set customer_id = 'KH-2026-027' where customer_name = '兴华';
update rail_demands set customer_id = 'KH-2026-034' where customer_name = '吴总';
