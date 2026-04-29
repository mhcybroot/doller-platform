alter table if exists parties
  add column if not exists deleted boolean not null default false,
  add column if not exists deleted_at timestamp,
  add column if not exists deleted_by varchar(255);

alter table if exists trade_deals
  add column if not exists deleted boolean not null default false,
  add column if not exists deleted_at timestamp,
  add column if not exists deleted_by varchar(255);

alter table if exists settlements
  add column if not exists deleted boolean not null default false,
  add column if not exists deleted_at timestamp,
  add column if not exists deleted_by varchar(255);

alter table if exists expenses
  add column if not exists deleted boolean not null default false,
  add column if not exists deleted_at timestamp,
  add column if not exists deleted_by varchar(255);
