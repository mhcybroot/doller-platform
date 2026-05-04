create table if not exists companies (
  id bigserial primary key,
  name varchar(255) not null,
  notes varchar(255),
  deleted boolean not null default false,
  deleted_at timestamp,
  deleted_by varchar(255)
);

create table if not exists owner_custom_entries (
  id bigserial primary key,
  company_id bigint not null references companies(id),
  entry_type varchar(32) not null,
  amount_bdt numeric(19,2) not null check (amount_bdt > 0),
  entry_time timestamp not null,
  item_purpose varchar(255) not null,
  notes varchar(255),
  deleted boolean not null default false,
  deleted_at timestamp,
  deleted_by varchar(255)
);

create index if not exists idx_owner_custom_entries_company_time_deleted
  on owner_custom_entries(company_id, entry_time, deleted);

