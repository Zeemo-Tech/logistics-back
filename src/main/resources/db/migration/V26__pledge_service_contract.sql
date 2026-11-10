-- Distinguish the customer's fee-bearing supervision service contract from the bank's external tripartite agreement.
insert into contract_categories (id, name, status, sort_order)
select 'CC-010', '监管服务', 'ACTIVE', 77
where not exists (select 1 from contract_categories where name = '监管服务');

-- Older deployments may have applied the competing V25 migration first.
insert into contract_categories (id, name, status, sort_order)
select 'CC-011', '仓储费服务', 'ACTIVE', 78
where not exists (select 1 from contract_categories where name = '仓储费服务');
insert into contract_categories (id, name, status, sort_order)
select 'CC-012', '监管费服务', 'ACTIVE', 79
where not exists (select 1 from contract_categories where name = '监管费服务');
insert into contract_templates (id, title, resource_type, category, subject, purpose, status, sort_order)
select 'CT-010', '仓单质押仓储费服务合同', '', '仓储费服务', '质押货物仓储保管及仓储作业', '约定仓单质押货物的仓储费用和服务责任', 'ACTIVE', 78
where not exists (select 1 from contract_templates where title = '仓单质押仓储费服务合同');
insert into contract_templates (id, title, resource_type, category, subject, purpose, status, sort_order)
select 'CT-011', '仓单质押监管费服务合同', '', '监管费服务', '质押货物监管、盘点、指令核验及放货协同', '约定仓单质押监管服务费用和服务责任', 'ACTIVE', 79
where not exists (select 1 from contract_templates where title = '仓单质押监管费服务合同');
