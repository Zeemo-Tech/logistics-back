-- Link rail demands to the quote they execute.

alter table rail_demands add column quote_id varchar(36) references rail_quotes(id);
create index idx_rail_demands_quote on rail_demands(quote_id);
