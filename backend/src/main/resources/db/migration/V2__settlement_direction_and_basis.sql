alter table settlements
  add column if not exists direction varchar(32);

alter table settlements
  add column if not exists basis varchar(32);

update settlements
set direction = coalesce(direction, 'INCOMING'),
    basis = coalesce(basis, 'RECEIVABLE');

alter table settlements
  alter column direction set not null;

alter table settlements
  alter column basis set not null;
