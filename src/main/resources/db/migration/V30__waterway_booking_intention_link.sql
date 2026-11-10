alter table waterway_bookings
    add column intention_id varchar(36) references customer_intentions(id);
