create table if not exists user_accounts (
  id bigserial primary key,
  username varchar(255) not null unique,
  password_hash varchar(255) not null,
  role varchar(32) not null,
  active boolean not null,
  must_change_password boolean not null default true
);

create table if not exists parties (
  id bigserial primary key,
  name varchar(255) not null,
  phone varchar(255),
  notes varchar(255)
);

create table if not exists trade_deals (
  id bigserial primary key,
  deal_type varchar(32) not null,
  party_id bigint not null references parties(id),
  created_by_id bigint not null references user_accounts(id),
  usd_amount numeric(19,6) not null check (usd_amount >= 0),
  bdt_rate numeric(19,6) not null check (bdt_rate > 0),
  bdt_gross numeric(19,2) not null check (bdt_gross >= 0),
  deal_time timestamp not null,
  notes varchar(255),
  locked_by_day_close boolean not null
);

create table if not exists settlements (
  id bigserial primary key,
  party_id bigint not null references parties(id),
  trade_deal_id bigint references trade_deals(id),
  bdt_amount numeric(19,2) not null check (bdt_amount > 0),
  settlement_time timestamp not null,
  notes varchar(255),
  applied_amount numeric(19,2) not null default 0,
  advance_amount numeric(19,2) not null default 0
);

create table if not exists expenses (
  id bigserial primary key,
  expense_type varchar(64) not null,
  trade_deal_id bigint references trade_deals(id),
  amount_bdt numeric(19,2) not null check (amount_bdt > 0),
  expense_time timestamp not null,
  category varchar(255) not null,
  notes varchar(255)
);

create table if not exists ledger_entries (
  id bigserial primary key,
  entry_time timestamp not null,
  account_code varchar(255) not null,
  debit numeric(19,2) not null default 0,
  credit numeric(19,2) not null default 0,
  reference_type varchar(255) not null,
  reference_id bigint not null,
  narration varchar(255) not null
);

create table if not exists daily_closes (
  id bigserial primary key,
  business_date date not null unique,
  closed_by_id bigint not null references user_accounts(id),
  closed_at timestamp not null,
  reopened boolean not null,
  reopen_reason varchar(255)
);

create table if not exists statement_snapshots (
  id bigserial primary key,
  business_date date not null unique,
  opening_cash_bdt numeric(19,2) not null,
  closing_cash_bdt numeric(19,2) not null,
  opening_usd numeric(19,6) not null,
  closing_usd numeric(19,6) not null,
  realized_profit_loss_bdt numeric(19,2) not null
);

create table if not exists refresh_tokens (
  id bigserial primary key,
  user_id bigint not null references user_accounts(id),
  token_hash varchar(255) not null unique,
  expires_at timestamp not null,
  revoked boolean not null default false,
  created_at timestamp not null,
  revoked_at timestamp,
  replaced_by_hash varchar(255)
);

create table if not exists audit_logs (
  id bigserial primary key,
  action varchar(64) not null,
  actor varchar(255) not null,
  request_path varchar(255) not null,
  metadata varchar(2000),
  reason varchar(255),
  before_payload text,
  after_payload text,
  before_hash varchar(128),
  after_hash varchar(128),
  created_at timestamp not null
);

create index if not exists idx_deals_time on trade_deals(deal_time);
create index if not exists idx_settlements_time on settlements(settlement_time);
create index if not exists idx_expenses_time on expenses(expense_time);
create index if not exists idx_ledger_time on ledger_entries(entry_time);
create index if not exists idx_audit_created on audit_logs(created_at);
