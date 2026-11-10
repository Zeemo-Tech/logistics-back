-- Fee-bearing contracts signed directly with the customer for warehouse pledge services.
insert into contract_categories (id, name, status, sort_order)
select 'CC-008', '仓储费服务', 'ACTIVE', 75
where not exists (select 1 from contract_categories where name = '仓储费服务');
insert into contract_categories (id, name, status, sort_order)
select 'CC-009', '监管费服务', 'ACTIVE', 76
where not exists (select 1 from contract_categories where name = '监管费服务');
insert into contract_templates (id, title, resource_type, category, subject, purpose, status, sort_order)
select 'CT-008', '仓单质押仓储费服务合同', '', '仓储费服务', '质押货物仓储保管及仓储作业', '约定仓单质押货物的仓储费用和服务责任', 'ACTIVE', 75
where not exists (select 1 from contract_templates where id = 'CT-008');
insert into contract_templates (id, title, resource_type, category, subject, purpose, status, sort_order)
select 'CT-009', '仓单质押监管费服务合同', '', '监管费服务', '质押货物监管、盘点、指令核验及放货协同', '约定仓单质押监管服务费用和服务责任', 'ACTIVE', 76
where not exists (select 1 from contract_templates where id = 'CT-009');
