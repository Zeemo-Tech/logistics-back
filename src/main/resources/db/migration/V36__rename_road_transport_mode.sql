-- Unify the road transport mode label from 货车运输 to 公路运输.

update customer_intentions set business_detail = '公路运输' where business_detail = '货车运输';

update contracts set business_detail = '公路运输' where business_detail = '货车运输';
update contracts set category = '公路运输合同' where category = '货车运输合同';

update contract_categories set name = '公路运输合同' where id = 'CC-012';

update contract_templates
set business_detail = '公路运输',
    title = '公路运输服务合同模板',
    category = '公路运输合同',
    content = replace(content, '货车运输服务合同', '公路运输服务合同')
where id = 'CT-014';
