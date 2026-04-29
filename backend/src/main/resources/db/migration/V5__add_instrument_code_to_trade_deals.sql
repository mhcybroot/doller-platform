alter table trade_deals
  add column if not exists instrument_code varchar(32);

update trade_deals
set instrument_code = 'USD'
where instrument_code is null;

alter table trade_deals
  alter column instrument_code set not null;
