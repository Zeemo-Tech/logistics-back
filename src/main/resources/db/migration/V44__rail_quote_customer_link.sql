-- Link rail quotes to existing customers with rail transport intentions.

alter table rail_quotes add column customer_id varchar(36) references customers(id);

insert into customers (id, code, name, customer_nature, credit_code, legal_person, contact_person, contact_phone, address, bank_name, bank_account, type, service, source, owner, stage, status, notes)
select 'KH-2026-034', 'KH-2026-034', '吴总', '个人', '', '', '吴总', '', '勐腊县', '', '', '运输客户', '木材火车运输', '业务合作', '小九', '合作中', '已签约', '火车运输客户'
where not exists (select 1 from customers where id = 'KH-2026-034');

insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source, notes)
select 'CI-034-01', 'KH-2026-034', '运输服务', '火车运输', 17400, '合作中', '小九', '业务合作', '勐腊发元瑞木材火车运输'
where not exists (select 1 from customer_intentions where id = 'CI-034-01');

update rail_quotes set customer_id = 'KH-2026-027' where customer_name = '兴华';
update rail_quotes set customer_id = 'KH-2026-034' where customer_name = '吴总';
