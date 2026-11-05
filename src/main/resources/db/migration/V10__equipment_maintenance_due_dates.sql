alter table assets add maintenance_due_date date;
alter table assets add inspection_due_date date;

update assets set maintenance_due_date = '2026-11-05', inspection_due_date = '2027-02-15' where id = 'CS-BC-001';
update assets set maintenance_due_date = '2026-11-08', inspection_due_date = '2027-01-20' where id = 'CS-BC-002';
update assets set maintenance_due_date = '2026-11-02', inspection_due_date = '2026-11-02' where id = 'FAC-BC-001';

insert into assets (id, code, kind, name, category, ownership, owner_name, site, location, custodian,
                   lifecycle_status, availability_status, maintenance_due_date, inspection_due_date, notes)
values
    ('FAC-BC-002', 'FAC-BC-002', 'FACILITY', '综合服务楼客梯 E-02', '电梯', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '综合服务楼 1 号厅', '设备组', 'ACTIVE', 'AVAILABLE', '2026-11-09', '2026-11-09', '年度检验临近，需提前 5 天联系维保单位'),
    ('FAC-BC-003', 'FAC-BC-003', 'FACILITY', '北门汽车衡 S-01', '地磅秤', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区北门入口', '设备组', 'ACTIVE', 'IN_USE', '2026-11-02', '2026-11-25', '计量检定逾期，已安排外协复核'),
    ('FAC-BC-004', 'FAC-BC-004', 'FACILITY', '1 号冷库制冷机组 C-03', '冷库设备', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区东区 A 区机房', '赵强', 'ACTIVE', 'IN_USE', '2026-11-05', '2027-03-12', '月度维保，重点检查油过滤器和冷凝压力'),
    ('FAC-BC-005', 'FAC-BC-005', 'FACILITY', '装卸区货梯 F-01', '货梯', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', 'A 区装卸月台', '李宁', 'ACTIVE', 'AVAILABLE', '2026-11-06', '2027-01-18', '每日运行巡检，月度保养'),
    ('FAC-BC-006', 'FAC-BC-006', 'FACILITY', '消防泵房主泵组', '消防设施', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', '园区西区消防泵房', '周敏', 'ACTIVE', 'AVAILABLE', '2026-11-18', '2027-04-30', '月度巡检，检查压力表、阀门和联动控制'),
    ('FAC-BC-007', 'FAC-BC-007', 'FACILITY', 'A 区公共给水主管', '水电设施', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', 'A 区装卸月台东侧', '工程组', 'ACTIVE', 'IN_USE', '2026-11-05', null, '公共区域水管巡查及报修对象'),
    ('FAC-BC-008', 'FAC-BC-008', 'FACILITY', 'B 区配电房低压柜', '水电设施', 'OWNED', '百川物流智慧冷链中心', '百川物流智慧冷链中心', 'B 区冷库配电房', '工程组', 'ACTIVE', 'IN_USE', '2026-11-12', '2027-05-12', '月度红外测温和季度预防性试验');
