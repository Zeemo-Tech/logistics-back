create table if not exists users (
    id bigint primary key,
    username varchar(32) not null unique,
    password_hash varchar(100) not null,
    nickname varchar(32) not null,
    real_name varchar(32) not null,
    phone varchar(20) not null default '',
    roles varchar(255) not null default 'admin'
);
