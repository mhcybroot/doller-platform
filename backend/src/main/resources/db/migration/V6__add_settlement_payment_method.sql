alter table settlements
    add column if not exists payment_method varchar(16) not null default 'CASH';

alter table settlements
    add column if not exists payment_reference varchar(255);
