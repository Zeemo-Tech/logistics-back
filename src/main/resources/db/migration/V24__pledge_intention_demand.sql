alter table customer_intentions add column pledge_demand text;
alter table pledge_businesses add column source_intention_id varchar(36);
alter table pledge_businesses add column intention_snapshot text;
alter table pledge_businesses add constraint fk_pledge_source_intention
    foreign key (source_intention_id) references customer_intentions(id);
create index idx_pledge_source_intention on pledge_businesses(source_intention_id, deleted_at);
