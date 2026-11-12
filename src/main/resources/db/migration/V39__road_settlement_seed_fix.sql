-- Align the demo partial-payment settlement with the invoicing rules.

update road_settlements
set status = '已开票', invoice_no = 'FP-20261110-003', invoice_date = '2026-11-10'
where code = 'RB-20261110-003';
