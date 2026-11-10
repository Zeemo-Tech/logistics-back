-- Distinguish the customer's fee-bearing supervision service contract from the bank's external tripartite agreement.
insert into contract_categories (id, name, status, sort_order)
select 'CC-008', '监管服务', 'ACTIVE', 75
where not exists (select 1 from contract_categories where name = '监管服务');
