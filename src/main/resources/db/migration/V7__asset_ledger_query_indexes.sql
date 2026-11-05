create index idx_assets_kind_code on assets(kind, code, id);
create index idx_assets_linked on assets(linked_asset_id);
