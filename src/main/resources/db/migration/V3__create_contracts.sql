create table if not exists contracts (
    id varchar(32) primary key,
    title varchar(200) not null,
    category varchar(32) not null,
    counterparty varchar(200) not null,
    resource varchar(100) not null,
    start_date varchar(16) not null,
    end_date varchar(16) not null,
    amount varchar(32) not null,
    owner varchar(32) not null,
    document_count int not null default 0,
    status varchar(16) not null
);

insert into contracts (id, title, category, counterparty, resource, start_date, end_date, amount, owner, document_count, status) values
    ('HT-2026-018', '果蔬冷库年度仓储服务合同',   '仓储服务', '云岭生鲜供应链有限公司',       '百川果蔬冷库',           '2026-01-01', '2026-12-31', '86.40 万元',  '李明', 6,  '履约中'),
    ('HT-2026-017', '综合冷库分区租赁合同',       '冷库租赁', '华南餐饮供应链有限公司',       '百川综合冷库',           '2026-03-01', '2027-02-28', '112.00 万元', '王芳', 4,  '履约中'),
    ('HT-2026-016', '冷链干线运输框架协议',       '运输服务', '西南捷运物流有限公司',         '百川物流智慧冷链中心',   '2026-02-15', '2026-11-30', '168.00 万元', '张伟', 9,  '履约中'),
    ('HT-2026-015', '制冷机组年度维保合同',       '设备维保', '昆明恒温制冷工程有限公司',     '百川果蔬冷库',           '2026-01-20', '2026-09-20', '18.60 万元',  '赵强', 5,  '即将到期'),
    ('HT-2026-014', '托盘及周转箱采购合同',       '采购合同', '滇中物流器具有限公司',         '百川一号常温仓',         '2026-08-20', '2026-10-31', '32.80 万元',  '陈晓', 2,  '待生效'),
    ('HT-2025-042', '园区保洁与秩序维护服务合同', '园区服务', '百安物业服务有限公司',         '百川物流智慧冷链中心',   '2025-07-01', '2026-06-30', '46.20 万元',  '周敏', 12, '已归档');
