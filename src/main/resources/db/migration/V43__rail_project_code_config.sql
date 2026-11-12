-- User-configurable rail box type / project code / price coefficient mappings.

create table rail_project_codes (
    id varchar(36) primary key,
    box_type int not null,
    project_code varchar(32) not null unique,
    price_coeff varchar(16) not null default '',
    status varchar(16) not null default '生效',
    effective_from varchar(20) not null default '',
    notes varchar(1000) not null default '',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (box_type > 0)
);

create index idx_rail_project_codes_box on rail_project_codes(box_type, status);

insert into rail_project_codes (id, box_type, project_code, price_coeff, status, effective_from, notes) values
('RAIL-PROJECT-070', 70, 'KM2601076d', '-35.6%', '生效', '2026-11-02', '70箱项目号'),
('RAIL-PROJECT-060', 60, 'KM2601076c', '-27%', '生效', '2026-11-02', '60箱项目号'),
('RAIL-PROJECT-058E', 58, 'KM2601076e', '34.1%', '生效', '2026-11-02', '58箱默认项目号'),
('RAIL-PROJECT-058F', 58, 'KM2601076f', '-33.5%', '停用', '2026-11-02', '补充合同 f 项目号，待确认适用箱型');
