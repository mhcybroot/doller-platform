alter table statement_snapshots
  drop column if exists opening_aging0_to3_bdt,
  drop column if exists closing_aging0_to3_bdt,
  drop column if exists opening_aging4_to7_bdt,
  drop column if exists closing_aging4_to7_bdt,
  drop column if exists opening_aging8_to15_bdt,
  drop column if exists closing_aging8_to15_bdt,
  drop column if exists opening_aging15_to30_bdt,
  drop column if exists closing_aging15_to30_bdt,
  drop column if exists opening_aging30_plus_bdt,
  drop column if exists closing_aging30_plus_bdt;
