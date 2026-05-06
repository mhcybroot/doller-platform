alter table trade_deals
  rename column instrument_code to currency_code;

create table if not exists currencies (
  id bigserial primary key,
  code varchar(32) not null unique,
  display_name varchar(255) not null,
  notes varchar(255),
  deleted boolean not null default false,
  deleted_at timestamp,
  deleted_by varchar(255)
);

insert into currencies (code, display_name, notes, deleted)
values
  ('USD', 'US DOLLAR', null, false),
  ('USD_SA', 'US DOLLAR SAUDI', null, false),
  ('USD_ID', 'US DOLLAR INDONESIA', null, false),
  ('USD_MY', 'US DOLLAR MALAYSIA', null, false),
  ('USD_HK', 'US DOLLAR HONGKONG', null, false),
  ('USD_CN', 'US DOLLAR CHINA', null, false),
  ('USD_MV', 'US DOLLAR MALDIVES', null, false),
  ('EXCHANGE_FEE', 'EXCHANGE FEE', null, false),
  ('RMB', 'RMB', null, false),
  ('MYR', 'RINGGIT', null, false),
  ('AED', 'DIRHAM', null, false),
  ('SGD', 'SIN DOLLAR', null, false),
  ('GBP', 'POUND', null, false),
  ('AUD', 'AUS DOLLAR', null, false),
  ('CAD', 'CANADIAN DOLLAR', null, false),
  ('SAR', 'SAUDI RIYAL', null, false),
  ('HKD', 'HONGKONG DOLLAR', null, false),
  ('EUR', 'EURO', null, false),
  ('INR', 'INDIAN RUPEE', null, false)
on conflict (code) do nothing;
