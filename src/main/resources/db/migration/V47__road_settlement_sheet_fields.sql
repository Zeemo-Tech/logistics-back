-- Fields required for the printable transport service settlement sheet.

alter table road_settlements add column delivery_id varchar(36) references road_deliveries(id);
alter table road_settlements add column service_mode varchar(24) not null default '门到门';
alter table road_settlements add column factory varchar(160) not null default '';
alter table road_settlements add column origin varchar(80) not null default '勐腊';
alter table road_settlements add column destination varchar(80) not null default '';
alter table road_settlements add column consignee_name varchar(80) not null default '';
alter table road_settlements add column wagon_no varchar(32) not null default '';
alter table road_settlements add column cargo varchar(80) not null default '胶合板';

update road_settlements set delivery_id = 'ROAD-DELIVERY-003', service_mode = '门到门',
    factory = '瑞祥 / 海发', origin = '勐腊', destination = '元瑞', consignee_name = '胡总',
    wagon_no = '3800537', cargo = '胶合板'
where code = 'RB-20261110-001';

update road_settlements set delivery_id = 'ROAD-DELIVERY-002', service_mode = '门到门',
    factory = '顺达', origin = '勐腊', destination = '元瑞', consignee_name = '胡泽东',
    wagon_no = '3805609', cargo = '胶合板'
where code = 'RB-20261110-002';

update road_settlements set delivery_id = 'ROAD-DELIVERY-001', service_mode = '门到门',
    factory = '顺达', origin = '勐腊', destination = '元瑞', consignee_name = '彭清勇',
    wagon_no = '3465352', cargo = '胶合板'
where code = 'RB-20261110-003';
